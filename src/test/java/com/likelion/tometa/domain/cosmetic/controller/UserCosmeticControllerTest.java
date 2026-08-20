package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.ManualCosmeticCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.SearchedCosmeticCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.service.SearchedCosmeticRegistrationService;
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
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserCosmeticControllerTest {

    @Mock
    private UserCosmeticService userCosmeticService;

    @Mock
    private SearchedCosmeticRegistrationService searchedCosmeticRegistrationService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        UserCosmeticController controller = new UserCosmeticController(
                userCosmeticService,
                searchedCosmeticRegistrationService
        );

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createManualCosmetic_returnsCreatedCosmetic() throws Exception {
        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "productName": "내가 쓰는 진정 세럼",
                                  "productType": "serum",
                                  "mainIngredients": ["히알루론산", "나이아신아마이드"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createSearchedCosmetic_returnsProductNameWithBrand() throws Exception {
        when(searchedCosmeticRegistrationService.create(any(), eq("session-token")))
                .thenReturn(new SearchedCosmeticCreateResponseDto(
                        11L,
                        "토리든 다이브인 세럼",
                        "serum",
                        java.util.List.of("세럼", "보습", "히알루론산")
                ));

        mockMvc.perform(post("/api/user-cosmetics/search-result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "searchId": "search-id",
                                  "itemId": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "userCosmeticId": 11,
                            "productName": "토리든 다이브인 세럼",
                            "productType": "serum",
                            "tags": ["세럼", "보습", "히알루론산"]
                          }
                        }
                        """));
    }

    @Test
    void createManualCosmetic_rejectsEmptyIngredients() throws Exception {
        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "productName": "제품명",
                                  "productType": "serum",
                                  "mainIngredients": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "주요 성분은 최소 1개 이상 입력해야 합니다.",
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
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(userCosmeticService)
                .createManualCosmetic(
                        any(ManualCosmeticCreateRequestDto.class),
                        eq("invalid-token")
                );

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
        doThrow(new GeneralException(CosmeticErrorCode.MAIN_INGREDIENTS_LIMIT_EXCEEDED))
                .when(userCosmeticService)
                .createManualCosmetic(
                        any(ManualCosmeticCreateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(post("/api/user-cosmetics/manual")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "productName": "제품명",
                                  "productType": "serum",
                                  "mainIngredients": ["1", "2", "3", "4"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_4001",
                          "message": "주요 성분은 최대 3개까지 입력할 수 있습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void deleteUserCosmetic_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/user-cosmetics/{userCosmeticId}", 1L)
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

        verify(userCosmeticService).deleteUserCosmetic(1L, "session-token");
    }

    @Test
    void deleteUserCosmetic_returnsNotFound() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND))
                .when(userCosmeticService)
                .deleteUserCosmetic(1L, "session-token");

        mockMvc.perform(delete("/api/user-cosmetics/{userCosmeticId}", 1L)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_4042",
                          "message": "등록된 화장품을 찾을 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    private String validRequestBody() {
        return """
                {
                  "productName": "제품명",
                  "productType": "serum",
                  "mainIngredients": ["히알루론산", "나이아신아마이드", "판테놀"]
                }
                """;
    }
}
