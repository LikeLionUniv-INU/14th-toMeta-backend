package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.client.OpenAiDailyReportClient;
import com.likelion.tometa.domain.report.dto.response.DailyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.support.DailyReportAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class DailyReportGenerationService {

    private final DailyReportGenerationTransactionService transactionService;
    private final OpenAiDailyReportClient openAiDailyReportClient;

    public DailyReportGenerationResponseDto generate(
            LocalDate date,
            String sessionToken
    ) {
        DailyReportGenerationTransactionService.Preparation preparation =
                transactionService.prepare(date, sessionToken);

        if (!preparation.requiresGeneration()) {
            return preparation.completedResponse();
        }

        try {
            DailyReportAiResult aiResult =
                    openAiDailyReportClient.generate(
                            preparation.context()
                    );

            return transactionService.complete(
                    preparation.reportId(),
                    preparation.healthSummaryId(),
                    aiResult
            );
        } catch (RuntimeException e) {
            transactionService.reset(preparation.reportId());
            throw e;
        }
    }
}
