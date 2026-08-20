package com.likelion.tometa.domain.user.controller;

import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.domain.user.dto.request.UserProfileRequestDto;
import com.likelion.tometa.domain.user.service.UserService;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserControllerTest {

    @Mock
    private UserService userService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new UserController(userService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void updateProfile_updatesOnlyProvidedFields() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "새닉네임"
                                }
                                """)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": null
                        }
                        """));

        ArgumentCaptor<UserProfileRequestDto> captor =
                ArgumentCaptor.forClass(UserProfileRequestDto.class);
        verify(userService).saveProfile(captor.capture(),
                org.mockito.ArgumentMatchers.eq("session-token"));
        UserProfileRequestDto request = captor.getValue();
        assertTrue(request.hasNickname());
        assertEquals("새닉네임", request.nickname());
        assertFalse(request.hasGender());
        assertFalse(request.hasAgeGroup());
        assertFalse(request.hasSkinType());
    }

    @Test
    void updateProfile_rejectsNicknameWithSpecialCharacters() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "김도영!"
                                }
                                """)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "닉네임은 한글, 영문, 숫자만 사용할 수 있습니다.",
                          "result": null
                        }
                        """));

        verify(userService, never()).saveProfile(any(), any());
    }

    @Test
    void updateProfile_rejectsNicknameLongerThanTenCharacters() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "nickname": "abcdefghijk"
                                }
                                """)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "닉네임은 1자 이상 10자 이하여야 합니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateProfile_rejectsExplicitNull() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "gender": null
                                }
                                """)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "성별은 null일 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateProfile_rejectsEmptyRequest() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("{}")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "수정할 프로필 정보를 입력해주세요.",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateProfile_rejectsInvalidChoiceValues() throws Exception {
        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "gender": "other",
                                  "ageGroup": "50s",
                                  "skinType": "normal"
                                }
                                """)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateProfile_returnsUnauthorizedForMissingSession() throws Exception {
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(userService)
                .saveProfile(any(UserProfileRequestDto.class), isNull());

        mockMvc.perform(patch("/api/users/me/profile")
                        .contentType(APPLICATION_JSON)
                        .content("""
                                {
                                  "skinType": "combination_dry"
                                }
                                """))
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
