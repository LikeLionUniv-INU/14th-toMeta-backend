package com.likelion.tometa.domain.mypage.dto.response;

public record MypageResponseDto(
        String nickname,
        boolean healthConnectLinked,
        boolean pushConnected,
        NotificationSettings notificationSettings
) {

    public record NotificationSettings(
            boolean dailyReportEnabled,
            boolean recordReminderEnabled,
            String recordReminderTime,
            boolean weeklyReportEnabled,
            String weeklyReportTime
    ) {

        public static NotificationSettings defaults() {
            return new NotificationSettings(
                    false,
                    false,
                    null,
                    false,
                    null
            );
        }
    }
}
