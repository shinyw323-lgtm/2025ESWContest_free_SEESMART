// functions/index.js
const { onCall, HttpsError } = require("firebase-functions/v2/https");
const admin = require("firebase-admin");
admin.initializeApp();

// 위치/이름으로 region 판별 (요청에 region이 오면 그 값을 우선)
function resolveRegion({ region, location_name, lat, lng }) {
  if (region) return String(region).toLowerCase();

  const name = (location_name || "").toLowerCase();
  if (name.includes("대구") || name.includes("daegu")) return "daegu";
  if (name.includes("서울") || name.includes("seoul")) return "seoul";

  const la = Number(lat), ln = Number(lng);
  if (Number.isFinite(la) && Number.isFinite(ln)) {
    // 대략적인 범위 (임의 박스, 필요시 수정)
    if (la >= 35.7 && la <= 36.1 && ln >= 128.4 && ln <= 128.8) return "daegu";
    if (la >= 37.2 && la <= 37.7 && ln >= 126.7 && ln <= 127.2) return "seoul";
  }
  return "etc";
}

exports.sendAlertNotification = onCall(async (request) => {
  const { title, body, location_name, lat, lng, region } = request.data || {};

  if (!title || !body) {
    throw new HttpsError("invalid-argument", "제목과 내용을 입력하세요.");
  }

  const final = {
    title: String(title).trim() || "🚨 알림",
    body: String(body).trim() || "새로운 알림이 있습니다.",
    location_name: (location_name && String(location_name).trim()) || "위치 정보 없음",
    lat: Number.isFinite(Number(lat)) ? Number(lat) : null,
    lng: Number.isFinite(Number(lng)) ? Number(lng) : null,
  };

  const resolvedRegion = resolveRegion({
    region,
    location_name: final.location_name,
    lat: final.lat,
    lng: final.lng,
  });

  // 1) Firestore 저장 (region 포함)
  await admin.firestore().collection("alerts").add({
    ...final,
    region: resolvedRegion,
    timestamp: admin.firestore.FieldValue.serverTimestamp(),
  });

  // 2) 지역 토픽으로 FCM 발송
  const topic = `alerts-${resolvedRegion}`;
  await admin.messaging().send({
    topic,
    notification: {
      title: `🚨 ${final.title}`,
      body: final.body,
    },
    data: {
      region: resolvedRegion,
      location_name: final.location_name,
      lat: final.lat?.toString() ?? "",
      lng: final.lng?.toString() ?? "",
    },
    android: { priority: "high", notification: { channelId: "alerts" } },
  });

  return { success: true, topic };
});
