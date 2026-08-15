package com.likelion.tometa.domain.health.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum HealthErrorCode implements BaseErrorCode {

    INVALID_HEALTH_DEVICE_TOKEN(HttpStatus.UNAUTHORIZED, "HEALTH_4011", "유효하지 않은 Health Connect 기기 토큰입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
