package com.shiny.railcctv

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.messaging.FirebaseMessaging
import com.shiny.railcctv.screens.*
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

class MainActivity : ComponentActivity() {

    private var currentIntentState = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        FirebaseApp.initializeApp(this)

        // ✅ FCM 토픽 구독
        FirebaseMessaging.getInstance().subscribeToTopic("alerts")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    println("✅ FCM alerts 토픽 구독 성공")
                } else {
                    println("❌ 토픽 구독 실패: ${task.exception}")
                }
            }

        currentIntentState.value = intent // 최초 실행 시 인텐트 저장

        setContent {
            MaterialTheme {
                MainApp(currentIntentState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentIntentState.value = intent // 새 인텐트 갱신
    }
}

@Composable
fun MainApp(currentIntentState: State<Intent?>) {
    val navController = rememberNavController()

    // ✅ 로그인 여부 확실히 체크 (세션 + 이메일 존재)
    val user = FirebaseAuth.getInstance().currentUser
    val isLoggedIn = user != null && !user.email.isNullOrEmpty()

    // ✅ 로그인 여부에 따라 첫 화면 결정
    val startDestination = if (isLoggedIn) "main" else "login"

    NavHost(navController = navController, startDestination = startDestination) {
        composable("login") { LoginScreen(navController) }
        composable("register") { RegisterScreen(navController) }
        composable("main") { MainScreen(navController) }
        composable("region_setting") { RegionScreen(navController) }
        composable("alert_list") { AlertListScreen(navController) }
        composable("report") { ReportScreen(navController) }

        composable(
            route = "alert_detail/{title}/{body}/{time}/{location}/{lat}/{lng}",
            arguments = listOf(
                navArgument("title") { type = NavType.StringType },
                navArgument("body") { type = NavType.StringType },
                navArgument("time") { type = NavType.StringType },
                navArgument("location") { type = NavType.StringType },
                navArgument("lat") { type = NavType.StringType },
                navArgument("lng") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val title = URLDecoder.decode(backStackEntry.arguments?.getString("title") ?: "", StandardCharsets.UTF_8.toString())
            val body = URLDecoder.decode(backStackEntry.arguments?.getString("body") ?: "", StandardCharsets.UTF_8.toString())
            val time = backStackEntry.arguments?.getString("time") ?: ""
            val location = URLDecoder.decode(backStackEntry.arguments?.getString("location") ?: "", StandardCharsets.UTF_8.toString())
            val lat = backStackEntry.arguments?.getString("lat") ?: ""
            val lng = backStackEntry.arguments?.getString("lng") ?: ""

            AlertDetailScreen(title, body, time, location, lat, lng)
        }
    }

    // ✅ 알림 클릭 시 상세 화면 이동 (로그인 한 경우만 허용)
    LaunchedEffect(currentIntentState.value, isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect

        currentIntentState.value?.let { intent ->
            val title = intent.getStringExtra("title") ?: ""
            val body = intent.getStringExtra("body") ?: ""
            val time = intent.getStringExtra("time") ?: ""
            val location = intent.getStringExtra("location_name") ?: ""
            val lat = intent.getStringExtra("lat") ?: ""
            val lng = intent.getStringExtra("lng") ?: ""

            if (title.isNotEmpty() && location.isNotEmpty()) {
                val encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8.toString())
                val encodedBody = URLEncoder.encode(body, StandardCharsets.UTF_8.toString())
                val encodedLocation = URLEncoder.encode(location, StandardCharsets.UTF_8.toString())

                navController.navigate("alert_detail/$encodedTitle/$encodedBody/$time/$encodedLocation/$lat/$lng") {
                    popUpTo("main") { inclusive = false }
                }
            }
        }
    }
}
