# 2025ESWContest_free_SEESMART

레일형 CCTV와 AI 객체 감지를 활용한 스마트시티 보안 시스템 🚨  


## 📂 프로젝트 구조
2025ESWContest_free_SEESMART/
├── app/ # Android 앱 (주민 신고 & 푸시 알림)
│ ├── src/ # Kotlin 소스 코드 + 리소스
│ ├── build.gradle.kts
│ ├── proguard-rules.pro
│ └── .gitignore
│
├── hardware/ # STM32 하드웨어 제어 (C언어)
│ └── main.c
│
├── jetsonnano/ # Jetson Nano 객체 감지 (YOLO, Mediapipe)
│ └── jetson_main.py
│
├── mainserver/ # 메인 서버 (실시간 감지/제어, Firestore 연동)
│ └── server.py
│
└── web+smartsign/ # 관리자 웹 & 스마트 표지판 (Firebase Hosting)
├── public/ # 정적 파일 (index.html, script.js, style.css)
├── functions/ # Firebase Functions (index.js)
├── firebase.json
└── .firebaserc
