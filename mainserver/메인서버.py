import cv2
import numpy as np
import torch
import time
import winsound
from collections import Counter
from google.cloud import firestore
import warnings
import os
import pathlib
import socket

warnings.filterwarnings("ignore", category=FutureWarning)

# ✅ Pathlib 보정 (Windows 호환)
pathlib.PosixPath = pathlib.WindowsPath

# ✅ Firestore 인증
os.environ["GOOGLE_APPLICATION_CREDENTIALS"] = r"C:\\Users\\shiny\\OneDrive\\Desktop\\firebase\\railcctv-xxxx.json"
db = firestore.Client(project="railcctv-6e8a6")

# ✅ YOLO 모델 로드
model_path = r"C:\\Users\\shiny\\OneDrive\\Desktop\\best.pt"
model = torch.hub.load('ultralytics/yolov5', 'custom', path=model_path, force_reload=True)
device = 'cuda' if torch.cuda.is_available() else 'cpu'
model.to(device)
model.conf = 0.4
model.iou = 0.45
if device == 'cuda':
    model.half()

# ✅ Jetson 소켓 통신 설정
JETSON_IP, CTRL_PORT = "192.168.0.27", 8993
NET_TIMEOUT = 1.5

def send_cmd(cmd: str):
    """Jetson Nano로 제어 명령 송신"""
    try:
        with socket.create_connection((JETSON_IP, CTRL_PORT), timeout=NET_TIMEOUT) as s:
            s.sendall(cmd.encode())
            print(f"[CTRL] 송신: {cmd.strip()}")
    except Exception as e:
        print(f"[CTRL] 송신 실패: {e}")

# ✅ CCTV 정보
lat, lng = 35.893220, 128.610206
location_name = "경북대 북문"
document_id = "kmu_gate"

# ✅ Firestore 업로드 함수
def update_detection_results(detected_summary, status):
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    try:
        db.collection("detection_results").add({
            "location_name": location_name,
            "lat": lat,
            "lng": lng,
            "status": status,
            "detected_objects": detected_summary,
            "timestamp": timestamp
        })
        print(f"[Firestore] ✅ detection_results 업데이트 성공 ({location_name}, {status})")
    except Exception as e:
        print("[Firestore] ❌ 업데이트 실패:", str(e))

def update_crowd_status(status, detected_summary, count):
    timestamp = time.strftime("%Y-%m-%d %H:%M:%S")
    try:
        db.collection("crowd_status").document(document_id).set({
            "location_name": location_name,
            "lat": lat,
            "lng": lng,
            "status": status,
            "detected_objects": detected_summary,
            "count": int(count),
            "timestamp": timestamp
        })
        print(f"[Firestore] ✅ crowd_status 업데이트 ({status})")
    except Exception as e:
        print("[Firestore] ❌ 업데이트 실패:", str(e))

# ✅ 색상 출력 함수
def print_colored(text, color="green"):
    colors = {"green": "\033[92m", "red": "\033[91m", "end": "\033[0m"}
    print(f"{colors.get(color, '')}{text}{colors['end']}")

# ✅ 카메라 초기화
cap = cv2.VideoCapture(0)
cap.set(cv2.CAP_PROP_FRAME_WIDTH, 640)
cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 480)

window_name = "YOLOv5 Detection"
cv2.namedWindow(window_name, cv2.WINDOW_NORMAL)
cv2.resizeWindow(window_name, 640, 480)

last_send_time = 0
last_log_time = 0
last_status = "safe"
upload_interval = 5
crowd_update_interval = 3
last_crowd_time = 0

stop_classes = {'trash', 'fire'}

# ✅ 불법주차 감지용 메모리
car_positions = {}
ILLEGAL_PARKING_TIME = 10  # 10초 이상 같은 위치면 불법주차

