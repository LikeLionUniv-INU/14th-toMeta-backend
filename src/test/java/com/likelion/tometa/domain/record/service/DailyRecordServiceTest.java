package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticTagRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.record.code.RecordErrorCode;
import com.likelion.tometa.domain.record.dto.request.DailyRecordCreateRequestDto;
import com.likelion.tometa.domain.record.dto.request.DailyRecordUpdateRequestDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordCreateResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordDetailResponseDto;
import com.likelion.tometa.domain.record.dto.response.DailyRecordUpdateResponseDto;
import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmetic;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmeticSet;
import com.likelion.tometa.domain.record.entity.DailyRecordCosmeticSetItem;
import com.likelion.tometa.domain.record.entity.DailyRecordImage;
import com.likelion.tometa.domain.record.entity.DailyRecordSelection;
import com.likelion.tometa.domain.record.enums.DailyRecordSelectionType;
import com.likelion.tometa.domain.record.event.DailyRecordCreatedEvent;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticSetRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticSetItemRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordImageRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordSelectionRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.mock;
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
    private DailyRecordCosmeticSetItemRepository dailyRecordCosmeticSetItemRepository;
    @Mock
    private DailyRecordSelectionRepository dailyRecordSelectionRepository;
    @Mock
    private DailyRecordImageRepository dailyRecordImageRepository;
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
    private CosmeticTagRepository cosmeticTagRepository;
    @Mock
    private DailyRecordImageAttachmentService imageAttachmentService;
    @Mock
    private RecordImageReadUrlService imageReadUrlService;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ApplicationEventPublisher eventPublisher;

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
                List.of(15L),
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
        assertEquals(List.of("morning", "morning"), savedSets.stream()
                .map(DailyRecordCosmeticSet::getUsagePeriod)
                .toList());

        ArgumentCaptor<Iterable<DailyRecordCosmeticSetItem>> setItemCaptor =
                iterableCaptor();
        verify(dailyRecordCosmeticSetItemRepository).saveAll(setItemCaptor.capture());
        List<DailyRecordCosmeticSetItem> savedSetItems = toList(
                setItemCaptor.getValue());
        assertEquals(List.of(5L, 9L, 5L, 12L), savedSetItems.stream()
                .map(item -> item.getUserCosmetic().getId())
                .toList());
        assertEquals(List.of(1, 2, 1, 2), savedSetItems.stream()
                .map(DailyRecordCosmeticSetItem::getSortOrder)
                .toList());

        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor = iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        List<DailyRecordCosmetic> savedCosmetics = toList(cosmeticCaptor.getValue());
        assertEquals(List.of(5L, 9L, 12L, 15L, 15L), savedCosmetics.stream()
                .map(snapshot -> snapshot.getUserCosmetic().getId())
                .toList());
        assertEquals(List.of(1, 2, 3, 4, 1), savedCosmetics.stream()
                .map(DailyRecordCosmetic::getSortOrder)
                .toList());
        assertEquals(
                List.of("morning", "morning", "morning", "morning", "night"),
                savedCosmetics.stream()
                        .map(DailyRecordCosmetic::getUsagePeriod)
                        .toList()
        );

        ArgumentCaptor<Iterable<DailyRecordSelection>> selectionCaptor = iterableCaptor();
        verify(dailyRecordSelectionRepository).saveAll(selectionCaptor.capture());
        List<DailyRecordSelection> savedSelections = toList(selectionCaptor.getValue());
        assertEquals(
                List.of(
                        DailyRecordSelectionType.SET,
                        DailyRecordSelectionType.SET,
                        DailyRecordSelectionType.COSMETIC,
                        DailyRecordSelectionType.COSMETIC,
                        DailyRecordSelectionType.COSMETIC
                ),
                savedSelections.stream()
                        .map(DailyRecordSelection::getSelectionType)
                        .toList()
        );
        assertEquals(List.of(3L, 8L, 12L, 15L, 15L), savedSelections.stream()
                .map(DailyRecordSelection::getSourceId)
                .toList());
        assertEquals(List.of(1, 2, 1, 2, 1), savedSelections.stream()
                .map(DailyRecordSelection::getSortOrder)
                .toList());
        assertEquals(
                List.of("morning", "morning", "morning", "morning", "night"),
                savedSelections.stream()
                        .map(DailyRecordSelection::getUsagePeriod)
                        .toList()
        );
        assertEquals(List.of(
                        "set-3",
                        "set-8",
                        "product-12",
                        "product-15",
                        "product-15"
                ),
                savedSelections.stream()
                        .map(DailyRecordSelection::getNameSnapshot)
                        .toList());

        ArgumentCaptor<DailyReport> reportCaptor = ArgumentCaptor.forClass(DailyReport.class);
        verify(dailyReportRepository).save(reportCaptor.capture());
        assertEquals("collecting", reportCaptor.getValue().getReportStatus());
        assertEquals(37L, reportCaptor.getValue().getDailyRecord().getId());
        verify(imageAttachmentService).attach(any(DailyRecord.class), eq(user), eq(List.of()));
        ArgumentCaptor<DailyRecordCreatedEvent> eventCaptor =
                ArgumentCaptor.forClass(DailyRecordCreatedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        assertEquals(1L, eventCaptor.getValue().userId());
        assertEquals(request.date(), eventCaptor.getValue().recordDate());
    }

    @Test
    void create_acceptsDirectCosmeticsForMorningAndNight() throws Exception {
        UserCosmetic cosmetic12 = userCosmetic(12L);
        UserCosmetic cosmetic22 = userCosmetic(22L);
        DailyRecordCreateRequestDto request = request(
                "good",
                List.of(12L),
                List.of(),
                List.of(22L),
                List.of(),
                null
        );

        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(
                Set.of(12L, 22L),
                user
        )).thenReturn(List.of(cosmetic12, cosmetic22));
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
        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor = iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        assertEquals(
                List.of("morning", "night"),
                toList(cosmeticCaptor.getValue()).stream()
                        .map(DailyRecordCosmetic::getUsagePeriod)
                        .toList()
        );
        ArgumentCaptor<Iterable<DailyRecordSelection>> selectionCaptor =
                iterableCaptor();
        verify(dailyRecordSelectionRepository).saveAll(selectionCaptor.capture());
        assertEquals(
                List.of("morning", "night"),
                toList(selectionCaptor.getValue()).stream()
                        .map(DailyRecordSelection::getUsagePeriod)
                        .toList()
        );
    }

    @Test
    void create_acceptsCosmeticSetsForMorningAndNight() throws Exception {
        UserCosmetic morningCosmetic = userCosmetic(12L);
        UserCosmetic nightCosmetic = userCosmetic(22L);
        CosmeticSet morningSet = cosmeticSet(3L, CosmeticSetUsageTime.MORNING);
        CosmeticSet nightSet = cosmeticSet(7L, CosmeticSetUsageTime.NIGHT);
        DailyRecordCreateRequestDto request = request(
                "good",
                List.of(),
                List.of(3L),
                List.of(),
                List.of(7L),
                null
        );

        when(cosmeticSetRepository.findAllByIdInAndUserOrderById(
                Set.of(3L, 7L),
                user
        )).thenReturn(List.of(morningSet, nightSet));
        when(cosmeticSetItemRepository
                .findAllActiveByCosmeticSetsOrderBySetAndCosmeticId(
                        List.of(morningSet, nightSet)
                )).thenReturn(List.of(
                        setItem(morningSet, morningCosmetic),
                        setItem(nightSet, nightCosmetic)
                ));
        when(cosmeticIngredientRepository
                .findAllByCosmeticProductIdsOrderByIngredientOrder(any()))
                .thenReturn(List.of());
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(dailyRecordRepository.saveAndFlush(any(DailyRecord.class)))
                .thenAnswer(invocation -> {
                    DailyRecord record = invocation.getArgument(0);
                    ReflectionTestUtils.setField(record, "id", 39L);
                    return record;
                });

        DailyRecordCreateResponseDto result = dailyRecordService.create(
                request,
                SESSION_TOKEN
        );

        assertEquals(39L, result.recordId());
        ArgumentCaptor<Iterable<DailyRecordCosmeticSet>> setCaptor = iterableCaptor();
        verify(dailyRecordCosmeticSetRepository).saveAll(setCaptor.capture());
        assertEquals(
                List.of("morning", "night"),
                toList(setCaptor.getValue()).stream()
                        .map(DailyRecordCosmeticSet::getUsagePeriod)
                        .toList()
        );
        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor = iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        assertEquals(
                List.of("morning", "night"),
                toList(cosmeticCaptor.getValue()).stream()
                        .map(DailyRecordCosmetic::getUsagePeriod)
                        .toList()
        );
        ArgumentCaptor<Iterable<DailyRecordSelection>> selectionCaptor =
                iterableCaptor();
        verify(dailyRecordSelectionRepository).saveAll(selectionCaptor.capture());
        assertEquals(
                List.of("morning", "night"),
                toList(selectionCaptor.getValue()).stream()
                        .map(DailyRecordSelection::getUsagePeriod)
                        .toList()
        );
    }

    @Test
    void create_rejectsNightSelectionWithoutMorningSelection() {
        DailyRecordCreateRequestDto request = request(
                "good",
                List.of(),
                List.of(),
                List.of(22L),
                List.of(),
                null
        );

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoRecordGraphWrites();
    }

    @Test
    void create_rejectsMorningSelectionWithoutNightSelection() {
        DailyRecordCreateRequestDto request = request(
                "good",
                List.of(12L),
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
        verifyNoRecordGraphWrites();
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
        verifyNoRecordGraphWrites();
    }

    @Test
    void create_rejectsFutureDateInKorea() {
        DailyRecordCreateRequestDto request = new DailyRecordCreateRequestDto(
                LocalDate.now(ZoneId.of("Asia/Seoul")).plusDays(1),
                "normal",
                List.of(12L),
                List.of(),
                List.of(12L),
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
                List.of(12L),
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
                List.of(12L),
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
    void create_returnsConflictForConcurrentDuplicateDateConstraint() {
        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(12L),
                List.of(),
                List.of(12L),
                List.of(),
                null
        );
        UserCosmetic cosmetic = userCosmetic(12L);
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(Set.of(12L), user))
                .thenReturn(List.of(cosmetic));
        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName())
                .thenReturn("uk_daily_records_user_date");
        when(dailyRecordRepository.saveAndFlush(any(DailyRecord.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate daily record",
                        constraintViolation
                ));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(RecordErrorCode.DAILY_RECORD_ALREADY_EXISTS, exception.getErrorCode());
    }

    @Test
    void create_propagatesUnrelatedDataIntegrityViolation() {
        DailyRecordCreateRequestDto request = request(
                "normal",
                List.of(12L),
                List.of(),
                List.of(12L),
                List.of(),
                null
        );
        UserCosmetic cosmetic = userCosmetic(12L);
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(Set.of(12L), user))
                .thenReturn(List.of(cosmetic));
        ConstraintViolationException constraintViolation =
                mock(ConstraintViolationException.class);
        when(constraintViolation.getConstraintName())
                .thenReturn("fk_daily_records_user");
        DataIntegrityViolationException dataAccessException =
                new DataIntegrityViolationException(
                        "unrelated constraint",
                        constraintViolation
                );
        when(dailyRecordRepository.saveAndFlush(any(DailyRecord.class)))
                .thenThrow(dataAccessException);

        DataIntegrityViolationException exception = assertThrows(
                DataIntegrityViolationException.class,
                () -> dailyRecordService.create(request, SESSION_TOKEN)
        );

        assertSame(dataAccessException, exception);
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
                List.of(7L),
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

    @Test
    void getByDate_returnsSnapshotsInSetThenCosmeticOrderAndSignsImages() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 12);
        DailyRecord dailyRecord = DailyRecord.builder()
                .user(user)
                .recordDate(date)
                .skinStatus("bad")
                .foodMemo("food")
                .memo("memo")
                .build();
        ReflectionTestUtils.setField(dailyRecord, "id", 37L);

        DailyRecordSelection secondSet = selection(
                dailyRecord,
                DailyRecordSelectionType.SET,
                8L,
                "set-8",
                List.of("panthenol"),
                2
        );
        DailyRecordSelection cosmetic = selection(
                dailyRecord,
                DailyRecordSelectionType.COSMETIC,
                11L,
                "product-11",
                List.of("ceramide"),
                1
        );
        DailyRecordSelection firstSet = selection(
                dailyRecord,
                DailyRecordSelectionType.SET,
                3L,
                "set-3",
                List.of("heartleaf"),
                1
        );
        DailyRecordImage image = DailyRecordImage.builder()
                .dailyRecord(dailyRecord)
                .objectKey("skin-images/1/image.jpg")
                .mimeType("image/jpeg")
                .fileSize(100L)
                .sortOrder(1)
                .build();

        when(dailyRecordRepository.findByUserAndRecordDate(user, date))
                .thenReturn(Optional.of(dailyRecord));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(dailyRecord))
                .thenReturn(List.of(secondSet, cosmetic, firstSet));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(dailyRecord))
                .thenReturn(List.of(image));
        when(imageReadUrlService.issueReadUrl("skin-images/1/image.jpg"))
                .thenReturn("https://signed.example/image.jpg");

        DailyRecordDetailResponseDto result = dailyRecordService.getByDate(
                date,
                SESSION_TOKEN
        );

        assertEquals(37L, result.recordId());
        assertEquals(List.of("SET", "SET", "COSMETIC"), result.morningSelections()
                .stream()
                .map(selection -> selection instanceof DailyRecordDetailResponseDto.SetSelection
                        ? ((DailyRecordDetailResponseDto.SetSelection) selection).selectionType()
                        : ((DailyRecordDetailResponseDto.CosmeticSelection) selection).selectionType())
                .toList());
        assertEquals(3L, ((DailyRecordDetailResponseDto.SetSelection)
                result.morningSelections().getFirst()).cosmeticSetId());
        assertEquals(11L, ((DailyRecordDetailResponseDto.CosmeticSelection)
                result.morningSelections().get(2)).userCosmeticId());
        assertTrue(result.nightSelections().isEmpty());
        assertEquals("skin-images/1/image.jpg", result.images().getFirst().imageKey());
        assertEquals("https://signed.example/image.jpg", result.images().getFirst().imageUrl());
    }

    @Test
    void getByDate_returnsRecordNotFoundForMissingUserDate() {
        LocalDate date = LocalDate.of(2026, 8, 12);
        when(dailyRecordRepository.findByUserAndRecordDate(user, date))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.getByDate(date, SESSION_TOKEN)
        );

        assertSame(RecordErrorCode.DAILY_RECORD_NOT_FOUND, exception.getErrorCode());
    }

    @Test
    void update_clearsMemoAndInvalidatesExistingReportWithoutReplacingSelections() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic cosmetic = userCosmetic(12L);
        DailyRecord record = dailyRecord(date, "normal", null, "old memo");
        DailyRecordSelection selection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                cosmetic.getId(),
                "product-12",
                List.of("hydrating"),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                cosmetic
        );
        DailyRecordCosmetic cosmeticSnapshot = cosmeticSnapshot(
                record,
                cosmetic,
                "morning",
                1
        );
        DailyRecordCosmetic nightCosmeticSnapshot = cosmeticSnapshot(
                record,
                cosmetic,
                "night",
                1
        );
        DailyReport report = DailyReport.builder().dailyRecord(record).build();
        report.updateNote("keep this note");
        ReflectionTestUtils.setField(report, "reportStatus", "completed");
        ReflectionTestUtils.setField(report, "aiSummary", "old summary");
        ReflectionTestUtils.setField(report, "aiAnalysis", "old analysis");
        ReflectionTestUtils.setField(report, "personalizedSolution", "old solution");
        LocalDateTime generatedAt = LocalDateTime.of(2026, 8, 13, 7, 0);
        ReflectionTestUtils.setField(report, "generatedAt", generatedAt);
        ReflectionTestUtils.setField(report, "regeneratedAt", LocalDateTime.now());

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(selection, nightSelection));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(cosmeticSnapshot, nightCosmeticSnapshot));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of());
        when(dailyReportRepository.findByDailyRecord(record))
                .thenReturn(Optional.of(report));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMemo(null);

        DailyRecordUpdateResponseDto result = dailyRecordService.update(
                date,
                request,
                SESSION_TOKEN
        );

        assertEquals(37L, result.recordId());
        assertNull(record.getMemo());
        assertEquals(1L, report.getGenerationVersion());
        assertEquals("collecting", report.getReportStatus());
        assertNull(report.getAiSummary());
        assertNull(report.getAiAnalysis());
        assertNull(report.getPersonalizedSolution());
        assertEquals(generatedAt, report.getGeneratedAt());
        assertNull(report.getRegeneratedAt());
        assertEquals("keep this note", report.getNote());
        verify(dailyRecordSelectionRepository, never()).deleteAll(any());
        verify(imageAttachmentService, never()).replace(any(), any(), any());
    }

    @Test
    void update_noOpDoesNotInvalidateReport() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic cosmetic = userCosmetic(12L);
        DailyRecord record = dailyRecord(date, "normal", null, "same memo");
        DailyRecordSelection selection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                cosmetic.getId(),
                "product-12",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                cosmetic
        );

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(selection, nightSelection));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(
                        cosmeticSnapshot(record, cosmetic, "morning", 1),
                        cosmeticSnapshot(record, cosmetic, "night", 1)
                ));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of());

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMemo("  same memo  ");
        request.setMorningCosmeticIds(List.of(12L));

        dailyRecordService.update(date, request, SESSION_TOKEN);

        verify(dailyReportRepository, never()).findByDailyRecord(any());
        verify(dailyReportRepository, never()).save(any());
        verify(dailyRecordSelectionRepository, never()).deleteAll(any());
    }

    @Test
    void update_acceptsEmptyMorningCosmeticsWhenMorningSetRemains() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic nightCosmetic = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordSelection morningSet = selection(
                record,
                DailyRecordSelectionType.SET,
                3L,
                "morning-set",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                nightCosmetic
        );
        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(morningSet, nightSelection));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMorningCosmeticIds(List.of());

        DailyRecordUpdateResponseDto result = dailyRecordService.update(
                date,
                request,
                SESSION_TOKEN
        );

        assertEquals(37L, result.recordId());
        verify(dailyRecordSelectionRepository, never()).deleteAll(any());
    }

    @Test
    void update_rejectsRemovingAllMorningSelections() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic morningCosmetic = userCosmetic(12L);
        UserCosmetic nightCosmetic = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(
                        selection(
                                record,
                                DailyRecordSelectionType.COSMETIC,
                                morningCosmetic.getId(),
                                "product-12",
                                List.of(),
                                1
                        ),
                        nightCosmeticSelection(record, nightCosmetic)
                ));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMorningCosmeticIds(List.of());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.update(date, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoRecordGraphWrites();
    }

    @Test
    void update_rejectsRemovingAllNightSelections() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic morningCosmetic = userCosmetic(12L);
        UserCosmetic nightCosmetic = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(
                        selection(
                                record,
                                DailyRecordSelectionType.COSMETIC,
                                morningCosmetic.getId(),
                                "product-12",
                                List.of(),
                                1
                        ),
                        nightCosmeticSelection(record, nightCosmetic)
                ));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setNightCosmeticIds(List.of());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.update(date, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoRecordGraphWrites();
    }

    @Test
    void update_rejectsRemovingRequiredMemoForBadSkinStatus() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic cosmetic = userCosmetic(12L);
        DailyRecord record = dailyRecord(date, "bad", null, "required memo");
        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(
                        selection(
                                record,
                                DailyRecordSelectionType.COSMETIC,
                                12L,
                                "product-12",
                                List.of(),
                                1
                        ),
                        nightCosmeticSelection(record, cosmetic)
                ));
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(cosmeticSnapshot(record, cosmetic, "morning", 1)));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMemo(null);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.update(date, request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
    }

    @Test
    void update_replacesChangedImageListInRequestedOrderAndInvalidatesReport() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic cosmetic = userCosmetic(12L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordSelection selection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                12L,
                "product-12",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                cosmetic
        );
        DailyRecordImage existingImage = DailyRecordImage.builder()
                .dailyRecord(record)
                .objectKey("skin-images/1/old.jpg")
                .mimeType("image/jpeg")
                .fileSize(100L)
                .sortOrder(1)
                .build();
        DailyReport report = DailyReport.builder().dailyRecord(record).build();

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(selection, nightSelection));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(
                        cosmeticSnapshot(record, cosmetic, "morning", 1),
                        cosmeticSnapshot(record, cosmetic, "night", 1)
                ));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of(existingImage));
        when(dailyReportRepository.findByDailyRecord(record))
                .thenReturn(Optional.of(report));

        List<String> requestedKeys = List.of(
                "skin-images/1/second.jpg",
                "skin-images/1/first.jpg"
        );
        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setImageKeys(requestedKeys);

        dailyRecordService.update(date, request, SESSION_TOKEN);

        verify(imageAttachmentService).replace(record, user, requestedKeys);
        assertEquals(1L, report.getGenerationVersion());
        assertEquals("collecting", report.getReportStatus());
    }

    @Test
    void update_withEmptyImageListClearsAllImagesAndInvalidatesReport() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic cosmetic = userCosmetic(12L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordSelection selection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                12L,
                "product-12",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                cosmetic
        );
        DailyRecordImage existingImage = DailyRecordImage.builder()
                .dailyRecord(record)
                .objectKey("skin-images/1/old.jpg")
                .mimeType("image/jpeg")
                .fileSize(100L)
                .sortOrder(1)
                .build();
        DailyReport report = DailyReport.builder().dailyRecord(record).build();

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(selection, nightSelection));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(
                        cosmeticSnapshot(record, cosmetic, "morning", 1),
                        cosmeticSnapshot(record, cosmetic, "night", 1)
                ));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of(existingImage));
        when(dailyReportRepository.findByDailyRecord(record))
                .thenReturn(Optional.of(report));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setImageKeys(List.of());

        dailyRecordService.update(date, request, SESSION_TOKEN);

        verify(imageAttachmentService).replace(record, user, List.of());
        assertEquals(1L, report.getGenerationVersion());
        assertEquals("collecting", report.getReportStatus());
    }

    @Test
    void update_replacesProvidedDirectSelectionAndRebuildsSnapshots() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic oldCosmetic = userCosmetic(12L);
        UserCosmetic newCosmetic = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordSelection oldSelection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                oldCosmetic.getId(),
                "product-12",
                List.of("old"),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                oldCosmetic
        );
        DailyRecordCosmetic oldSnapshot = cosmeticSnapshot(
                record,
                oldCosmetic,
                "morning",
                1
        );
        DailyRecordCosmetic nightSnapshot = cosmeticSnapshot(
                record,
                oldCosmetic,
                "night",
                1
        );
        DailyReport report = DailyReport.builder().dailyRecord(record).build();

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(oldSelection, nightSelection));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(oldSnapshot, nightSnapshot));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of());
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(
                Set.of(22L),
                user
        )).thenReturn(List.of(newCosmetic));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(dailyReportRepository.findByDailyRecord(record))
                .thenReturn(Optional.of(report));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMorningCosmeticIds(List.of(22L));

        dailyRecordService.update(date, request, SESSION_TOKEN);

        verify(dailyRecordSelectionRepository).deleteAll(List.of(
                oldSelection,
                nightSelection
        ));
        verify(dailyRecordCosmeticRepository).deleteAll(List.of(
                oldSnapshot,
                nightSnapshot
        ));
        ArgumentCaptor<Iterable<DailyRecordSelection>> selectionCaptor =
                iterableCaptor();
        verify(dailyRecordSelectionRepository).saveAll(selectionCaptor.capture());
        List<DailyRecordSelection> savedSelections = toList(
                selectionCaptor.getValue()
        );
        assertEquals(List.of(22L, 12L), savedSelections.stream()
                .map(DailyRecordSelection::getSourceId)
                .toList());
        assertEquals(List.of("morning", "night"), savedSelections.stream()
                .map(DailyRecordSelection::getUsagePeriod)
                .toList());
        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor =
                iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        List<DailyRecordCosmetic> savedCosmetics = toList(cosmeticCaptor.getValue());
        assertEquals(List.of(22L, 12L), savedCosmetics.stream()
                .map(snapshot -> snapshot.getUserCosmetic().getId())
                .toList());
        assertEquals(List.of("morning", "night"), savedCosmetics.stream()
                .map(DailyRecordCosmetic::getUsagePeriod)
                .toList());
        assertEquals(1L, report.getGenerationVersion());
    }

    @Test
    void update_preservesOmittedSetAndItsMemberSnapshot() throws Exception {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic firstSetMember = userCosmetic(5L);
        UserCosmetic secondSetMember = userCosmetic(6L);
        UserCosmetic oldDirect = userCosmetic(12L);
        UserCosmetic newDirect = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordCosmeticSet setSnapshot = DailyRecordCosmeticSet.builder()
                .dailyRecord(record)
                .sourceCosmeticSetId(3L)
                .setNameSnapshot("historic set name")
                .setUsageTimeSnapshot("morning")
                .usagePeriod("morning")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(setSnapshot, "id", 103L);
        DailyRecordCosmeticSetItem setItemSnapshot =
                DailyRecordCosmeticSetItem.builder()
                        .dailyRecordCosmeticSet(setSnapshot)
                        .userCosmetic(firstSetMember)
                        .sortOrder(1)
                        .build();
        DailyRecordCosmeticSetItem secondSetItemSnapshot =
                DailyRecordCosmeticSetItem.builder()
                        .dailyRecordCosmeticSet(setSnapshot)
                        .userCosmetic(secondSetMember)
                        .sortOrder(2)
                        .build();
        DailyRecordSelection setSelection = selection(
                record,
                DailyRecordSelectionType.SET,
                3L,
                "historic set name",
                List.of("historic tag"),
                1
        );
        DailyRecordSelection directSelection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                12L,
                "product-12",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                oldDirect
        );
        DailyRecordCosmetic memberSnapshot = cosmeticSnapshot(
                record,
                firstSetMember,
                "morning",
                1
        );
        DailyRecordCosmetic secondMemberSnapshot = cosmeticSnapshot(
                record,
                secondSetMember,
                "morning",
                2
        );
        DailyRecordCosmetic directSnapshot = cosmeticSnapshot(
                record,
                oldDirect,
                "morning",
                3
        );
        DailyRecordCosmetic nightSnapshot = cosmeticSnapshot(
                record,
                oldDirect,
                "night",
                1
        );
        DailyReport report = DailyReport.builder().dailyRecord(record).build();

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(
                        setSelection,
                        directSelection,
                        nightSelection
                ));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(setSnapshot));
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of(setItemSnapshot, secondSetItemSnapshot));
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(
                        memberSnapshot,
                        secondMemberSnapshot,
                        directSnapshot,
                        nightSnapshot
                ));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of());
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(
                Set.of(22L),
                user
        )).thenReturn(List.of(newDirect));
        when(objectMapper.writeValueAsString(any())).thenReturn("[]");
        when(dailyReportRepository.findByDailyRecord(record))
                .thenReturn(Optional.of(report));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMorningCosmeticIds(List.of(22L));

        dailyRecordService.update(date, request, SESSION_TOKEN);

        ArgumentCaptor<Iterable<DailyRecordCosmeticSet>> setCaptor = iterableCaptor();
        verify(dailyRecordCosmeticSetRepository).saveAll(setCaptor.capture());
        DailyRecordCosmeticSet preservedSet = toList(setCaptor.getValue()).getFirst();
        assertEquals(3L, preservedSet.getSourceCosmeticSetId());
        assertEquals("historic set name", preservedSet.getSetNameSnapshot());
        assertEquals("morning", preservedSet.getUsagePeriod());

        ArgumentCaptor<Iterable<DailyRecordCosmeticSetItem>> itemCaptor =
                iterableCaptor();
        verify(dailyRecordCosmeticSetItemRepository).saveAll(itemCaptor.capture());
        assertEquals(List.of(5L, 6L), toList(itemCaptor.getValue()).stream()
                .map(item -> item.getUserCosmetic().getId())
                .toList());

        ArgumentCaptor<Iterable<DailyRecordCosmetic>> cosmeticCaptor =
                iterableCaptor();
        verify(dailyRecordCosmeticRepository).saveAll(cosmeticCaptor.capture());
        List<DailyRecordCosmetic> rebuilt = toList(cosmeticCaptor.getValue());
        assertEquals(List.of(5L, 6L, 22L, 12L), rebuilt.stream()
                .map(snapshot -> snapshot.getUserCosmetic().getId())
                .toList());
        assertEquals(
                List.of("morning", "morning", "morning", "night"),
                rebuilt.stream()
                        .map(DailyRecordCosmetic::getUsagePeriod)
                        .toList()
        );
        assertEquals(memberSnapshot.getProductNameSnapshot(),
                rebuilt.getFirst().getProductNameSnapshot());
        assertEquals(secondMemberSnapshot.getProductNameSnapshot(),
                rebuilt.get(1).getProductNameSnapshot());

        ArgumentCaptor<Iterable<DailyRecordSelection>> selectionCaptor =
                iterableCaptor();
        verify(dailyRecordSelectionRepository).saveAll(selectionCaptor.capture());
        assertEquals(
                List.of("morning", "morning", "night"),
                toList(selectionCaptor.getValue()).stream()
                        .map(DailyRecordSelection::getUsagePeriod)
                        .toList()
        );
    }

    @Test
    void update_rejectsOmittedSetWhenMemberSnapshotIsMissing() {
        LocalDate date = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        UserCosmetic oldDirect = userCosmetic(12L);
        UserCosmetic newDirect = userCosmetic(22L);
        DailyRecord record = dailyRecord(date, "normal", null, null);
        DailyRecordCosmeticSet setSnapshot = DailyRecordCosmeticSet.builder()
                .dailyRecord(record)
                .sourceCosmeticSetId(3L)
                .setNameSnapshot("historic set name")
                .setUsageTimeSnapshot("morning")
                .usagePeriod("morning")
                .sortOrder(1)
                .build();
        ReflectionTestUtils.setField(setSnapshot, "id", 103L);
        DailyRecordSelection setSelection = selection(
                record,
                DailyRecordSelectionType.SET,
                3L,
                "historic set name",
                List.of("historic tag"),
                1
        );
        DailyRecordSelection directSelection = selection(
                record,
                DailyRecordSelectionType.COSMETIC,
                12L,
                "product-12",
                List.of(),
                1
        );
        DailyRecordSelection nightSelection = nightCosmeticSelection(
                record,
                oldDirect
        );
        DailyRecordCosmetic directSnapshot = cosmeticSnapshot(
                record,
                oldDirect,
                "morning",
                1
        );
        DailyRecordCosmetic nightSnapshot = cosmeticSnapshot(
                record,
                oldDirect,
                "night",
                1
        );

        when(dailyRecordRepository.findByUserAndRecordDateForUpdate(user, date))
                .thenReturn(Optional.of(record));
        when(dailyRecordSelectionRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(
                        setSelection,
                        directSelection,
                        nightSelection
                ));
        when(dailyRecordCosmeticSetRepository.findAllByDailyRecord(record))
                .thenReturn(List.of(setSnapshot));
        when(dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(record))
                .thenReturn(List.of());
        when(dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(record))
                .thenReturn(List.of(directSnapshot, nightSnapshot));
        when(dailyRecordImageRepository.findAllByDailyRecordOrderBySortOrderAsc(record))
                .thenReturn(List.of());
        when(userCosmeticRepository.findAllActiveByIdsAndUserForRecord(
                Set.of(22L),
                user
        )).thenReturn(List.of(newDirect));

        DailyRecordUpdateRequestDto request = new DailyRecordUpdateRequestDto();
        request.setMorningCosmeticIds(List.of(22L));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> dailyRecordService.update(date, request, SESSION_TOKEN)
        );

        assertSame(
                RecordErrorCode.DAILY_RECORD_SNAPSHOT_INCOMPLETE,
                exception.getErrorCode()
        );
        verify(dailyRecordCosmeticSetItemRepository, never()).deleteAll(any());
        verify(dailyRecordCosmeticSetRepository, never()).deleteAll(any());
        verify(dailyRecordSelectionRepository, never()).deleteAll(any());
        verify(dailyRecordCosmeticRepository, never()).deleteAll(any());
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
                LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1),
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

    private DailyRecord dailyRecord(
            LocalDate date,
            String skinStatus,
            String foodMemo,
            String memo
    ) {
        DailyRecord record = DailyRecord.builder()
                .user(user)
                .recordDate(date)
                .skinStatus(skinStatus)
                .foodMemo(foodMemo)
                .memo(memo)
                .build();
        ReflectionTestUtils.setField(record, "id", 37L);
        return record;
    }

    private DailyRecordCosmetic cosmeticSnapshot(
            DailyRecord record,
            UserCosmetic cosmetic,
            String usagePeriod,
            int sortOrder
    ) {
        CosmeticProduct product = cosmetic.getCosmeticProduct();
        return DailyRecordCosmetic.builder()
                .dailyRecord(record)
                .userCosmetic(cosmetic)
                .usagePeriod(usagePeriod)
                .productNameSnapshot(product.getProductName())
                .brandNameSnapshot(product.getBrandName())
                .productTypeSnapshot(product.getProductType())
                .customNameSnapshot(cosmetic.getCustomName())
                .ingredientsSnapshot("[]")
                .sortOrder(sortOrder)
                .build();
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

    private DailyRecordSelection selection(
            DailyRecord dailyRecord,
            DailyRecordSelectionType type,
            Long sourceId,
            String name,
            List<String> tags,
            int sortOrder
    ) {
        return DailyRecordSelection.builder()
                .dailyRecord(dailyRecord)
                .usagePeriod("morning")
                .selectionType(type)
                .sourceId(sourceId)
                .nameSnapshot(name)
                .tagsSnapshot(tags)
                .sortOrder(sortOrder)
                .build();
    }

    private DailyRecordSelection nightCosmeticSelection(
            DailyRecord dailyRecord,
            UserCosmetic cosmetic
    ) {
        return DailyRecordSelection.builder()
                .dailyRecord(dailyRecord)
                .usagePeriod("night")
                .selectionType(DailyRecordSelectionType.COSMETIC)
                .sourceId(cosmetic.getId())
                .nameSnapshot("product-" + cosmetic.getId())
                .tagsSnapshot(List.of())
                .sortOrder(1)
                .build();
    }

    private void verifyNoRecordGraphWrites() {
        verify(dailyRecordRepository, never()).saveAndFlush(any());
        verify(dailyRecordCosmeticRepository, never()).saveAll(any());
        verify(dailyRecordCosmeticSetRepository, never()).saveAll(any());
        verify(dailyRecordCosmeticSetItemRepository, never()).saveAll(any());
        verify(dailyRecordSelectionRepository, never()).saveAll(any());
        verify(dailyRecordCosmeticRepository, never()).deleteAll(any());
        verify(dailyRecordCosmeticSetRepository, never()).deleteAll(any());
        verify(dailyRecordCosmeticSetItemRepository, never()).deleteAll(any());
        verify(dailyRecordSelectionRepository, never()).deleteAll(any());
        verify(imageAttachmentService, never()).attach(any(), any(), any());
        verify(imageAttachmentService, never()).replace(any(), any(), any());
        verify(dailyReportRepository, never()).save(any());
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private <T> ArgumentCaptor<Iterable<T>> iterableCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(Iterable.class);
    }

    private <T> List<T> toList(Iterable<T> values) {
        return StreamSupport.stream(values.spliterator(), false).toList();
    }
}
