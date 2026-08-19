package com.likelion.tometa.domain.record.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum RecordErrorCode implements BaseErrorCode {

    DAILY_RECORD_ALREADY_EXISTS(
            HttpStatus.CONFLICT,
            "RECORD_4091",
            "해당 날짜의 기록이 이미 존재합니다."
    ),
    DAILY_RECORD_NOT_FOUND(
            HttpStatus.NOT_FOUND,
            "RECORD_4041",
            "해당 날짜의 기록을 찾을 수 없습니다."
    ),
    DAILY_RECORD_SNAPSHOT_INCOMPLETE(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "RECORD_5001",
            "일일 기록 스냅샷 데이터가 불완전합니다."
    );

    private final HttpStatus status;
    private final String code;
    private final String message;
}
