package com.likelion.tometa.domain.record.controller;

import com.likelion.tometa.domain.record.dto.request.DailyRecordCreateRequestDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordCreateResponseDto;
import com.likelion.tometa.domain.record.service.DailyRecordService;
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

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class DailyRecordControllerTest {

    @Mock
    private DailyRecordService dailyRecordService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DailyRecordController(dailyRecordService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createDailyRecord_acceptsOnlyMorningSelectionAndFiveImages() throws Exception {
        when(dailyRecordService.create(
                any(DailyRecordCreateRequestDto.class),
                eq("session-token")
        )).thenReturn(new DailyRecordCreateResponseDto(
                37L,
                LocalDate.of(2026, 8, 12)
        ));

        mockMvc.perform(post("/api/daily-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "date": "2026-08-12",
                                  "skinStatus": "bad",
                                  "morningCosmeticIds": [12],
                                  "morningCosmeticSetIds": [3],
                                  "nightCosmeticIds": [],
                                  "foodMemo": "  아침에 마라탕  ",
                                  "imageKeys": ["key-1", "key-2", "key-3", "key-4", "key-5"],
                                  "memo": "  볼이 조금 따가웠음  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().json("""
                        {
                          "isSuccess": true,
                          "code": "COMMON_200",
                          "message": "요청에 성공했습니다.",
                          "result": {
                            "recordId": 37,
                            "date": "2026-08-12"
                          }
                        }
                        """));

        verify(dailyRecordService).create(any(DailyRecordCreateRequestDto.class),
                eq("session-token"));
    }

    @Test
    void createDailyRecord_rejectsMoreThanFiveImages() throws Exception {
        mockMvc.perform(post("/api/daily-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-08-12",
                                  "skinStatus": "normal",
                                  "morningCosmeticIds": [12],
                                  "nightCosmeticIds": [],
                                  "imageKeys": ["1", "2", "3", "4", "5", "6"]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("""
                        {
                          "isSuccess": false,
                          "code": "COMMON_400",
                          "message": "피부 사진은 최대 5장까지 등록할 수 있습니다.",
                          "result": null
                        }
                        """));

        verify(dailyRecordService, never()).create(any(), any());
    }

    @Test
    void createDailyRecord_requiresBothCosmeticArrayFields() throws Exception {
        mockMvc.perform(post("/api/daily-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026-08-12",
                                  "skinStatus": "normal",
                                  "morningCosmeticIds": [12]
                                }
                                """))
                .andExpect(status().isBadRequest());

        verify(dailyRecordService, never()).create(any(), any());
    }

    @Test
    void createDailyRecord_rejectsInvalidDateFormatAsBadRequest() throws Exception {
        mockMvc.perform(post("/api/daily-records")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "date": "2026/08/12",
                                  "skinStatus": "normal",
                                  "morningCosmeticIds": [12],
                                  "nightCosmeticIds": []
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

        verify(dailyRecordService, never()).create(any(), any());
    }
}
