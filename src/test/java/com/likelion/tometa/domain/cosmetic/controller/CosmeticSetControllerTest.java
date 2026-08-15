package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSetService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
}
