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
import androidx.health.connect.client.PermissionController
import com.likelion.tometa.healthconnect.HealthConnectManager
import com.likelion.tometa.healthconnect.HealthConnectPermissions
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val healthConnectManager = HealthConnectManager(this)

        setContent {

            var statusText by remember {
                mutableStateOf("Health Connect 상태 확인 중...")
            }

            var permissionText by remember {
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

                if (!healthConnectManager.isAvailable()) {
                    statusText = "Health Connect 사용 불가"
                    return@LaunchedEffect
                }

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

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center
            ) {

                Text(
                    text = statusText
                )

                Spacer(
                    modifier = Modifier.height(8.dp)
                )

                Text(
                    text = permissionText
                )

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

                Button(
                    onClick = {
                        healthConnectManager.openHealthConnectSettings()
                    }
                ) {
                    Text("Health Connect 설정 열기")
                }
            }
        }
    }
}