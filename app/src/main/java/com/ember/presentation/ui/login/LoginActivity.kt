package com.ember.presentation.ui.login

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.ember.presentation.theme.EmberTheme
import com.ember.presentation.ui.main.MainActivity

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            setContent {
                EmberTheme {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        LoginScreen(
                            onLoginSuccess = {
                                // 登录成功跳转 MainActivity，不在 LoginActivity 加载数据
                                startActivity(Intent(this, MainActivity::class.java))
                                finish()
                            }
                        )
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("LoginActivity", "setContent crash", e)
            setContentView(TextView(this).apply { text = "启动失败，请重试" })
        }
    }
}
