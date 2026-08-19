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
    DAILY_REPORT_GENERATION_IN_PROGRESS(
            HttpStatus.CONFLICT,
            "REPORT_4091",
            "일간 리포트가 생성 중입니다."
    ),
    DAILY_REPORT_AI_GENERATION_FAILED(
            HttpStatus.BAD_GATEWAY,
            "REPORT_5021",
            "AI 일간 리포트 생성에 실패했습니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
