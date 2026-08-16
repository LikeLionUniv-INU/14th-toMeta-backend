package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetUpdateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetDetailResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSetService;
import com.likelion.tometa.domain.user.code.UserErrorCode;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CosmeticSetControllerTest {

    @Mock
    private CosmeticSetService cosmeticSetService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        CosmeticSetController controller = new CosmeticSetController(cosmeticSetService);

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createCosmeticSet_returnsCreatedSetId() throws Exception {
        when(cosmeticSetService.createCosmeticSet(
                any(CosmeticSetCreateRequestDto.class),
                eq("session-token")
        )).thenReturn(new CosmeticSetCreateResponseDto(7L));

        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "  진정템  ",
                                  "usageTime": "morning",
                                  "userCosmeticIds": [11, 12, 15]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "setId": 7
                          }
                        }
                        """));

        ArgumentCaptor<CosmeticSetCreateRequestDto> requestCaptor =
                ArgumentCaptor.forClass(CosmeticSetCreateRequestDto.class);
        verify(cosmeticSetService).createCosmeticSet(
                requestCaptor.capture(),
                eq("session-token")
        );
        assertEquals("진정템", requestCaptor.getValue().name());
    }

    @Test
    void getCosmeticSetDetail_returnsSetAndCosmetics() throws Exception {
        CosmeticSetDetailResponseDto response = new CosmeticSetDetailResponseDto(
                7L,
                "진정 꿀조합",
                "morning",
                List.of(
                        new CosmeticSetDetailResponseDto.Cosmetic(
                                12L,
                                "아누아 어성초 77% 진정 토너",
                                null,
                                "skin_toner",
                                List.of("어성초")
                        ),
                        new CosmeticSetDetailResponseDto.Cosmetic(
                                15L,
                                "토리든 다이브인 저분자 히알루론산 세럼",
                                null,
                                "serum",
                                List.of("히알루론산")
                        )
                )
        );
        when(cosmeticSetService.getCosmeticSetDetail(7L, "session-token"))
                .thenReturn(response);

        mockMvc.perform(get("/api/cosmetic-sets/{setId}", 7L)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "setId": 7,
                            "name": "진정 꿀조합",
                            "usageTime": "morning",
                            "cosmetics": [
                              {
                                "userCosmeticId": 12,
                                "productName": "아누아 어성초 77% 진정 토너",
                                "customName": null,
                                "productType": "skin_toner",
                                "mainIngredients": ["어성초"]
                              },
                              {
                                "userCosmeticId": 15,
                                "productName": "토리든 다이브인 저분자 히알루론산 세럼",
                                "customName": null,
                                "productType": "serum",
                                "mainIngredients": ["히알루론산"]
                              }
                            ]
                          }
                        }
                        """));

        verify(cosmeticSetService).getCosmeticSetDetail(7L, "session-token");
    }

    @Test
    void getCosmeticSetDetail_returnsUnauthorizedForInvalidSession() throws Exception {
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(cosmeticSetService)
                .getCosmeticSetDetail(7L, "invalid-token");

        mockMvc.perform(get("/api/cosmetic-sets/{setId}", 7L)
                        .cookie(new Cookie("anonymous_session", "invalid-token")))
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
    void getCosmeticSetDetail_returnsUnauthorizedForMissingSession() throws Exception {
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(cosmeticSetService)
                .getCosmeticSetDetail(7L, null);

        mockMvc.perform(get("/api/cosmetic-sets/{setId}", 7L))
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
    void getCosmeticSetDetail_returnsNotFoundForMissingOrUnownedSet() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND))
                .when(cosmeticSetService)
                .getCosmeticSetDetail(99L, "session-token");

        mockMvc.perform(get("/api/cosmetic-sets/{setId}", 99L)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4041",
                          "message": "화장품 세트를 찾을 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void getCosmeticSetDetail_returnsBadRequestForNonNumericSetId() throws Exception {
        mockMvc.perform(get("/api/cosmetic-sets/abc")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "잘못된 요청입니다.",
                          "result": null
                        }
                        """));

        verify(cosmeticSetService, never()).getCosmeticSetDetail(any(), any());
    }

    @Test
    void createCosmeticSet_rejectsEmptyCosmeticIds() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED))
                .when(cosmeticSetService)
                .createCosmeticSet(
                        any(CosmeticSetCreateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "진정템",
                                  "usageTime": "morning",
                                  "userCosmeticIds": []
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4001",
                          "message": "세트에 포함할 화장품을 선택해주세요.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createCosmeticSet_rejectsFewerThanTwoCosmetics() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_MIN_ITEMS_REQUIRED))
                .when(cosmeticSetService)
                .createCosmeticSet(
                        any(CosmeticSetCreateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "진정템",
                                  "usageTime": "morning",
                                  "userCosmeticIds": [11]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4003",
                          "message": "화장품 세트는 최소 2개의 화장품으로 구성해야 합니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createCosmeticSet_rejectsDuplicateCosmeticIds() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_DUPLICATE_ITEM))
                .when(cosmeticSetService)
                .createCosmeticSet(
                        any(CosmeticSetCreateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "진정템",
                                  "usageTime": "morning",
                                  "userCosmeticIds": [11, 11, 12]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4002",
                          "message": "세트에 동일한 화장품을 중복으로 선택할 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void createCosmeticSet_rejectsUnsupportedUsageTime() throws Exception {
        doThrow(new GeneralException(GlobalErrorCode.BAD_REQUEST))
                .when(cosmeticSetService)
                .createCosmeticSet(
                        any(CosmeticSetCreateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "진정템",
                                  "usageTime": "MORNING",
                                  "userCosmeticIds": [11]
                                }
                                """))
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
    void createCosmeticSet_rejectsBlankNameBeforeCallingService() throws Exception {
        mockMvc.perform(post("/api/cosmetic-sets")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "   ",
                                  "usageTime": "both",
                                  "userCosmeticIds": [11]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "세트 이름은 필수입니다.",
                          "result": null
                        }
                        """));

        verify(cosmeticSetService, never()).createCosmeticSet(any(), any());
    }

    @Test
    void updateCosmeticSet_updatesOnlyProvidedFields() throws Exception {
        mockMvc.perform(patch("/api/cosmetic-sets/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "  새 세트 이름  "
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

        ArgumentCaptor<CosmeticSetUpdateRequestDto> requestCaptor =
                ArgumentCaptor.forClass(CosmeticSetUpdateRequestDto.class);
        verify(cosmeticSetService).updateCosmeticSet(
                eq(7L),
                requestCaptor.capture(),
                eq("session-token")
        );
        assertEquals("새 세트 이름", requestCaptor.getValue().name());
        assertNull(requestCaptor.getValue().usageTime());
        assertNull(requestCaptor.getValue().userCosmeticIds());
    }

    @Test
    void updateCosmeticSet_rejectsFewerThanTwoCosmetics() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_MIN_ITEMS_REQUIRED))
                .when(cosmeticSetService)
                .updateCosmeticSet(
                        eq(7L),
                        any(CosmeticSetUpdateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(patch("/api/cosmetic-sets/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "userCosmeticIds": [11]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4003",
                          "message": "화장품 세트는 최소 2개의 화장품으로 구성해야 합니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateCosmeticSet_returnsNotFoundForMissingSet() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND))
                .when(cosmeticSetService)
                .updateCosmeticSet(
                        eq(99L),
                        any(CosmeticSetUpdateRequestDto.class),
                        eq("session-token")
                );

        mockMvc.perform(patch("/api/cosmetic-sets/99")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "usageTime": "night"
                                }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4041",
                          "message": "화장품 세트를 찾을 수 없습니다.",
                          "result": null
                        }
                        """));
    }

    @Test
    void updateCosmeticSet_rejectsBlankNameBeforeCallingService() throws Exception {
        mockMvc.perform(patch("/api/cosmetic-sets/7")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "name": "   "
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "세트 이름은 필수입니다.",
                          "result": null
                        }
                        """));

        verify(cosmeticSetService, never()).updateCosmeticSet(any(), any(), any());
    }

    @Test
    void deleteCosmeticSet_returnsSuccess() throws Exception {
        mockMvc.perform(delete("/api/cosmetic-sets/{setId}", 7L)
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

        verify(cosmeticSetService).deleteCosmeticSet(7L, "session-token");
    }

    @Test
    void deleteCosmeticSet_returnsNotFound() throws Exception {
        doThrow(new GeneralException(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND))
                .when(cosmeticSetService)
                .deleteCosmeticSet(7L, "session-token");

        mockMvc.perform(delete("/api/cosmetic-sets/{setId}", 7L)
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isNotFound())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COSMETIC_SET_4041",
                          "message": "화장품 세트를 찾을 수 없습니다.",
                          "result": null
                        }
                        """));
    }
}
