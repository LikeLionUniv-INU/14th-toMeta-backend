package com.likelion.tometa

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.likelion.tometa.healthconnect.HealthConnectManager
import com.likelion.tometa.healthconnect.HealthConnectPermissions
import com.likelion.tometa.healthconnect.HealthConnectReader
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val healthConnectManager = HealthConnectManager(this)
        val healthConnectReader = HealthConnectReader(healthConnectManager)

        setContent {

            var sdkStatus by remember {
                mutableStateOf<Int?>(null)
            }

            var statusText by remember {
                mutableStateOf("Health Connect 상태 확인 중...")
            }

            var permissionText by remember {
                mutableStateOf("")
            }

            var healthDataText by remember {
                mutableStateOf("")
            }

            val coroutineScope = rememberCoroutineScope()

            val permissionLauncher =
                rememberLauncherForActivityResult(
                    contract = PermissionController
                        .createRequestPermissionResultContract()
                ) { grantedPermissions ->

                    statusText =
                        if (
                            grantedPermissions.containsAll(
                                HealthConnectPermissions.READ_PERMISSIONS
                            )
                        ) {
                            "Health Connect 권한 전체 허용됨"
                        } else {
                            "Health Connect 권한이 부족함"
                        }

                    permissionText =
                        "허용된 권한 수: ${grantedPermissions.size} / " +
                                "${HealthConnectPermissions.READ_PERMISSIONS.size}"
                }

            LaunchedEffect(Unit) {

                val status = healthConnectManager.getSdkStatus()
                sdkStatus = status

                when (status) {

                    HealthConnectClient.SDK_AVAILABLE -> {

                        val grantedPermissions =
                            healthConnectManager.getGrantedPermissions()

                        statusText =
                            if (
                                grantedPermissions.containsAll(
                                    HealthConnectPermissions.READ_PERMISSIONS
                                )
                            ) {
                                "Health Connect 권한 전체 허용됨"
                            } else {
                                "Health Connect 사용 가능 / 권한 필요"
                            }

                        permissionText =
                            "허용된 권한 수: ${grantedPermissions.size} / " +
                                    "${HealthConnectPermissions.READ_PERMISSIONS.size}"
                    }

                    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> {
                        statusText =
                            "Health Connect 설치 또는 업데이트가 필요합니다."
                        permissionText = ""
                    }

                    else -> {
                        statusText =
                            "이 기기에서는 Health Connect를 사용할 수 없습니다."
                        permissionText = ""
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = statusText
                )

                if (permissionText.isNotEmpty()) {

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Text(
                        text = permissionText
                    )
                }

                if (sdkStatus == HealthConnectClient.SDK_AVAILABLE) {

                    Spacer(
                        modifier = Modifier.height(24.dp)
                    )

                    Button(
                        onClick = {
                            permissionLauncher.launch(
                                HealthConnectPermissions.READ_PERMISSIONS
                            )
                        }
                    ) {
                        Text("Health Connect 권한 요청")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {

                            coroutineScope.launch {

                                val grantedPermissions =
                                    healthConnectManager
                                        .getGrantedPermissions()

                                statusText =
                                    if (
                                        grantedPermissions.containsAll(
                                            HealthConnectPermissions.READ_PERMISSIONS
                                        )
                                    ) {
                                        "Health Connect 권한 전체 허용됨"
                                    } else {
                                        "Health Connect 권한이 부족함"
                                    }

                                permissionText =
                                    "허용된 권한 수: " +
                                            "${grantedPermissions.size} / " +
                                            "${HealthConnectPermissions.READ_PERMISSIONS.size}"
                            }
                        }
                    ) {
                        Text("권한 상태 다시 확인")
                    }

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Spacer(
                        modifier = Modifier.height(8.dp)
                    )

                    Button(
                        onClick = {

                            coroutineScope.launch {

                                if (!healthConnectManager.hasAllPermissions()) {
                                    healthDataText =
                                        "Health Connect 권한이 부족합니다."
                                    return@launch
                                }

                                healthDataText =
                                    "Health Connect 데이터 조회 중..."

                                try {

                                    val zoneId = ZoneId.systemDefault()

                                    val today = LocalDate.now(zoneId)

                                    val startDate =
                                        today.minusDays(6)

                                    val endDateExclusive =
                                        today.plusDays(1)

                                    val endTime =
                                        Instant.now()

                                    val startTime =
                                        endTime.minus(
                                            7,
                                            ChronoUnit.DAYS
                                        )

                                    val summary =
                                        healthConnectReader.readSummary(
                                            startTime = startTime,
                                            endTime = endTime,
                                            startDate = startDate,
                                            endDateExclusive = endDateExclusive,
                                            zoneId = zoneId
                                        )

                                    val stepsText =
                                        summary.dailySteps.joinToString(
                                            separator = "\n"
                                        ) {
                                            "${it.date}: ${it.totalSteps}보"
                                        }

                                    healthDataText =
                                        """
                    최근 7일 Health Connect 조회 성공

                    수면 Record: ${summary.sleepRecordCount}
                    심박 Record: ${summary.heartRateRecordCount}
                    심박 Sample: ${summary.heartRateSampleCount}
                    운동 Record: ${summary.exerciseRecordCount}

                    일별 걸음 수
                    $stepsText
                    """.trimIndent()

                                } catch (e: Exception) {

                                    healthDataText =
                                        "조회 실패: ${e.message}"
                                }
                            }
                        }
                    ) {
                        Text("최근 7일 데이터 조회")
                    }

                    if (healthDataText.isNotEmpty()) {

                        Spacer(
                            modifier = Modifier.height(16.dp)
                        )

                        Text(
                            text = healthDataText
                        )
                    }
                }
            }
        }
    }
}