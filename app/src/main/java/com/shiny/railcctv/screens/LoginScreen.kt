package com.shiny.railcctv

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.google.firebase.firestore.FirebaseFirestore

@Composable
fun LoginScreen(navController: NavController) {
    var nickname by remember { mutableStateOf("") }
    val firestore = FirebaseFirestore.getInstance()
    var message by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("로그인", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(20.dp))
        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임 입력") }
        )
        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            firestore.collection("users")
                .whereEqualTo("nickname", nickname)
                .get()
                .addOnSuccessListener { documents ->
                    if (!documents.isEmpty) {
                        navController.navigate("main") {
                            popUpTo("login") { inclusive = true } // 뒤로가기 시 로그인 화면 제거
                        }
                    } else {
                        message = "닉네임이 존재하지 않습니다."
                    }
                }
        }) {
            Text("로그인")
        }

        Spacer(modifier = Modifier.height(10.dp))
        TextButton(onClick = { navController.navigate("register") }) {
            Text("회원가입")
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(message, color = Color.Red)
    }
}
