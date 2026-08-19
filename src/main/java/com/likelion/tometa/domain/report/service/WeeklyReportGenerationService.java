package com.likelion.tometa.domain.report.service;

import com.likelion.tometa.domain.report.client.OpenAiWeeklyReportClient;
import com.likelion.tometa.domain.report.dto.response.WeeklyReportGenerationResponseDto;
import com.likelion.tometa.domain.report.support.WeeklyReportAiResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
@RequiredArgsConstructor
public class WeeklyReportGenerationService {

    private final WeeklyReportGenerationTransactionService transactionService;
    private final OpenAiWeeklyReportClient openAiWeeklyReportClient;

    public WeeklyReportGenerationResponseDto generate(
            LocalDate startDate,
            String sessionToken
    ) {
        WeeklyReportGenerationTransactionService.Preparation preparation =
                transactionService.prepare(
                        startDate,
                        sessionToken
                );

        if (!preparation.requiresGeneration()) {
            return preparation.completedResponse();
        }

        try {
            WeeklyReportAiResult aiResult =
                    openAiWeeklyReportClient.generate(
                            preparation.context()
                    );

            return transactionService.complete(
                    preparation.reportId(),
                    aiResult
            );
        } catch (RuntimeException e) {
            transactionService.reset(
                    preparation.reportId()
            );
            throw e;
        }
    }
}
