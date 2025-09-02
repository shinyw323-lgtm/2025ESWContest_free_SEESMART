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
fun RegisterScreen(navController: NavController) {
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
        Text("회원가입", style = MaterialTheme.typography.headlineLarge)
        Spacer(modifier = Modifier.height(20.dp))

        OutlinedTextField(
            value = nickname,
            onValueChange = { nickname = it },
            label = { Text("닉네임 입력") }
        )

        Spacer(modifier = Modifier.height(20.dp))

        Button(onClick = {
            if (nickname.isNotEmpty()) {
                // ✅ 닉네임 중복 체크
                firestore.collection("users")
                    .whereEqualTo("nickname", nickname)
                    .get()
                    .addOnSuccessListener { result ->
                        if (!result.isEmpty) {
                            message = "이미 사용 중인 닉네임입니다."
                        } else {
                            // 중복 없으면 회원가입
                            firestore.collection("users")
                                .add(mapOf("nickname" to nickname))
                                .addOnSuccessListener {
                                    message = "회원가입 완료!"
                                    navController.navigate("login")
                                }
                                .addOnFailureListener {
                                    message = "회원가입 실패: ${it.message}"
                                }
                        }
                    }
                    .addOnFailureListener {
                        message = "중복 검사 실패: ${it.message}"
                    }
            } else {
                message = "닉네임을 입력하세요."
            }
        }) {
            Text("회원가입")
        }

        Spacer(modifier = Modifier.height(10.dp))
        Text(message, color = Color.Blue)
    }
}
