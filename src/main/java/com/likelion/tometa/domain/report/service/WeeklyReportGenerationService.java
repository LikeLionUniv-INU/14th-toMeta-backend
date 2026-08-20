package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.client.OpenAiWeeklyReportClient;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.support.WeeklyReportAiResult;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WeeklyReportGenerationService {

    private final WeeklyReportGenerationTransactionService transactionService;
    private final OpenAiWeeklyReportClient openAiWeeklyReportClient;

    public ReportGenerationResult<WeeklyReportGenerationResponseDto> generate(
            User user,
            LocalDate startDate
    ) {
        WeeklyReportGenerationTransactionService.Preparation preparation =
                transactionService.prepare(
                        user,
                        startDate
                );

        if (!preparation.requiresGeneration()) {
            return ReportGenerationResult.alreadyCompleted(
                    preparation.completedResponse()
            );
        }

        try {
            WeeklyReportAiResult aiResult =
                    openAiWeeklyReportClient.generate(
                            preparation.context()
                    );

            return ReportGenerationResult.generated(
                    transactionService.complete(
                            preparation.reportId(),
                            preparation.generationStartedAt(),
                            aiResult
                    )
            );
        } catch (RuntimeException e) {
            transactionService.reset(
                    preparation.reportId(),
                    preparation.generationStartedAt()
            );
            throw e;
        }
    }
}
