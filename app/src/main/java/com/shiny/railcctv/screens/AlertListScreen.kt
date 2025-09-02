package com.shiny.railcctv.screens

import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query

@Composable
fun AlertListScreen(navController: NavController) {
    val db = FirebaseFirestore.getInstance()
    val alerts = remember { mutableStateListOf<Map<String, Any>>() }

    // ✅ Firestore 실시간 데이터 가져오기
    LaunchedEffect(Unit) {
        db.collection("alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshots, e ->
                if (e != null) return@addSnapshotListener
                alerts.clear()
                for (doc in snapshots!!) {
                    alerts.add(doc.data)
                }
            }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("알림 내역", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(alerts) { alert ->
                val title = alert["title"] as? String ?: "제목 없음"
                val body = alert["body"] as? String ?: "내용 없음"
                val timestamp = (alert["timestamp"] as? Timestamp)?.toDate()?.time ?: 0L // ✅ 안전하게 Long 값
                val locationName = alert["location_name"] as? String ?: "위치 정보 없음"
                val lat = (alert["lat"] as? Double)?.toString() ?: "0"
                val lng = (alert["lng"] as? Double)?.toString() ?: "0"

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable {
                            // ✅ 모든 값 URI 인코딩 후 NavController에 전달
                            val route = "alert_detail/${
                                Uri.encode(title)
                            }/${
                                Uri.encode(body)
                            }/$timestamp/${
                                Uri.encode(locationName)
                            }/${
                                Uri.encode(lat)
                            }/${
                                Uri.encode(lng)
                            }"

                            navController.navigate(route)
                        },
                    elevation = CardDefaults.cardElevation(4.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        Text(body, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                        Text("위치: $locationName", style = MaterialTheme.typography.bodySmall)
                        Text("시간: ${if (timestamp != 0L) java.util.Date(timestamp).toString() else "시간 없음"}",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}