try:
    while True:
        ret, frame = cap.read()
        if not ret:
            print("[ERROR] 웹캠에서 프레임 읽기 실패")
            break

        # YOLO 추론
        img = frame if device == 'cpu' else frame.astype(np.float16)
        results = model(img, size=320)
        detections = results.xyxy[0].cpu().numpy()
        labels_detected = [model.names[int(cls)] for *xyxy, conf, cls in detections]
        count_labels = Counter(labels_detected)
        detected_summary = ", ".join([f"{k}:{v}" for k, v in count_labels.items()]) if count_labels else "없음"
        person_count = count_labels.get('person', 0)

        # ✅ safe / warning 판정
        status = "warning" if (stop_classes.intersection(labels_detected) or person_count >= 4) else "safe"

        current_time = time.time()

        # Firestore 업로드 (safe/warning만)
        if current_time - last_send_time >= upload_interval:
            update_detection_results(detected_summary, status)
            last_send_time = current_time

        # Crowd status 업데이트
        if current_time - last_crowd_time >= crowd_update_interval:
            if person_count >= 4:
                update_crowd_status("danger", detected_summary, person_count)
            elif 2 <= person_count < 4:
                update_crowd_status("warning", detected_summary, person_count)
            else:
                try:
                    db.collection("crowd_status").document(document_id).delete()
                    print("[Firestore] ✅ crowd_status 문서 삭제 완료")
                except Exception as e:
                    print("[Firestore] ❌ 삭제 실패:", str(e))
            last_crowd_time = current_time

        # 🔶 불법주차 감지 
        cars = []
        for *xyxy, conf, cls in detections:
            label = model.names[int(cls)]
            if label == "car":
                x1, y1, x2, y2 = map(int, xyxy)
                cx, cy = (x1+x2)//2, (y1+y2)//2
                cars.append((cx, cy))

        illegal_parking_detected = False
        for cx, cy in cars:
            key = f"{cx//50}_{cy//50}"
            if key in car_positions:
                start_time = car_positions[key]
                if current_time - start_time >= ILLEGAL_PARKING_TIME:
                    illegal_parking_detected = True
            else:
                car_positions[key] = current_time

        car_positions = {k:v for k,v in car_positions.items() if current_time - v <= ILLEGAL_PARKING_TIME*2}

        if illegal_parking_detected:
            print_colored("🚗 불법주차 감지!", "red")
            send_cmd("warning\n")  # Jetson에만 전송

        # 상태 변경 시 Jetson으로 송신
        if status != last_status:
            send_cmd(status + "\n")
            last_status = status

        # 시각화
        for *xyxy, conf, cls in detections:
            x1, y1, x2, y2 = map(int, xyxy)
            label = model.names[int(cls)]
            color = (0, 255, 0) if label not in stop_classes else (0, 0, 255)
            cv2.rectangle(frame, (x1, y1), (x2, y2), color, 2)
            cv2.putText(frame, f"{label} {conf:.2f}", (x1, y1 - 5),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.6, color, 2)

        status_color = (0, 0, 255) if status == "warning" else (0, 255, 0)
        cv2.putText(frame, status.upper(), (30, 50), cv2.FONT_HERSHEY_SIMPLEX, 2.0, status_color, 5)

        cv2.imshow(window_name, frame)

        # ✅ 수동 제어
        key = cv2.waitKey(1) & 0xFF
        if key in (27, ord('q'), ord('Q')):
            break
        elif key in (ord('w'), ord('W')):
            send_cmd("w\n")  # 전진
        elif key in (ord('s'), ord('S')):
            send_cmd("s\n")  # 후진
        elif key in (ord('d'), ord('D')):
            send_cmd("d\n")  # 오른쪽
        elif key in (ord('a'), ord('A')):
            send_cmd("a\n")  # 왼쪽
        elif key in (ord('b'), ord('B')):
            send_cmd("1\n")  # 정지

        # 로그 출력 (1초마다)
        if current_time - last_log_time >= 1:
            print_colored(f"상태: {status} | 감지 객체: {detected_summary}", 
                          "red" if status == "warning" else "green")
            last_log_time = current_time

except KeyboardInterrupt:
    pass
finally:
    cap.release()
    cv2.destroyAllWindows()
    print("[INFO] 프로그램 종료")
