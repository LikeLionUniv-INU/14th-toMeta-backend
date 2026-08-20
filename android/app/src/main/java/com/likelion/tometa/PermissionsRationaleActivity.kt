package com.likelion.tometa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

class PermissionsRationaleActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            PermissionsRationaleScreen()
        }
    }
}

@Composable
private fun PermissionsRationaleScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = """
                Health Connect 데이터 이용 안내

                toMeta는 피부 상태 분석을 위해 다음 건강 데이터를 사용합니다.

                • 수면
                • 운동
                • 총 소모 칼로리
                • 산소포화도
                • 생리주기
                • 피부온도

                수집된 데이터는 사용자의 생활 패턴과 피부 상태를 분석하는 데 사용됩니다.
            """.trimIndent()
        )
    }
}