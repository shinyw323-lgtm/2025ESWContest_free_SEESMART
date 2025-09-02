import { initializeApp } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-app.js";
import { getFirestore, collection, query, orderBy, limit, onSnapshot } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-firestore.js";
import { getFunctions, httpsCallable } from "https://www.gstatic.com/firebasejs/10.12.0/firebase-functions.js";

// ✅ Firebase 설정
const firebaseConfig = {
    apiKey: "AIzaSyCMX4TtnCZ9lLYYZ6otP8d_su_L7dpD7Lo",
    authDomain: "railcctv-6e8a6.firebaseapp.com",
    projectId: "railcctv-6e8a6",
    storageBucket: "railcctv-6e8a6.appspot.com",
    messagingSenderId: "375792566445",
    appId: "1:375792566445:web:1462d4f7dc1f856cc6c624"
};

// ✅ DOMContentLoaded 후 실행
document.addEventListener("DOMContentLoaded", () => {
    console.log("✅ DOM 로드 완료");

    const app = initializeApp(firebaseConfig);
    const db = getFirestore(app);
    const functions = getFunctions(app, "us-central1");
    const sendAlertNotification = httpsCallable(functions, "sendAlertNotification");

    // ✅ HTML 요소
    const titleInput = document.getElementById("title");
    const bodyInput = document.getElementById("body");
    const locationInput = document.getElementById("locationName");
    const latInput = document.getElementById("lat");
    const lngInput = document.getElementById("lng");
    const sendButton = document.getElementById("sendButton");
    const statusElement = document.getElementById("status");

    if (!sendButton || !titleInput || !bodyInput) {
        console.error("❌ HTML 요소를 찾을 수 없습니다.");
        return;
    }

    // ✅ Firestore에서 최신 detection_results 자동 채움
    const q = query(collection(db, "detection_results"), orderBy("timestamp", "desc"), limit(1));
    onSnapshot(q, (snapshot) => {
        snapshot.forEach(doc => {
            const data = doc.data();
            const locationName = data.location_name || "";
            const lat = data.lat || "";
            const lng = data.lng || "";
            const detectedObjects = data.detected_objects || "";

            // 입력 필드 자동 채움
            locationInput.value = locationName;
            latInput.value = lat;
            lngInput.value = lng;

            // 제목 & 내용 자동 추천
            let title = "";
            let body = "";
            if (detectedObjects.includes("fire")) {
                title = "화재 발생";
                body = `${locationName}에서 화재가 감지되었습니다. 즉시 확인 바랍니다.`;
            } else if (detectedObjects.includes("fallen")) {
                title = "쓰러진 사람 감지";
                body = `${locationName}에서 사람이 쓰러진 것으로 감지되었습니다. 즉시 확인 바랍니다.`;
            } else if (detectedObjects.includes("person")) {
                const match = detectedObjects.match(/person:(\d+)/);
                const personCount = match ? parseInt(match[1]) : 0;
                if (personCount >= 4) {
                    title = "인원 밀집 발생";
                    body = `${locationName}에서 인원 밀집이 감지되었습니다. 주의 바랍니다.`;
                }
            }


            titleInput.value = title;
            bodyInput.value = body;
        });
    });

    // ✅ 알림 전송
    sendButton.addEventListener("click", async () => {
        const title = titleInput.value.trim() || "🚨 알림";
        const body = bodyInput.value.trim() || "새로운 알림이 있습니다.";
        const locationName = locationInput.value.trim() || "위치 정보 없음";
        const lat = latInput.value.trim() || "";
        const lng = lngInput.value.trim() || "";

        console.log("✅ 전송할 데이터:", { title, body, location_name: locationName, lat, lng });

        statusElement.textContent = "⏳ 알림 전송 중...";
        statusElement.className = "";

        try {
            const result = await sendAlertNotification({ title, body, location_name: locationName, lat, lng });
            console.log("✅ Functions 응답:", result.data);
            statusElement.textContent = "✅ 알림 전송 성공!";
            statusElement.className = "success";

            titleInput.value = "";
            bodyInput.value = "";
        } catch (error) {
            console.error("🔥 Functions 호출 실패:", error);
            statusElement.textContent = "❌ 전송 실패: " + (error.message || "알 수 없는 오류");
            statusElement.className = "error";
        }
    });
});
