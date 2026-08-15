package com.likelion.tometa.domain.user.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record UserNotificationSettingRequestDto(

        @NotNull(message = "일간 리포트 알림 설정은 필수입니다.")
        Boolean dailyReportEnabled,

        @NotNull(message = "기록 작성 알림 설정은 필수입니다.")
        Boolean recordReminderEnabled,

        @Pattern(
                regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "기록 작성 알림 시간은 HH:mm 형식이어야 합니다."
        )
        String recordReminderTime,

        @NotNull(message = "주간 리포트 알림 설정은 필수입니다.")
        Boolean weeklyReportEnabled,

        @Pattern(
                regexp = "^([01]\\d|2[0-3]):[0-5]\\d$",
                message = "주간 리포트 알림 시간은 HH:mm 형식이어야 합니다."
        )
        String weeklyReportTime
) {

    @AssertTrue(message = "기록 작성 알림 활성화 시 알림 시간이 필요합니다.")
    public boolean isRecordReminderValid() {
        return !Boolean.TRUE.equals(recordReminderEnabled) || recordReminderTime != null;
    }

    @AssertTrue(message = "주간 리포트 알림 활성화 시 알림 시간이 필요합니다.")
    public boolean isWeeklyReportValid() {
        return !Boolean.TRUE.equals(weeklyReportEnabled) || weeklyReportTime != null;
    }
}