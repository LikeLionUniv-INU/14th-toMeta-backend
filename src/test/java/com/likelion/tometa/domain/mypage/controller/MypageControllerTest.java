package com.likelion.tometa.domain.mypage.controller;

import com.likelion.tometa.domain.mypage.dto.response.MypageResponseDto;
import com.likelion.tometa.domain.mypage.service.MypageService;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class MypageControllerTest {

    @Mock
    private MypageService mypageService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new MypageController(mypageService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getMypage_returnsProfileAndConnectionSummary() throws Exception {
        MypageResponseDto response = new MypageResponseDto(
                "김도영",
                true,
                true,
                new MypageResponseDto.NotificationSettings(
                        true,
                        true,
                        "22:00",
                        true,
                        "22:00"
                )
        );
        when(mypageService.getMypage("session-token")).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "nickname": "김도영",
                            "healthConnectLinked": true,
                            "pushConnected": true,
                            "notificationSettings": {
                              "dailyReportEnabled": true,
                              "recordReminderEnabled": true,
                              "recordReminderTime": "22:00",
                              "weeklyReportEnabled": true,
                              "weeklyReportTime": "22:00"
                            }
                          }
                        }
                        """));

        verify(mypageService).getMypage("session-token");
    }

    @Test
    void getMypage_returnsDefaultNotificationSettingsObject() throws Exception {
        MypageResponseDto response = new MypageResponseDto(
                null,
                false,
                false,
                MypageResponseDto.NotificationSettings.defaults()
        );
        when(mypageService.getMypage("session-token")).thenReturn(response);

        mockMvc.perform(get("/api/users/me")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "nickname": null,
                            "healthConnectLinked": false,
                            "pushConnected": false,
                            "notificationSettings": {
                              "dailyReportEnabled": false,
                              "recordReminderEnabled": false,
                              "recordReminderTime": null,
                              "weeklyReportEnabled": false,
                              "weeklyReportTime": null
                            }
                          }
                        }
                        """));
    }

    @Test
    void getMypage_returnsUnauthorizedForMissingSession() throws Exception {
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(mypageService)
                .getMypage(null);

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "USER_4011",
                          "message": "유효하지 않거나 만료된 사용자 세션입니다.",
                          "result": null
                        }
                        """));
    }
}
