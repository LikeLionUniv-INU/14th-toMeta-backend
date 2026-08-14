package com.likelion.tometa.domain.user.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum UserErrorCode implements BaseErrorCode {

    INVALID_ANONYMOUS_SESSION(HttpStatus.UNAUTHORIZED, "USER_4011", "유효하지 않거나 만료된 사용자 세션입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
