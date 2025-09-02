package com.shiny.railcctv.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import java.util.*

@Composable
fun ReportScreen(navController: NavController) {
    val context = LocalContext.current
    val fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)

    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var videoUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    // ✅ 권한 요청
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val denied = permissions.filterValues { !it }
        if (denied.isNotEmpty()) {
            Toast.makeText(context, "권한이 필요합니다: $denied", Toast.LENGTH_SHORT).show()
        }
    }

    // ✅ 앱 시작 시 권한 요청
    LaunchedEffect(Unit) {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.CAMERA
        )
        permissionLauncher.launch(permissions)
    }

    // ✅ 영상 촬영 런처
    val videoCaptureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let { uri ->
                videoUri = uri

                // ✅ 촬영 시간 검증
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(context, uri)
                val dateTaken = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)
                retriever.release()

                val currentTime = System.currentTimeMillis()
                val videoTime = dateTaken?.toLongOrNull() ?: currentTime

                val diff = (currentTime - videoTime) / 1000 // 초 단위
                if (diff > 120) { // 2분 이상 차이
                    Toast.makeText(context, "실시간 영상만 업로드 가능합니다.", Toast.LENGTH_SHORT).show()
                    videoUri = null
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("신고하기", style = MaterialTheme.typography.headlineMedium)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("제목") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("내용") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                val intent = Intent(MediaStore.ACTION_VIDEO_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, 10) // 최대 10초
                videoCaptureLauncher.launch(intent)
            }
        ) {
            Text("영상 촬영")
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                if (title.isBlank() || description.isBlank() || videoUri == null) {
                    Toast.makeText(context, "모든 항목을 입력하세요", Toast.LENGTH_SHORT).show()
                    return@Button
                }

                isUploading = true

                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                    != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(context, "위치 권한이 필요합니다.", Toast.LENGTH_SHORT).show()
                    isUploading = false
                    return@Button
                }

                // ✅ 위치 가져오기
                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                    if (location != null) {
                        val lat = location.latitude
                        val lng = location.longitude

                        val storageRef = FirebaseStorage.getInstance()
                            .reference.child("reports/${UUID.randomUUID()}.mp4")

                        videoUri?.let { uri ->
                            storageRef.putFile(uri).addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                                    val videoURL = downloadUrl.toString()

                                    // ✅ Firestore 저장
                                    val report = hashMapOf(
                                        "title" to title,
                                        "description" to description,
                                        "videoUrl" to videoURL,
                                        "latitude" to lat,
                                        "longitude" to lng,
                                        "status" to "pending",
                                        "timestamp" to FieldValue.serverTimestamp()
                                    )

                                    FirebaseFirestore.getInstance()
                                        .collection("pending_reports")
                                        .add(report)
                                        .addOnSuccessListener {
                                            Toast.makeText(context, "신고 완료!", Toast.LENGTH_SHORT).show()
                                            navController.popBackStack()
                                        }
                                        .addOnFailureListener {
                                            Toast.makeText(context, "신고 실패!", Toast.LENGTH_SHORT).show()
                                        }

                                    isUploading = false
                                }
                            }.addOnFailureListener {
                                Toast.makeText(context, "영상 업로드 실패", Toast.LENGTH_SHORT).show()
                                isUploading = false
                            }
                        }
                    } else {
                        Toast.makeText(context, "위치 정보를 가져올 수 없습니다", Toast.LENGTH_SHORT).show()
                        isUploading = false
                    }
                }
            },
            enabled = !isUploading
        ) {
            Text(if (isUploading) "업로드 중..." else "신고하기")
        }
    }
}
