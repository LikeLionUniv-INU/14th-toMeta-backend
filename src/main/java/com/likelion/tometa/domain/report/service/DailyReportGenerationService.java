package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.client.OpenAiDailyReportClient;
import com.likelion.tometa.domain.report.dto.response.DailyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.support.DailyReportAiResult;
import com.likelion.tometa.domain.report.support.ReportGenerationResult;
import com.likelion.tometa.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyReportGenerationService {

    private final DailyReportGenerationTransactionService transactionService;
    private final OpenAiDailyReportClient openAiDailyReportClient;

    public ReportGenerationResult<DailyReportGenerationResponseDto> generate(
            User user,
            LocalDate date
    ) {
        DailyReportGenerationTransactionService.Preparation preparation =
                transactionService.prepare(user, date);

        if (!preparation.requiresGeneration()) {
            return ReportGenerationResult.alreadyCompleted(
                    preparation.completedResponse()
            );
        }

        try {
            DailyReportAiResult aiResult =
                    openAiDailyReportClient.generate(
                            preparation.context()
                    );

            return ReportGenerationResult.generated(
                    transactionService.complete(
                            preparation.reportId(),
                            preparation.generationVersion(),
                            preparation.healthSummaryId(),
                            aiResult
                    )
            );
        } catch (RuntimeException e) {
            transactionService.reset(
                    preparation.reportId(),
                    preparation.generationVersion()
            );
            throw e;
        }
    }
}
