// RegionScreen.kt
package com.shiny.railcctv.screens

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegionScreen(navController: NavController) {
    val db = remember { FirebaseFirestore.getInstance() }
    val auth = remember { FirebaseAuth.getInstance() }
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var region by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }

    // 🔔 Android 13+ 알림 권한 런처
    val context = LocalContext.current
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted -> 알림 표시용; 토픽 구독과는 무관하지만 권장 */ }

    // 1) 현재 region 프리필 + 디버그용 토큰 로깅
    LaunchedEffect(Unit) {
        // 알림 권한 요청(13+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        val uid = auth.currentUser?.uid
        if (uid != null) {
            val snap = db.collection("users").document(uid).get().awaitOrNull()
            snap?.getString("region")?.let { region = it }
        }
        FirebaseMessaging.getInstance().token.addOnSuccessListener {
            println("🔑 FCM token: $it")
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbar) }
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text("지역 설정", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = region,
                onValueChange = { region = it },
                label = { Text("지역 입력 (예: daegu, seoul)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(16.dp))

            Button(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        val newRegion = region.trim().lowercase()
                        if (newRegion.isBlank()) {
                            snackbar.showMessage("지역을 입력하세요 (daegu / seoul)")
                            return@launch
                        }
                        loading = true
                        try {
                            // 2) 로그인 보장 (익명 로그인)
                            val uid = auth.currentUser?.uid ?: run {
                                auth.signInAnonymously().awaitOrThrow()
                                auth.currentUser!!.uid
                            }

                            val userRef = db.collection("users").document(uid)
                            val oldRegion = userRef.get().awaitOrNull()?.getString("region")?.lowercase()

                            // 3) Firestore 저장(merge)
                            userRef.set(mapOf("region" to newRegion), SetOptions.merge()).awaitOrThrow()

                            // 4) 이전 토픽 언섭 (안전하게 둘 다 언섭도 가능)
                            val unsubs = mutableListOf<String>()
                            if (!oldRegion.isNullOrBlank() && oldRegion != newRegion) {
                                unsubs += "alerts-$oldRegion"
                            } else {
                                // 안전망: 과거에 잘못 구독된 토픽 정리
                                unsubs += listOf("alerts-daegu", "alerts-seoul").filter { it != "alerts-$newRegion" }
                            }
                            unsubs.forEach { topic ->
                                FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
                                    .addOnCompleteListener { println("ℹ️ 언섭: $topic -> ${it.isSuccessful}") }
                            }

                            // 5) 새 토픽 구독
                            FirebaseMessaging.getInstance()
                                .subscribeToTopic("alerts-$newRegion")
                                .addOnCompleteListener {
                                    if (it.isSuccessful) {
                                        println("✅ 구독 성공: alerts-$newRegion")
                                        scope.launch { snackbar.showMessage("지역이 '$newRegion' 으로 설정되었습니다.") }
                                        navController.popBackStack()
                                    } else {
                                        scope.launch { snackbar.showMessage("토픽 구독 실패: ${it.exception?.message}") }
                                    }
                                }

                        } catch (e: Exception) {
                            snackbar.showMessage("저장 실패: ${e.message}")
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (loading) "저장 중..." else "저장")
            }
        }
    }
}

/* ---- 작은 확장함수들 (콜백을 코루틴처럼 쓰기 위함) ---- */
private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrNull(): T? =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { if (it.isSuccessful) cont.resume(it.result, null) else cont.resume(null, null) }
    }

private suspend fun <T> com.google.android.gms.tasks.Task<T>.awaitOrThrow(): T =
    kotlinx.coroutines.suspendCancellableCoroutine { cont ->
        addOnCompleteListener { if (it.isSuccessful) cont.resume(it.result, null) else cont.resumeWith(Result.failure(it.exception ?: RuntimeException("Task failed"))) }
    }

private suspend fun SnackbarHostState.showMessage(msg: String) {
    this.showSnackbar(message = msg, withDismissAction = true)
}
