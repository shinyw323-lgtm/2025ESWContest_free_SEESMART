package com.shiny.railcctv.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.*
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import com.google.android.gms.maps.model.CameraPosition
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()

    val alerts = remember { mutableStateListOf<Map<String, Any>>() }
    val crowdStatus = remember { mutableStateListOf<Map<String, Any>>() }
    val reports = remember { mutableStateListOf<Map<String, Any>>() }

    // ✅ 상태 관리
    var selectedType by remember { mutableStateOf<String?>(null) }
    var selectedLocation by remember { mutableStateOf<String?>(null) }
    var selectedReport by remember { mutableStateOf<Map<String, Any>?>(null) }

    val bottomSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    // ✅ Firestore 실시간 데이터
    LaunchedEffect(Unit) {
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                alerts.clear()
                for (doc in snapshots!!) alerts.add(doc.data)
            }

        db.collection("crowd_status")
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                crowdStatus.clear()
                for (doc in snapshots!!) crowdStatus.add(doc.data)
            }

        db.collection("pending_reports") // ✅ 변경된 컬렉션
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                reports.clear()
                for (doc in snapshots!!) reports.add(doc.data)
            }
    }

    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(LatLng(35.8714, 128.6014), 13f)
    }

    Scaffold(
        topBar = { CenterAlignedTopAppBar(title = { Text("메인 화면") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ✅ Google Map
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState
                ) {
                    // ✅ alerts + crowd_status 마커
                    val locations = (crowdStatus + alerts)
                        .groupBy { it["location_name"] as? String ?: "위치 정보 없음" }

                    locations.forEach { (location, group) ->
                        val lat = (group.first()["lat"] as? Double) ?: 0.0
                        val lng = (group.first()["lng"] as? Double) ?: 0.0

                        if (lat != 0.0 && lng != 0.0) {
                            val hasAlert = alerts.any { it["location_name"] == location }
                            val crowdData = crowdStatus.find { it["location_name"] == location }
                            val crowdStatusVal = crowdData?.get("status") as? String

                            val markerColor = when {
                                crowdStatusVal == "danger" -> BitmapDescriptorFactory.HUE_RED
                                crowdStatusVal == "warning" -> BitmapDescriptorFactory.HUE_YELLOW
                                hasAlert -> BitmapDescriptorFactory.HUE_BLUE
                                else -> null
                            }

                            if (markerColor != null) {
                                Marker(
                                    state = MarkerState(position = LatLng(lat, lng)),
                                    title = location,
                                    icon = BitmapDescriptorFactory.defaultMarker(markerColor),
                                    onClick = {
                                        selectedType = "alert"
                                        selectedLocation = location
                                        selectedReport = null
                                        coroutineScope.launch { bottomSheetState.show() }
                                        true
                                    }
                                )
                            }
                        }
                    }

                    // ✅ 신고 마커 (주황색)
                    reports.forEach { report ->
                        val lat = (report["latitude"] as? Double) ?: 0.0
                        val lng = (report["longitude"] as? Double) ?: 0.0
                        val title = report["title"] as? String ?: "신고"

                        if (lat != 0.0 && lng != 0.0) {
                            Marker(
                                state = MarkerState(position = LatLng(lat, lng)),
                                title = title,
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE),
                                onClick = {
                                    selectedType = "report"
                                    selectedReport = report
                                    selectedLocation = null
                                    coroutineScope.launch { bottomSheetState.show() }
                                    true
                                }
                            )
                        }
                    }
                }
            }

            // ✅ 버튼 영역
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = { navController.navigate("alert_list") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("알림 내역 보기")
                }

                Button(
                    onClick = { navController.navigate("region_setting") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Text("지역 설정")
                }

                Button(
                    onClick = { navController.navigate("report") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Text("신고하기")
                }
            }
        }
    }

    // ✅ BottomSheet
    if (selectedType != null) {
        ModalBottomSheet(
            onDismissRequest = {
                selectedType = null
                selectedLocation = null
                selectedReport = null
            },
            sheetState = bottomSheetState
        ) {
            when (selectedType) {
                "alert" -> {
                    Text(
                        text = "📍 ${selectedLocation ?: ""}",
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                    Divider()
                    val filteredAlerts = alerts.filter { it["location_name"] == selectedLocation }
                    if (filteredAlerts.isEmpty()) {
                        Text("기록이 없습니다.", modifier = Modifier.padding(16.dp))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(filteredAlerts.take(5)) { alert ->
                                val title = alert["title"].toString()
                                val body = alert["body"].toString()
                                val timestamp = alert["timestamp"] as? Timestamp
                                val formattedTime = if (timestamp != null) {
                                    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timestamp.toDate())
                                } else "시간 없음"

                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("제목: $title", style = MaterialTheme.typography.bodyLarge)
                                    Text("내용: $body", style = MaterialTheme.typography.bodyMedium)
                                    Text(formattedTime, style = MaterialTheme.typography.bodySmall)
                                }
                                Divider()
                            }
                        }
                    }
                }

                "report" -> {
                    selectedReport?.let { report ->
                        val title = report["title"].toString()
                        val description = report["description"].toString()
                        val videoURL = report["videoUrl"].toString()
                        val timestamp = report["timestamp"] as? Timestamp
                        val formattedTime = if (timestamp != null) {
                            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(timestamp.toDate())
                        } else "시간 없음"

                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("제목: $title", style = MaterialTheme.typography.titleLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text("내용: $description", style = MaterialTheme.typography.bodyLarge)
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(formattedTime, style = MaterialTheme.typography.bodySmall)
                            Spacer(modifier = Modifier.height(16.dp))

                            // ✅ ExoPlayer 영상 재생
                            AndroidView(
                                factory = { ctx ->
                                    val player = ExoPlayer.Builder(ctx).build().apply {
                                        setMediaItem(MediaItem.fromUri(videoURL))
                                        prepare()
                                        playWhenReady = false
                                    }
                                    PlayerView(ctx).apply {
                                        this.player = player
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(220.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
