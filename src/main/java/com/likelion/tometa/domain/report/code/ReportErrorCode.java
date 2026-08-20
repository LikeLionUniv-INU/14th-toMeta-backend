package com.likelion.tometa.domain.report.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ReportErrorCode implements BaseErrorCode {

    DAILY_REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_4041",
            "해당 날짜의 일간 리포트가 존재하지 않습니다."
    ),
    WEEKLY_REPORT_SOURCE_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_4042",
            "주간 리포트를 생성할 일간 리포트가 존재하지 않습니다."
    ),
    WEEKLY_REPORT_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "REPORT_4043",
            "주간 리포트가 존재하지 않습니다."
    ),
    DAILY_REPORT_GENERATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "REPORT_4091",
            "일간 리포트가 생성 중입니다."
    ),
    DAILY_REPORT_GENERATION_STALE(
            HttpStatus.CONFLICT,
            "REPORT_4092",
            "일간 리포트 생성 기준이 변경되었습니다. 다시 요청해 주세요."
    ),
    WEEKLY_REPORT_GENERATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "REPORT_4093",
            "주간 리포트가 생성 중입니다."
    ),
    DAILY_REPORT_AI_GENERATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "REPORT_5021",
            "AI 일간 리포트 생성에 실패했습니다."
    ),
    WEEKLY_REPORT_AI_GENERATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "REPORT_5022",
            "AI 주간 리포트 생성에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
