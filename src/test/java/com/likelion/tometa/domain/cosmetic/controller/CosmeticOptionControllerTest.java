package com.likelion.tometa.domain.cosmetic.controller;

import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticOptionResponseDto;
import com.likelion.tometa.domain.cosmetic.service.CosmeticOptionService;
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

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CosmeticOptionControllerTest {

    @Mock
    private CosmeticOptionService cosmeticOptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CosmeticOptionController(cosmeticOptionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getCosmeticOptions_returnsSetsAndCosmetics() throws Exception {
        CosmeticOptionResponseDto response = new CosmeticOptionResponseDto(
                List.of(new CosmeticOptionResponseDto.SetOption(
                        1L,
                        "진정템",
                        "morning",
                        List.of("어성초", "진정", "피지조절")
                )),
                List.of(new CosmeticOptionResponseDto.CosmeticOption(
                        11L,
                        "아누아 어성초 토너",
                        "아누아",
                        "toner",
                        List.of("toner", "진정", "어성초", "판테놀")
                ))
        );
        when(cosmeticOptionService.getCosmeticOptions("session-token"))
                .thenReturn(response);

        mockMvc.perform(get("/api/cosmetic-options")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "sets": [{
                              "setId": 1,
                              "name": "진정템",
                              "usageTime": "morning",
                              "tags": ["어성초", "진정", "피지조절"]
                            }],
                            "cosmetics": [{
                              "userCosmeticId": 11,
                              "productName": "아누아 어성초 토너",
                              "brandName": "아누아",
                              "productType": "toner",
                              "tags": ["toner", "진정", "어성초", "판테놀"]
                            }]
                          }
                        }
                        """));

        verify(cosmeticOptionService).getCosmeticOptions("session-token");
    }

    @Test
    void getCosmeticOptions_returnsEmptyArrays() throws Exception {
        when(cosmeticOptionService.getCosmeticOptions("session-token"))
                .thenReturn(new CosmeticOptionResponseDto(List.of(), List.of()));

        mockMvc.perform(get("/api/cosmetic-options")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "sets": [],
                            "cosmetics": []
                          }
                        }
                        """));
    }

    @Test
    void getCosmeticOptions_returnsUnauthorizedForMissingSession() throws Exception {
        doThrow(new GeneralException(UserErrorCode.INVALID_ANONYMOUS_SESSION))
                .when(cosmeticOptionService)
                .getCosmeticOptions(null);

        mockMvc.perform(get("/api/cosmetic-options"))
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
