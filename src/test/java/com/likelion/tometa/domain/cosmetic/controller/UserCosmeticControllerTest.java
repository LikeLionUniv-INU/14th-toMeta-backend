package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequest;
import com.likelion.tometa.domain.cosmetic.dto.response.ManualCosmeticCreateResponse;
import com.likelion.tometa.domain.cosmetic.service.UserCosmeticService;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserCosmeticControllerTest {

    @Mock
    private UserCosmeticService userCosmeticService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserCosmeticController controller = new UserCosmeticController(userCosmeticService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createManualCosmetic_returnsCreatedCosmetic() throws Exception {
        when(userCosmeticService.createManualCosmetic(
                any(ManualCosmeticCreateRequest.class),
                eq("session-token")
        )).thenReturn(new ManualCosmeticCreateResponse(13L, "내가 쓰는 진정 세럼"));

        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "usageTime": "morning",
                                  "productName": "내가 쓰는 진정 세럼",
                                  "productType": "serum",
                                  "mainIngredients": ["히알루론산", "나이아신아마이드", "판테놀"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "userCosmeticId": 13,
                            "productName": "내가 쓰는 진정 세럼"
                          }
                        }
                        """));
    }

    @Test
    void createManualCosmetic_rejectsFewerThanThreeIngredients() throws Exception {
        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "usageTime": "morning",
                                  "productName": "제품명",
                                  "productType": "serum",
                                  "mainIngredients": ["히알루론산", "판테놀"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "주요 성분은 최소 3개 이상 입력해야 합니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createManualCosmetic_rejectsBlankIngredientName() throws Exception {
        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "usageTime": "morning",
                                  "productName": "제품명",
                                  "productType": "serum",
                                  "mainIngredients": ["히알루론산", "   ", "판테놀"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "주요 성분명은 비어 있을 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createManualCosmetic_returnsUnauthorizedForInvalidSession() throws Exception {
        when(userCosmeticService.createManualCosmetic(
                any(ManualCosmeticCreateRequest.class),
                eq("invalid-token")
        )).thenThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION));

        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "invalid-token"))
                        .content(validRequestBody()))
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

    @Test
    void createManualCosmetic_returnsCosmeticErrorForTooManyIngredients() throws Exception {
        when(userCosmeticService.createManualCosmetic(
                any(ManualCosmeticCreateRequest.class),
                eq("session-token")
        )).thenThrow(new GeneralException(CosmeticErrorCode.MAIN_INGREDIENTS_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "usageTime": "morning",
                                  "productName": "제품명",
                                  "productType": "serum",
                                  "mainIngredients": ["1", "2", "3", "4", "5", "6"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_4001",
                          "message": "주요 성분은 최대 5개까지 입력할 수 있습니다.",
                          "result": null
                        }
                        """));
    }

    private String validRequestBody() {
        return """
                {
                  "usageTime": "morning",
                  "productName": "제품명",
                  "productType": "serum",
                  "mainIngredients": ["히알루론산", "나이아신아마이드", "판테놀"]
                }
                """;
    }
}
