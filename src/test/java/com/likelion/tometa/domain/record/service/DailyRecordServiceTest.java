package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.record.code.RecordErrorCode;
import com.likelion.tometa.domain.record.dto.request.DailyRecordCreateRequestDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordCreateResponseDto;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmetic;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmeticSet;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticSetRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DailyRecordServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;
    @Mock
    private DailyRecordRepository dailyRecordRepository;
    @Mock
    private DailyRecordCosmeticRepository dailyRecordCosmeticRepository;
    @Mock
    private DailyRecordCosmeticSetRepository dailyRecordCosmeticSetRepository;
    @Mock
    private DailyReportRepository dailyReportRepository;
    @Mock
    private UserCosmeticRepository userCosmeticRepository;
    @Mock
    private CosmeticSetRepository cosmeticSetRepository;
    @Mock
    private CosmeticSetItemRepository cosmeticSetItemRepository;
    @Mock
    private CosmeticIngredientRepository cosmeticIngredientRepository;
    @Mock
    private DailyRecordImageAttachmentService imageAttachmentService;
    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private DailyRecordService dailyRecordService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
    }

    @Test
    void create_sortsSetsAndCosmeticsAndCreatesCollectingReport() throws Exception {
        UserCosmetic cosmetic5 = userCosmetic(5L);
        UserCosmetic cosmetic9 = userCosmetic(9L);
        UserCosmetic cosmetic12 = userCosmetic(12L);
        UserCosmetic cosmetic15 = userCosmetic(15L);
        CosmeticSet set3 = cosmeticSet(3L, CosmeticSetUsageTime.MORNING);
        CosmeticSet set8 = cosmeticSet(8L, CosmeticSetUsageTime.BOTH);

        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(15L, 12L),
                List.of(8L, 3L),
                List.of(),
                List.of(),
                null
        );

        when(dailyRecordRepository.existsByUserAndRecordDate(user, request.date()))
                .thenReturn(false);
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(
                eq(Set.of(12L, 15L)),
                eq(user)
        )).thenReturn(List.of(cosmetic12, cosmetic15));
        when(cosmeticSetRepository.findAllByIdInAndUserOrderById(
                eq(Set.of(3L, 8L)),
                eq(user)
        )).thenReturn(List.of(set3, set8));
        when(cosmeticSetItemRepository
                .findAllActiveByCosmeticSetsOrderBySetAndCosmeticId(List.of(set3, set8)))
                .thenReturn(List.of(
                        setItem(set3, cosmetic5),
                        setItem(set3, cosmetic9),
                        setItem(set8, cosmetic5),
                        setItem(set8, cosmetic12)
                ));
        when(cosmeticIngredientRepository
                .findAllByCosmeticProductIdsOrderByIngredientOrder(any()))
                .thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(dailyRecordRepository.saveAndFlush(any(DailyRecord.class)))
                .thenAnswer(invocation -> {
                    DailyRecord record = invocation.getArgument(0);
                    ReflectionTestUtils.setField(record, "id", 37L);
                    return record;
                });

        DailyRecordCreateResponseDto result = dailyRecordService.create(request, SESSION_TOKEN);

        assertEquals(37L, result.recordId());
        assertEquals(request.date(), result.date());

        ArgumentCaptor<Iterable<DailyRecordCosmeticSet>> setCaptor = iterableCaptor();
        verify(dailyRecordCosmeticSetRepository).saveAll(setCaptor.capture());
        List<DailyRecordCosmeticSet> savedSets = toList(setCaptor.getValue());
        assertEquals(List.of(3L, 8L), savedSets.stream()
                .map(DailyRecordCosmeticSet::getSourceCosmeticSetId)
                .toList());
        assertEquals(List.of(1, 2), savedSets.stream()
                .map(DailyRecordCosmeticSet::getSortOrder)
                .toList());

        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor = iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        List<DailyRecordCosmetic> savedCosmetics = toList(cosmeticCaptor.getValue());
        assertEquals(List.of(5L, 9L, 12L, 15L), savedCosmetics.stream()
                .map(snapshot -> snapshot.getUserCosmetic().getId())
                .toList());
        assertEquals(List.of(1, 2, 3, 4), savedCosmetics.stream()
                .map(DailyRecordCosmetic::getSortOrder)
                .toList());

        ArgumentCaptor<DailyReport> reportCaptor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(reportCaptor.capture());
        assertEquals("collecting", reportCaptor.getValue().getReportStatus());
        assertEquals(37L, reportCaptor.getValue().getDailyRecord().getId());
        verify(imageAttachmentService).attach(any(DailyRecord.class), eq(user), eq(List.of()));
    }

    @Test
    void create_acceptsNightSelectionWithoutMorningSelection() throws Exception {
        UserCosmetic cosmetic22 = userCosmetic(22L);
        DailyRecordCreateRequestDto request = request(
                "good",
                List.of(),
                List.of(),
                List.of(22L),
                List.of(),
                null
        );

        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(Set.of(22L), user))
                .thenReturn(List.of(cosmetic22));
        when(cosmeticIngredientRepository
                .findAllByCosmeticProductIdsOrderByIngredientOrder(any()))
                .thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(dailyRecordRepository.saveAndFlush(any(DailyRecord.class)))
                .thenAnswer(invocation -> {
                    DailyRecord record = invocation.getArgument(0);
                    ReflectionTestUtils.setField(record, "id", 38L);
                    return record;
                });

        DailyRecordCreateResponseDto result = dailyRecordService.create(request, SESSION_TOKEN);

        assertEquals(38L, result.recordId());
        verify(dailyRecordCosmeticRepository).saveAll(any());
    }

    @Test
    void create_rejectsWhenAllCosmeticSelectionsAreEmpty() {
        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verify(dailyRecordRepository, never()).saveAndFlush(any());
    }

    @Test
    void create_rejectsFutureDateInKorea() {
        DailyRecordCreateRequestDto request = new DailyRecordCreateRequestDto(
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1),
                "normal",
                List.of(12L),
                List.of(),
                List.of(),
                List.of(),
                null,
                List.of(),
                null
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void create_requiresNonBlankMemoForBadStatus() {
        DailyRecordCreateRequestDto request = request(
                "bad",
                List.of(12L),
                List.of(),
                List.of(),
                List.of(),
                "   "
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void create_returnsConflictForExistingDate() {
        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(12L),
                List.of(),
                List.of(),
                List.of(),
                null
        );
        when(dailyRecordRepository.existsByUserAndRecordDate(user, request.date()))
                .thenReturn(true);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(RecordErrorCode.DAILY_RECORD_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void create_rejectsMorningUseOfNightSet() {
        CosmeticSet nightSet = cosmeticSet(7L, CosmeticSetUsageTime.NIGHT);
        UserCosmetic cosmetic = userCosmetic(22L);
        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(),
                List.of(7L),
                List.of(),
                List.of(),
                null
        );

        when(cosmeticSetRepository.findAllByIdInAndUserOrderById(Set.of(7L), user))
                .thenReturn(List.of(nightSet));
        when(cosmeticSetItemRepository
                .findAllActiveByCosmeticSetsOrderBySetAndCosmeticId(List.of(nightSet)))
                .thenReturn(List.of(setItem(nightSet, cosmetic)));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    private DailyRecordCreateRequestDto request(
            String skinStatus,
            List<Long> morningCosmeticIds,
            List<Long> morningSetIds,
            List<Long> nightCosmeticIds,
            List<Long> nightSetIds,
            String memo
    ) {
        return new DailyRecordCreateRequestDto(
                LocalDate.of(2026, 8, 12),
                skinStatus,
                morningCosmeticIds,
                morningSetIds,
                nightCosmeticIds,
                nightSetIds,
                null,
                List.of(),
                memo
        );
    }

    private UserCosmetic userCosmetic(Long id) {
        CosmeticProduct product = CosmeticProduct.builder()
                .sourceType("manual")
                .productName("product-" + id)
                .productType("serum")
                .build();
        ReflectionTestUtils.setField(product, "id", id + 100L);
        UserCosmetic cosmetic = UserCosmetic.builder()
                .user(user)
                .cosmeticProduct(product)
                .build();
        ReflectionTestUtils.setField(cosmetic, "id", id);
        return cosmetic;
    }

    private CosmeticSet cosmeticSet(Long id, CosmeticSetUsageTime usageTime) {
        CosmeticSet cosmeticSet = CosmeticSet.builder()
                .user(user)
                .name("set-" + id)
                .usageTime(usageTime)
                .build();
        ReflectionTestUtils.setField(cosmeticSet, "id", id);
        return cosmeticSet;
    }

    private CosmeticSetItem setItem(CosmeticSet cosmeticSet, UserCosmetic cosmetic) {
        return CosmeticSetItem.builder()
                .cosmeticSet(cosmeticSet)
                .userCosmetic(cosmetic)
                .itemOrder(1)
                .build();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<Iterable<T>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }

    private <T> List<T> toList(Iterable<T> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }
}
