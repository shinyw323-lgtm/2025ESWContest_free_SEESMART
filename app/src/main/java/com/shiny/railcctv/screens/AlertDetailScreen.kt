package com.shiny.railcctv.screens

import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun AlertDetailScreen(
    title: String,
    body: String,
    time: String,
    location: String,
    lat: String,
    lng: String
) {
    // ✅ URI Decode (한글, 특수문자 깨짐 방지)
    val decodedTitle = Uri.decode(title)
    val decodedBody = Uri.decode(body)
    val decodedLocation = Uri.decode(location)

    // ✅ time이 Long 값인지 확인 후 변환
    val formattedTime = try {
        val timeLong = time.toLong()
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        sdf.format(Date(timeLong))
    } catch (e: Exception) {
        time // 변환 실패 시 원래 값 표시
    }

    // ✅ lat, lng 안전 변환
    val latitude = lat.toDoubleOrNull() ?: 0.0
    val longitude = lng.toDoubleOrNull() ?: 0.0
    val locationLatLng = LatLng(latitude, longitude)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        // ✅ 알림 상세 정보
        Text(text = decodedTitle, style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(8.dp))
        Text(text = "시간: $formattedTime", style = MaterialTheme.typography.bodySmall)
        Text(text = "위치: $decodedLocation", style = MaterialTheme.typography.bodySmall)
        Spacer(modifier = Modifier.height(16.dp))
        Text(text = decodedBody, style = MaterialTheme.typography.bodyLarge)

        Spacer(modifier = Modifier.height(24.dp))

        // ✅ 지도 표시
        if (latitude != 0.0 && longitude != 0.0) {
            Text(text = "위치 지도", style = MaterialTheme.typography.titleMedium)
            Spacer(modifier = Modifier.height(8.dp))

            val cameraPositionState = rememberCameraPositionState {
                position = com.google.android.gms.maps.model.CameraPosition.fromLatLngZoom(locationLatLng, 15f)
            }

            GoogleMap(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
                cameraPositionState = cameraPositionState
            ) {
                Marker(
                    state = MarkerState(position = locationLatLng),
                    title = decodedLocation,
                    snippet = "위도: $latitude, 경도: $longitude"
                )
            }
        } else {
            Text("위치 좌표가 없습니다.", style = MaterialTheme.typography.bodySmall)
        }
    }
}
