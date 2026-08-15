package com.likelion.tometa.domain.user.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum PushErrorCode implements BaseErrorCode {

    PUSH_TOKEN_NOT_FOUND(HttpStatus.NOT_FOUND, "PUSH_4041", "등록된 푸시 정보를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}