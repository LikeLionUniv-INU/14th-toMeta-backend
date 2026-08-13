package com.likelion.tometa.domain.onboarding.code;

import com.likelion.tometa.global.code.BaseErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum OnboardingErrorCode implements BaseErrorCode {

    REQUIRED_CONSENT_NOT_AGREED(HttpStatus.BAD_REQUEST, "CONSENT_4001", "필수 약관에 모두 동의해야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
