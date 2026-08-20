package com.likelion.tometa.domain.record.controller;

import com.likelion.tometa.domain.record.code.RecordErrorCode;
import com.likelion.tometa.domain.record.dto.request.DailyRecordCreateRequestDto;
import com.likelion.tometa.domain.record.dto.request.DailyRecordUpdateRequestDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordCreateResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordDetailResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordUpdateResponseDto;
import com.likelion.tometa.domain.record.service.DailyRecordService;
import com.likelion.tometa.global.exception.GeneralException;
import com.likelion.tometa.global.exception.GlobalExceptionHandler;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
    void createDailyRecord_acceptsMorningAndNightSelectionsAndFiveImages() throws Exception {
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
                                  "nightCosmeticIds": [22],
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

    @Test
    void getDailyRecord_returnsDetail() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 12);
        DailyRecordDetailResponseDto response = new DailyRecordDetailResponseDto(
                37L,
                date,
                "bad",
                List.of(
                        new DailyRecordDetailResponseDto.SetSelection(
                                3L,
                                "calming set",
                                List.of("heartleaf", "panthenol")
                        ),
                        new DailyRecordDetailResponseDto.CosmeticSelection(
                                11L,
                                "barrier cream",
                                List.of("ceramide")
                        )
                ),
                List.of(),
                "food",
                List.of(new DailyRecordDetailResponseDto.Image(
                        "skin-images/1/image.jpg",
                        "https://signed.example/image.jpg"
                )),
                "memo"
        );
        when(dailyRecordService.getByDate(date, "session-token"))
                .thenReturn(response);

        mockMvc.perform(get("/api/daily-records/2026-08-12")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recordId").value(37))
                .andExpect(jsonPath("$.result.morningSelections[0].selectionType")
                        .value("SET"))
                .andExpect(jsonPath("$.result.morningSelections[0].cosmeticSetId")
                        .value(3))
                .andExpect(jsonPath("$.result.morningSelections[1].selectionType")
                        .value("COSMETIC"))
                .andExpect(jsonPath("$.result.morningSelections[1].userCosmeticId")
                        .value(11))
                .andExpect(jsonPath("$.result.images[0].imageKey")
                        .value("skin-images/1/image.jpg"));
    }

    @Test
    void getDailyRecord_returnsNotFoundForMissingDate() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(dailyRecordService.getByDate(date, "session-token"))
                .thenThrow(new GeneralException(RecordErrorCode.DAILY_RECORD_NOT_FOUND));

        mockMvc.perform(get("/api/daily-records/2026-08-12")
                        .cookie(new Cookie("anonymous_session", "session-token")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_4041"))
                .andExpect(jsonPath("$.result").doesNotExist());
    }

    @Test
    void getDailyRecord_rejectsInvalidDateFormat() throws Exception {
        mockMvc.perform(get("/api/daily-records/2026-08-xx"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400"));

        verify(dailyRecordService, never()).getByDate(any(), any());
    }

    @Test
    void updateDailyRecord_distinguishesOmittedFieldsFromExplicitNull() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(dailyRecordService.update(
                eq(date),
                any(DailyRecordUpdateRequestDto.class),
                eq("session-token")
        )).thenReturn(new DailyRecordUpdateResponseDto(37L, date));

        mockMvc.perform(patch("/api/daily-records/2026-08-12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("""
                                {
                                  "memo": null,
                                  "imageKeys": [],
                                  "morningCosmeticSetIds": [3]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result.recordId").value(37))
                .andExpect(jsonPath("$.result.date").value("2026-08-12"));

        ArgumentCaptor<DailyRecordUpdateRequestDto> captor =
                ArgumentCaptor.forClass(DailyRecordUpdateRequestDto.class);
        verify(dailyRecordService).update(eq(date), captor.capture(), eq("session-token"));
        DailyRecordUpdateRequestDto request = captor.getValue();
        assertTrue(request.hasMemo());
        assertNull(request.memo());
        assertTrue(request.hasImageKeys());
        assertEquals(List.of(), request.imageKeys());
        assertTrue(request.hasMorningCosmeticSetIds());
        assertEquals(
                List.of(3L),
                request.morningCosmeticSetIds()
        );
        assertFalse(request.hasFoodMemo());
        assertFalse(request.hasNightCosmeticIds());
    }

    @Test
    void updateDailyRecord_returnsRecordNotFound() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(dailyRecordService.update(
                eq(date),
                any(DailyRecordUpdateRequestDto.class),
                eq("session-token")
        )).thenThrow(new GeneralException(RecordErrorCode.DAILY_RECORD_NOT_FOUND));

        mockMvc.perform(patch("/api/daily-records/2026-08-12")
                        .contentType(MediaType.APPLICATION_JSON)
                        .cookie(new Cookie("anonymous_session", "session-token"))
                        .content("{\"memo\":\"updated\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RECORD_4041"));
    }
}
