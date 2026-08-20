package com.likelion.tometa.domain.mypage.dto.request;

public record NotificationSettingsUpdateRequestDto(
        Boolean dailyReportEnabled,
        Boolean recordReminderEnabled,
        String recordReminderTime,
        Boolean weeklyReportEnabled,
        String weeklyReportTime
) {
}
