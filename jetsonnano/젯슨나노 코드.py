import cv2, torch, time, serial, socket, threading, warnings, numpy as np
import mediapipe as mp

warnings.filterwarnings("ignore", category=FutureWarning)

# ================= YOLOv5 모델 =================
model = torch.hub.load(
    '/home/jetson/yolov5',
    'custom',
    path='/home/jetson/yolov5/best.pt',   # 학습된 가중치 (fire, trash, person, car 포함)
    source='local'
)
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model.to(device).eval()
model.conf, model.iou = 0.4, 0.45
if device == 'cuda':
    model.half()

# ================= Mediapipe Pose =================
mp_pose = mp.solutions.pose
pose = mp_pose.Pose()

# ================= UART =================
ser = serial.Serial('/dev/ttyTHS1', 9600, timeout=1)
UART_LEFT  = b'a\n'
UART_RIGHT = b'd\n'
UART_GO    = b'w\n'
UART_BACK  = b's\n'
UART_STOP  = b'1\n'

def uart_send(cmd, tag=""):
    try:
        ser.write(cmd)
        print(f"[UART] {tag} → {cmd}")
    except Exception as e:
        print("[UART] 송신 실패:", e)

# ================= 불법주차 기록 =================
car_positions = {}
ILLEGAL_PARKING_TIME = 10  # 10초 이상 같은 위치면 불법주차

# ================= 추적 함수 =================
DEADZONE_X, DEADZONE_Y = 0.2, 0.2  # 가로/세로 20%

def decide_direction_2d(frame_w, frame_h, target_box):
    """사람 중심 좌표 기준 좌/우/상/하 판단"""
    x1, y1, x2, y2 = target_box
    cx, cy = (x1+x2)/2, (y1+y2)/2
    left, right = frame_w*(0.5-DEADZONE_X/2), frame_w*(0.5+DEADZONE_X/2)
    top, bottom = frame_h*(0.5-DEADZONE_Y/2), frame_h*(0.5+DEADZONE_Y/2)

    if cx < left: return "LEFT"
    elif cx > right: return "RIGHT"
    elif cy < top: return "UP"
    elif cy > bottom: return "DOWN"
    else: return None

# ================= 고정형 CCTV 서버 통신 (TCP 8993) =================
def listen_from_server():
    TCP_PORT = 8993
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", TCP_PORT))
    sock.listen(1)
    print("[TCP] 고정형 서버 명령 대기...")

    while True:
        conn, addr = sock.accept()
        with conn:
            print(f"[TCP] 연결됨: {addr}")
            try:
                while True:
                    data = conn.recv(1024)
                    if not data: break
                    msg = data.decode(errors="ignore").strip().lower()
                    print(f"[TCP] 수신: {msg}")

                    if msg in ("go", "w"): uart_send(UART_GO, "GO (server)")
                    elif msg in ("back", "s"): uart_send(UART_BACK, "BACK (server)")
                    elif msg in ("left", "a"): uart_send(UART_LEFT, "LEFT (server)")
                    elif msg in ("right", "d"): uart_send(UART_RIGHT, "RIGHT (server)")
                    elif msg in ("stop", "b", "1"): uart_send(UART_STOP, "STOP (server)")
                    elif msg == "warning": uart_send(UART_STOP, "STOP (warning)")
                    elif msg == "safe": uart_send(UART_GO, "GO (safe)")
            except Exception as e:
                print("[TCP] 오류:", e)

threading.Thread(target=listen_from_server, daemon=True).start()

# ================= 메인 루프 =================
cap = cv2.VideoCapture(0)
cap.set(3,640); cap.set(4,480)

try:
    target_person = None  # 추적할 사람 bbox
    last_stop_time = 0

    while True:
        ret, frame = cap.read()
        if not ret: break
        H, W = frame.shape[:2]

        # YOLO 추론
        results = model(frame, size=640)
        det = results.xyxy[0].cpu().numpy()

        persons, trashes, cars = [], [], []
        fire_detected, fallen_detected = False, False

        for x1,y1,x2,y2,conf,cls in det:
            label = model.names[int(cls)]
            if label == "person": persons.append([x1,y1,x2,y2])
            elif label == "trash": trashes.append([x1,y1,x2,y2])
            elif label == "car": cars.append([x1,y1,x2,y2])
            elif label in ("fire","smoke"): fire_detected = True

        # Mediapipe → 쓰러짐 감지
        rgb = cv2.cvtColor(frame, cv2.COLOR_BGR2RGB)
        pose_results = pose.process(rgb)
        if pose_results.pose_landmarks:
            ls = pose_results.pose_landmarks.landmark[mp_pose.PoseLandmark.LEFT_SHOULDER]
            lh = pose_results.pose_landmarks.landmark[mp_pose.PoseLandmark.LEFT_HIP]
            if abs(ls.y - lh.y) < 0.05:
                fallen_detected = True

        # 불법주차 감지
        now = time.time()
        illegal_parking = False
        for (x1,y1,x2,y2) in cars:
            cx,cy = (x1+x2)//2,(y1+y2)//2
            key = f"{cx//50}_{cy//50}"
            if key in car_positions and now - car_positions[key] > ILLEGAL_PARKING_TIME:
                illegal_parking = True
            else:
                car_positions[key] = now

        # 무단투기 → trash 근처 사람이 멀어졌을 때 추적 시작
        if trashes and persons:
            for t in trashes:
                tx,ty = (t[0]+t[2])/2, (t[1]+t[3])/2
                for p in persons:
                    px,py = (p[0]+p[2])/2, (p[1]+p[3])/2
                    dist = np.linalg.norm([tx-px, ty-py])
                    if dist > 100:  # 일정 거리 이상 떨어짐
                        target_person = p

        # 제어 로직
        danger = fire_detected or fallen_detected or illegal_parking
        if target_person is not None:
            # 추적 모드 (사람 따라감)
            decision = decide_direction_2d(W, H, target_person)
            if decision == "LEFT": uart_send(UART_LEFT,"LEFT")
            elif decision == "RIGHT": uart_send(UART_RIGHT,"RIGHT")
            elif decision == "UP": uart_send(UART_GO,"GO")
            elif decision == "DOWN": uart_send(UART_BACK,"BACK")

            # bbox 면적이 충분히 크면 STOP
            area = (target_person[2]-target_person[0])*(target_person[3]-target_person[1])
            if area > (W*H*0.25) and time.time()-last_stop_time>3:
                uart_send(UART_STOP,"STOP (close to person)")
                last_stop_time = time.time()
                target_person = None
        elif danger:
            uart_send(UART_STOP,"STOP")
        else:
            uart_send(UART_GO,"GO")  # 기본 순찰

        # 디버그 화면
        for (x1,y1,x2,y2) in persons: cv2.rectangle(frame,(int(x1),int(y1)),(int(x2),int(y2)),(0,255,0),2)
        for (x1,y1,x2,y2) in trashes: cv2.rectangle(frame,(int(x1),int(y1)),(int(x2),int(y2)),(0,0,255),2)
        cv2.imshow("Jetson CCTV",frame)
        if cv2.waitKey(1)&0xFF==27: break

except KeyboardInterrupt:
    pass
finally:
    cap.release(); cv2.destroyAllWindows(); ser.close()
