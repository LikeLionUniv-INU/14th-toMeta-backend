package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticTag;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticIngredientRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticTagRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.cosmetic.service.CosmeticSetTagSelector;
import com.likelion.tometa.domain.record.code.RecordErrorCode;
import com.likelion.tometa.domain.record.constant.DailyRecordPolicy;
import com.likelion.tometa.domain.record.constant.RecordImagePolicy;
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
import com.likelion.tometa.domain.record.enums.RecordUsagePeriod;
import com.likelion.tometa.domain.record.enums.SkinStatus;
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
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class DailyRecordService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final DailyRecordRepository dailyRecordRepository;
    private final DailyRecordCosmeticRepository dailyRecordCosmeticRepository;
    private final DailyRecordCosmeticSetRepository dailyRecordCosmeticSetRepository;
    private final DailyRecordCosmeticSetItemRepository dailyRecordCosmeticSetItemRepository;
    private final DailyRecordSelectionRepository dailyRecordSelectionRepository;
    private final DailyRecordImageRepository dailyRecordImageRepository;
    private final DailyReportRepository dailyReportRepository;
    private final UserCosmeticRepository userCosmeticRepository;
    private final CosmeticSetRepository cosmeticSetRepository;
    private final CosmeticSetItemRepository cosmeticSetItemRepository;
    private final CosmeticIngredientRepository cosmeticIngredientRepository;
    private final CosmeticTagRepository cosmeticTagRepository;
    private final DailyRecordImageAttachmentService imageAttachmentService;
    private final RecordImageReadUrlService imageReadUrlService;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public DailyRecordCreateResponseDto create(
            DailyRecordCreateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        SkinStatus skinStatus = validateRequest(request);

        if (dailyRecordRepository.existsByUserAndRecordDate(user, request.date())) {
            throw new GeneralException(RecordErrorCode.DAILY_RECORD_ALREADY_EXISTS);
        }

        SelectionResources resources = loadSelectionResources(
                request.morningCosmeticIds(),
                request.morningCosmeticSetIds(),
                request.nightCosmeticIds(),
                request.nightCosmeticSetIds(),
                user
        );
        PeriodSelection morning = resolvePeriod(
                RecordUsagePeriod.MORNING,
                request.morningCosmeticSetIds(),
                request.morningCosmeticIds(),
                resources
        );
        PeriodSelection night = resolvePeriod(
                RecordUsagePeriod.NIGHT,
                request.nightCosmeticSetIds(),
                request.nightCosmeticIds(),
                resources
        );

        DailyRecord dailyRecord = DailyRecord.builder()
                .user(user)
                .recordDate(request.date())
                .skinStatus(skinStatus.getValue())
                .foodMemo(request.foodMemo())
                .memo(request.memo())
                .build();

        try {
            dailyRecordRepository.saveAndFlush(dailyRecord);
        } catch (DataIntegrityViolationException e) {
            if (isDuplicateRecordConstraint(e)) {
                throw new GeneralException(RecordErrorCode.DAILY_RECORD_ALREADY_EXISTS);
            }
            throw e;
        }

        saveSelectedSets(dailyRecord, morning, night, resources);
        saveUsedCosmetics(dailyRecord, morning, night);
        saveSelectionSnapshots(dailyRecord, morning, night, resources);
        imageAttachmentService.attach(dailyRecord, user, request.imageKeys());
        dailyReportRepository.save(DailyReport.builder()
                .dailyRecord(dailyRecord)
                .build());
        eventPublisher.publishEvent(new DailyRecordCreatedEvent(
                user.getId(),
                dailyRecord.getRecordDate()
        ));

        return new DailyRecordCreateResponseDto(dailyRecord.getId(), request.date());
    }

    @Transactional
    public DailyRecordDetailResponseDto getByDate(
            LocalDate date,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        DailyRecord dailyRecord = dailyRecordRepository
                .findByUserAndRecordDate(user, date)
                .orElseThrow(() -> new GeneralException(
                        RecordErrorCode.DAILY_RECORD_NOT_FOUND));

        List<DailyRecordSelection> selections = dailyRecordSelectionRepository
                .findAllByDailyRecord(dailyRecord);
        List<DailyRecordDetailResponseDto.Image> images = dailyRecordImageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(dailyRecord)
                .stream()
                .map(this::toImageResponse)
                .toList();

        return new DailyRecordDetailResponseDto(
                dailyRecord.getId(),
                dailyRecord.getRecordDate(),
                dailyRecord.getSkinStatus(),
                toSelectionResponses(selections, RecordUsagePeriod.MORNING),
                toSelectionResponses(selections, RecordUsagePeriod.NIGHT),
                dailyRecord.getFoodMemo(),
                images,
                dailyRecord.getMemo()
        );
    }

    @Transactional
    public DailyRecordUpdateResponseDto update(
            LocalDate date,
            DailyRecordUpdateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        if (request == null
                || date == null
                || date.isAfter(LocalDate.now(KOREA_ZONE))) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        DailyRecord dailyRecord = dailyRecordRepository
                .findByUserAndRecordDateForUpdate(user, date)
                .orElseThrow(() -> new GeneralException(
                        RecordErrorCode.DAILY_RECORD_NOT_FOUND));
        ExistingRecordState existing = loadExistingRecordState(dailyRecord);
        UpdateValues values = mergeAndValidateUpdate(dailyRecord, request, existing);

        boolean scalarChanged = !dailyRecord.getSkinStatus().equals(values.skinStatus())
                || !Objects.equals(dailyRecord.getFoodMemo(), values.foodMemo())
                || !Objects.equals(dailyRecord.getMemo(), values.memo());
        boolean selectionChanged = values.morningCosmeticIdsChanged()
                || values.morningCosmeticSetIdsChanged()
                || values.nightCosmeticIdsChanged()
                || values.nightCosmeticSetIdsChanged();
        boolean imagesChanged = request.hasImageKeys()
                && !existing.imageKeys().equals(values.imageKeys());

        if (!scalarChanged && !selectionChanged && !imagesChanged) {
            return new DailyRecordUpdateResponseDto(dailyRecord.getId(), date);
        }

        if (selectionChanged) {
            replaceSelectionGraph(dailyRecord, user, existing, values);
        }
        if (scalarChanged) {
            dailyRecord.update(values.skinStatus(), values.foodMemo(), values.memo());
        }
        if (imagesChanged) {
            imageAttachmentService.replace(dailyRecord, user, values.imageKeys());
        }

        DailyReport dailyReport = dailyReportRepository.findByDailyRecord(dailyRecord)
                .orElseGet(() -> dailyReportRepository.save(DailyReport.builder()
                        .dailyRecord(dailyRecord)
                        .build()));
        dailyReport.invalidateForRegeneration();

        return new DailyRecordUpdateResponseDto(dailyRecord.getId(), date);
    }

    private ExistingRecordState loadExistingRecordState(DailyRecord dailyRecord) {
        List<DailyRecordSelection> selections = dailyRecordSelectionRepository
                .findAllByDailyRecord(dailyRecord);
        List<DailyRecordCosmeticSet> sets = dailyRecordCosmeticSetRepository
                .findAllByDailyRecord(dailyRecord);
        List<DailyRecordCosmeticSetItem> setItems = dailyRecordCosmeticSetItemRepository
                .findAllByDailyRecordCosmeticSet_DailyRecord(dailyRecord);
        List<DailyRecordCosmetic> cosmetics = dailyRecordCosmeticRepository
                .findAllByDailyRecordOrderByUsagePeriodAscSortOrderAsc(dailyRecord);
        List<DailyRecordImage> images = dailyRecordImageRepository
                .findAllByDailyRecordOrderBySortOrderAsc(dailyRecord);
        return new ExistingRecordState(
                selections,
                sets,
                setItems,
                cosmetics,
                images.stream().map(DailyRecordImage::getObjectKey).toList()
        );
    }

    private UpdateValues mergeAndValidateUpdate(
            DailyRecord dailyRecord,
            DailyRecordUpdateRequestDto request,
            ExistingRecordState existing
    ) {
        if ((request.hasSkinStatus() && request.skinStatus() == null)
                || (request.hasMorningCosmeticIds() && request.morningCosmeticIds() == null)
                || (request.hasMorningCosmeticSetIds() && request.morningCosmeticSetIds() == null)
                || (request.hasNightCosmeticIds() && request.nightCosmeticIds() == null)
                || (request.hasNightCosmeticSetIds() && request.nightCosmeticSetIds() == null)
                || (request.hasImageKeys() && request.imageKeys() == null)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        List<Long> existingMorningCosmetics = selectionIds(
                existing.selections(),
                RecordUsagePeriod.MORNING,
                DailyRecordSelectionType.COSMETIC
        );
        List<Long> existingMorningSets = selectionIds(
                existing.selections(),
                RecordUsagePeriod.MORNING,
                DailyRecordSelectionType.SET
        );
        List<Long> existingNightCosmetics = selectionIds(
                existing.selections(),
                RecordUsagePeriod.NIGHT,
                DailyRecordSelectionType.COSMETIC
        );
        List<Long> existingNightSets = selectionIds(
                existing.selections(),
                RecordUsagePeriod.NIGHT,
                DailyRecordSelectionType.SET
        );

        List<Long> morningCosmetics = request.hasMorningCosmeticIds()
                ? validatedIds(request.morningCosmeticIds())
                : existingMorningCosmetics;
        List<Long> morningSets = request.hasMorningCosmeticSetIds()
                ? validatedIds(request.morningCosmeticSetIds())
                : existingMorningSets;
        List<Long> nightCosmetics = request.hasNightCosmeticIds()
                ? validatedIds(request.nightCosmeticIds())
                : existingNightCosmetics;
        List<Long> nightSets = request.hasNightCosmeticSetIds()
                ? validatedIds(request.nightCosmeticSetIds())
                : existingNightSets;

        validateRequiredPeriodSelections(
                morningCosmetics,
                morningSets,
                nightCosmetics,
                nightSets
        );

        String skinStatusValue = request.hasSkinStatus()
                ? request.skinStatus()
                : dailyRecord.getSkinStatus();
        SkinStatus skinStatus = SkinStatus.from(skinStatusValue)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.BAD_REQUEST));
        String foodMemo = request.hasFoodMemo()
                ? request.foodMemo()
                : dailyRecord.getFoodMemo();
        String memo = request.hasMemo() ? request.memo() : dailyRecord.getMemo();
        if ((foodMemo != null && foodMemo.length() > DailyRecordPolicy.MAX_MEMO_LENGTH)
                || (memo != null && memo.length() > DailyRecordPolicy.MAX_MEMO_LENGTH)
                || (skinStatus.requiresMemo() && (memo == null || memo.isBlank()))) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        List<String> imageKeys = request.hasImageKeys()
                ? validatedImageKeys(request.imageKeys())
                : existing.imageKeys();

        return new UpdateValues(
                skinStatus.getValue(),
                morningCosmetics,
                morningSets,
                nightCosmetics,
                nightSets,
                foodMemo,
                imageKeys,
                memo,
                request.hasMorningCosmeticIds()
                        && !morningCosmetics.equals(
                                existingMorningCosmetics.stream().sorted().toList()),
                request.hasMorningCosmeticSetIds()
                        && !morningSets.equals(existingMorningSets.stream().sorted().toList()),
                request.hasNightCosmeticIds()
                        && !nightCosmetics.equals(
                                existingNightCosmetics.stream().sorted().toList()),
                request.hasNightCosmeticSetIds()
                        && !nightSets.equals(existingNightSets.stream().sorted().toList())
        );
    }

    private List<Long> validatedIds(List<Long> ids) {
        validateNoDuplicates(ids);
        return ids.stream().sorted().toList();
    }

    private List<String> validatedImageKeys(List<String> imageKeys) {
        if (imageKeys.size() > RecordImagePolicy.MAX_IMAGE_COUNT
                || imageKeys.stream().anyMatch(key -> key == null || key.isBlank())
                || new HashSet<>(imageKeys).size() != imageKeys.size()) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
        return List.copyOf(imageKeys);
    }

    private List<Long> selectionIds(
            List<DailyRecordSelection> selections,
            RecordUsagePeriod period,
            DailyRecordSelectionType type
    ) {
        return selections.stream()
                .filter(selection -> selection.getUsagePeriod().equals(period.getValue()))
                .filter(selection -> selection.getSelectionType() == type)
                .sorted(Comparator.comparingInt(DailyRecordSelection::getSortOrder))
                .map(DailyRecordSelection::getSourceId)
                .toList();
    }

    private void replaceSelectionGraph(
            DailyRecord dailyRecord,
            User user,
            ExistingRecordState existing,
            UpdateValues values
    ) {
        SelectionResources changedResources = loadSelectionResources(
                values.morningCosmeticIdsChanged()
                        ? values.morningCosmeticIds() : List.of(),
                values.morningCosmeticSetIdsChanged()
                        ? values.morningCosmeticSetIds() : List.of(),
                values.nightCosmeticIdsChanged()
                        ? values.nightCosmeticIds() : List.of(),
                values.nightCosmeticSetIdsChanged()
                        ? values.nightCosmeticSetIds() : List.of(),
                user
        );

        Map<Long, List<String>> newDirectTags = loadMainIngredientNames(
                changedResources.cosmeticById().values().stream()
                        .map(UserCosmetic::getCosmeticProduct)
                        .map(CosmeticProduct::getId)
                        .collect(Collectors.toSet())
        );
        Map<Long, List<CosmeticTag>> newSetTags = loadSetTags(changedResources);

        List<SetChoice> morningSets = values.morningCosmeticSetIdsChanged()
                ? newSetChoices(
                        RecordUsagePeriod.MORNING,
                        values.morningCosmeticSetIds(),
                        changedResources,
                        newSetTags
                )
                : existingSetChoices(RecordUsagePeriod.MORNING, existing);
        List<SetChoice> nightSets = values.nightCosmeticSetIdsChanged()
                ? newSetChoices(
                        RecordUsagePeriod.NIGHT,
                        values.nightCosmeticSetIds(),
                        changedResources,
                        newSetTags
                )
                : existingSetChoices(RecordUsagePeriod.NIGHT, existing);
        List<DirectChoice> morningDirect = values.morningCosmeticIdsChanged()
                ? newDirectChoices(
                        RecordUsagePeriod.MORNING,
                        values.morningCosmeticIds(),
                        changedResources,
                        newDirectTags
                )
                : existingDirectChoices(RecordUsagePeriod.MORNING, existing);
        List<DirectChoice> nightDirect = values.nightCosmeticIdsChanged()
                ? newDirectChoices(
                        RecordUsagePeriod.NIGHT,
                        values.nightCosmeticIds(),
                        changedResources,
                        newDirectTags
                )
                : existingDirectChoices(RecordUsagePeriod.NIGHT, existing);

        deleteExistingSelectionGraph(existing);
        saveSetChoices(dailyRecord, morningSets, nightSets);
        saveChoiceSelections(
                dailyRecord,
                morningSets,
                nightSets,
                morningDirect,
                nightDirect
        );
        saveChoiceCosmetics(
                dailyRecord,
                existing.cosmetics(),
                morningSets,
                nightSets,
                morningDirect,
                nightDirect
        );
    }

    private List<SetChoice> existingSetChoices(
            RecordUsagePeriod period,
            ExistingRecordState existing
    ) {
        Map<String, DailyRecordSelection> selectionsByKey = existing.selections().stream()
                .collect(Collectors.toMap(
                        selection -> selectionKey(
                                selection.getUsagePeriod(),
                                selection.getSelectionType(),
                                selection.getSourceId()
                        ),
                        Function.identity()
                ));
        Map<Long, List<DailyRecordCosmeticSetItem>> itemsBySetId = existing.setItems()
                .stream()
                .collect(Collectors.groupingBy(item ->
                        item.getDailyRecordCosmeticSet().getId()));
        Set<String> cosmeticSnapshotKeys = existing.cosmetics().stream()
                .map(cosmetic -> cosmeticKey(
                        cosmetic.getUsagePeriod(),
                        cosmetic.getUserCosmetic().getId()
                ))
                .collect(Collectors.toSet());

        return existing.sets().stream()
                .filter(set -> set.getUsagePeriod().equals(period.getValue()))
                .sorted(Comparator.comparingInt(DailyRecordCosmeticSet::getSortOrder))
                .map(set -> {
                    DailyRecordSelection selection = selectionsByKey.get(selectionKey(
                            period.getValue(),
                            DailyRecordSelectionType.SET,
                            set.getSourceCosmeticSetId()
                    ));
                    if (selection == null) {
                        throw new GeneralException(
                                RecordErrorCode.DAILY_RECORD_SNAPSHOT_INCOMPLETE);
                    }
                    List<DailyRecordCosmeticSetItem> itemSnapshots =
                            itemsBySetId.get(set.getId());
                    if (itemSnapshots == null || itemSnapshots.isEmpty()) {
                        throw new GeneralException(
                                RecordErrorCode.DAILY_RECORD_SNAPSHOT_INCOMPLETE);
                    }
                    List<UserCosmetic> members = itemSnapshots
                            .stream()
                            .sorted(Comparator.comparingInt(
                                    DailyRecordCosmeticSetItem::getSortOrder))
                            .map(DailyRecordCosmeticSetItem::getUserCosmetic)
                            .toList();
                    boolean missingCosmeticSnapshot = members.stream()
                            .map(member -> cosmeticKey(
                                    period.getValue(),
                                    member.getId()
                            ))
                            .anyMatch(key -> !cosmeticSnapshotKeys.contains(key));
                    if (missingCosmeticSnapshot) {
                        throw new GeneralException(
                                RecordErrorCode.DAILY_RECORD_SNAPSHOT_INCOMPLETE);
                    }
                    return new SetChoice(
                            period,
                            set.getSourceCosmeticSetId(),
                            set.getSetNameSnapshot(),
                            set.getSetUsageTimeSnapshot(),
                            List.copyOf(selection.getTagsSnapshot()),
                            members,
                            false
                    );
                })
                .toList();
    }

    private List<SetChoice> newSetChoices(
            RecordUsagePeriod period,
            List<Long> setIds,
            SelectionResources resources,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        PeriodSelection resolved = resolvePeriod(
                period,
                setIds,
                List.of(),
                resources
        );
        return resolved.sets().stream()
                .map(set -> new SetChoice(
                        period,
                        set.getId(),
                        set.getName(),
                        set.getUsageTime().getValue(),
                        CosmeticSetTagSelector.select(
                                set,
                                resources.setItemsBySetId()
                                        .getOrDefault(set.getId(), List.of()),
                                tagsByProductId
                        ),
                        resources.cosmeticsBySetId()
                                .getOrDefault(set.getId(), List.of()),
                        true
                ))
                .toList();
    }

    private List<DirectChoice> existingDirectChoices(
            RecordUsagePeriod period,
            ExistingRecordState existing
    ) {
        Map<String, DailyRecordCosmetic> cosmeticByPeriodAndId = existing.cosmetics()
                .stream()
                .collect(Collectors.toMap(
                        cosmetic -> cosmeticKey(
                                cosmetic.getUsagePeriod(),
                                cosmetic.getUserCosmetic().getId()
                        ),
                        Function.identity()
                ));
        return existing.selections().stream()
                .filter(selection -> selection.getUsagePeriod().equals(period.getValue()))
                .filter(selection -> selection.getSelectionType()
                        == DailyRecordSelectionType.COSMETIC)
                .sorted(Comparator.comparingInt(DailyRecordSelection::getSortOrder))
                .map(selection -> {
                    DailyRecordCosmetic cosmetic = cosmeticByPeriodAndId.get(cosmeticKey(
                            period.getValue(),
                            selection.getSourceId()
                    ));
                    if (cosmetic == null) {
                        throw new IllegalStateException("Missing cosmetic snapshot");
                    }
                    return new DirectChoice(
                            period,
                            cosmetic.getUserCosmetic(),
                            selection.getNameSnapshot(),
                            List.copyOf(selection.getTagsSnapshot()),
                            false
                    );
                })
                .toList();
    }

    private List<DirectChoice> newDirectChoices(
            RecordUsagePeriod period,
            List<Long> cosmeticIds,
            SelectionResources resources,
            Map<Long, List<String>> mainIngredientsByProductId
    ) {
        return cosmeticIds.stream()
                .sorted()
                .map(resources.cosmeticById()::get)
                .map(cosmetic -> new DirectChoice(
                        period,
                        cosmetic,
                        cosmetic.getCosmeticProduct().getProductName(),
                        mainIngredientsByProductId.getOrDefault(
                                cosmetic.getCosmeticProduct().getId(),
                                List.of()
                        ),
                        true
                ))
                .toList();
    }

    private void deleteExistingSelectionGraph(ExistingRecordState existing) {
        dailyRecordCosmeticSetItemRepository.deleteAll(existing.setItems());
        dailyRecordCosmeticSetItemRepository.flush();
        dailyRecordCosmeticSetRepository.deleteAll(existing.sets());
        dailyRecordCosmeticSetRepository.flush();
        dailyRecordSelectionRepository.deleteAll(existing.selections());
        dailyRecordSelectionRepository.flush();
        dailyRecordCosmeticRepository.deleteAll(existing.cosmetics());
        dailyRecordCosmeticRepository.flush();
    }

    private void saveSetChoices(
            DailyRecord dailyRecord,
            List<SetChoice> morningSets,
            List<SetChoice> nightSets
    ) {
        List<SetChoice> choices = Stream.concat(
                morningSets.stream(),
                nightSets.stream()
        ).toList();
        Map<String, Integer> nextSortOrder = new HashMap<>();
        List<DailyRecordCosmeticSet> snapshots = choices.stream()
                .map(choice -> DailyRecordCosmeticSet.builder()
                        .dailyRecord(dailyRecord)
                        .sourceCosmeticSetId(choice.sourceId())
                        .setNameSnapshot(choice.name())
                        .setUsageTimeSnapshot(choice.usageTime())
                        .usagePeriod(choice.period().getValue())
                        .sortOrder(nextSortOrder.merge(
                                choice.period().getValue(),
                                1,
                                Integer::sum
                        ))
                        .build())
                .toList();
        dailyRecordCosmeticSetRepository.saveAll(snapshots);
        Map<String, DailyRecordCosmeticSet> savedByKey = snapshots.stream()
                .collect(Collectors.toMap(
                        set -> setKey(set.getUsagePeriod(), set.getSourceCosmeticSetId()),
                        Function.identity()
                ));

        List<DailyRecordCosmeticSetItem> items = choices.stream()
                .flatMap(choice -> IntStream
                        .range(0, choice.members().size())
                        .mapToObj(index -> DailyRecordCosmeticSetItem.builder()
                                .dailyRecordCosmeticSet(savedByKey.get(setKey(
                                        choice.period().getValue(),
                                        choice.sourceId()
                                )))
                                .userCosmetic(choice.members().get(index))
                                .sortOrder(index + 1)
                                .build()))
                .toList();
        dailyRecordCosmeticSetItemRepository.saveAll(items);
    }

    private void saveChoiceSelections(
            DailyRecord dailyRecord,
            List<SetChoice> morningSets,
            List<SetChoice> nightSets,
            List<DirectChoice> morningDirect,
            List<DirectChoice> nightDirect
    ) {
        List<DailyRecordSelection> selections = new ArrayList<>();
        selections.addAll(toSetChoiceSelections(dailyRecord, morningSets));
        selections.addAll(toSetChoiceSelections(dailyRecord, nightSets));
        selections.addAll(toDirectChoiceSelections(dailyRecord, morningDirect));
        selections.addAll(toDirectChoiceSelections(dailyRecord, nightDirect));
        dailyRecordSelectionRepository.saveAll(selections);
    }

    private List<DailyRecordSelection> toSetChoiceSelections(
            DailyRecord dailyRecord,
            List<SetChoice> choices
    ) {
        return IntStream.range(0, choices.size())
                .mapToObj(index -> {
                    SetChoice choice = choices.get(index);
                    return DailyRecordSelection.builder()
                            .dailyRecord(dailyRecord)
                            .usagePeriod(choice.period().getValue())
                            .selectionType(DailyRecordSelectionType.SET)
                            .sourceId(choice.sourceId())
                            .nameSnapshot(choice.name())
                            .tagsSnapshot(choice.tags())
                            .sortOrder(index + 1)
                            .build();
                })
                .toList();
    }

    private List<DailyRecordSelection> toDirectChoiceSelections(
            DailyRecord dailyRecord,
            List<DirectChoice> choices
    ) {
        return IntStream.range(0, choices.size())
                .mapToObj(index -> {
                    DirectChoice choice = choices.get(index);
                    return DailyRecordSelection.builder()
                            .dailyRecord(dailyRecord)
                            .usagePeriod(choice.period().getValue())
                            .selectionType(DailyRecordSelectionType.COSMETIC)
                            .sourceId(choice.cosmetic().getId())
                            .nameSnapshot(choice.name())
                            .tagsSnapshot(choice.tags())
                            .sortOrder(index + 1)
                            .build();
                })
                .toList();
    }

    private void saveChoiceCosmetics(
            DailyRecord dailyRecord,
            List<DailyRecordCosmetic> existingCosmetics,
            List<SetChoice> morningSets,
            List<SetChoice> nightSets,
            List<DirectChoice> morningDirect,
            List<DirectChoice> nightDirect
    ) {
        Map<String, DailyRecordCosmetic> existingByKey = existingCosmetics.stream()
                .collect(Collectors.toMap(
                        cosmetic -> cosmeticKey(
                                cosmetic.getUsagePeriod(),
                                cosmetic.getUserCosmetic().getId()
                        ),
                        Function.identity()
                ));
        Map<RecordUsagePeriod, LinkedHashMap<Long, EffectiveCosmetic>> effective =
                new LinkedHashMap<>();
        effective.put(RecordUsagePeriod.MORNING, new LinkedHashMap<>());
        effective.put(RecordUsagePeriod.NIGHT, new LinkedHashMap<>());

        addSetMembers(effective, morningSets);
        addSetMembers(effective, nightSets);
        addDirectCosmetics(effective, morningDirect);
        addDirectCosmetics(effective, nightDirect);

        Set<Long> productIdsToRefresh = effective.values().stream()
                .flatMap(map -> map.values().stream())
                .filter(value -> value.refreshed()
                        || !existingByKey.containsKey(cosmeticKey(
                                value.period().getValue(),
                                value.cosmetic().getId()
                        )))
                .map(EffectiveCosmetic::cosmetic)
                .map(UserCosmetic::getCosmeticProduct)
                .map(CosmeticProduct::getId)
                .collect(Collectors.toSet());
        Map<Long, List<IngredientSnapshot>> ingredientsByProductId =
                loadIngredients(productIdsToRefresh);

        List<DailyRecordCosmetic> snapshots = new ArrayList<>();
        for (Map.Entry<RecordUsagePeriod, LinkedHashMap<Long, EffectiveCosmetic>> entry
                : effective.entrySet()) {
            int sortOrder = 1;
            for (EffectiveCosmetic value : entry.getValue().values()) {
                String key = cosmeticKey(
                        entry.getKey().getValue(),
                        value.cosmetic().getId()
                );
                DailyRecordCosmetic existing = existingByKey.get(key);
                if (!value.refreshed() && existing != null) {
                    snapshots.add(copyCosmeticSnapshot(
                            dailyRecord,
                            existing,
                            sortOrder++
                    ));
                } else {
                    snapshots.add(newCosmeticSnapshot(
                            dailyRecord,
                            entry.getKey(),
                            value.cosmetic(),
                            ingredientsByProductId,
                            sortOrder++
                    ));
                }
            }
        }
        dailyRecordCosmeticRepository.saveAll(snapshots);
    }

    private void addSetMembers(
            Map<RecordUsagePeriod, LinkedHashMap<Long, EffectiveCosmetic>> effective,
            List<SetChoice> choices
    ) {
        for (SetChoice choice : choices) {
            for (UserCosmetic cosmetic : choice.members()) {
                mergeEffectiveCosmetic(
                        effective.get(choice.period()),
                        new EffectiveCosmetic(
                                choice.period(),
                                cosmetic,
                                choice.refreshed()
                        )
                );
            }
        }
    }

    private void addDirectCosmetics(
            Map<RecordUsagePeriod, LinkedHashMap<Long, EffectiveCosmetic>> effective,
            List<DirectChoice> choices
    ) {
        for (DirectChoice choice : choices) {
            mergeEffectiveCosmetic(
                    effective.get(choice.period()),
                    new EffectiveCosmetic(
                            choice.period(),
                            choice.cosmetic(),
                            choice.refreshed()
                    )
            );
        }
    }

    private void mergeEffectiveCosmetic(
            LinkedHashMap<Long, EffectiveCosmetic> effective,
            EffectiveCosmetic candidate
    ) {
        EffectiveCosmetic existing = effective.get(candidate.cosmetic().getId());
        if (existing == null) {
            effective.put(candidate.cosmetic().getId(), candidate);
        } else if (!existing.refreshed() && candidate.refreshed()) {
            effective.put(candidate.cosmetic().getId(), candidate);
        }
    }

    private DailyRecordCosmetic copyCosmeticSnapshot(
            DailyRecord dailyRecord,
            DailyRecordCosmetic existing,
            int sortOrder
    ) {
        return DailyRecordCosmetic.builder()
                .dailyRecord(dailyRecord)
                .userCosmetic(existing.getUserCosmetic())
                .usagePeriod(existing.getUsagePeriod())
                .productNameSnapshot(existing.getProductNameSnapshot())
                .brandNameSnapshot(existing.getBrandNameSnapshot())
                .productTypeSnapshot(existing.getProductTypeSnapshot())
                .customNameSnapshot(existing.getCustomNameSnapshot())
                .ingredientsSnapshot(existing.getIngredientsSnapshot())
                .sortOrder(sortOrder)
                .build();
    }

    private DailyRecordCosmetic newCosmeticSnapshot(
            DailyRecord dailyRecord,
            RecordUsagePeriod period,
            UserCosmetic cosmetic,
            Map<Long, List<IngredientSnapshot>> ingredientsByProductId,
            int sortOrder
    ) {
        CosmeticProduct product = cosmetic.getCosmeticProduct();
        return DailyRecordCosmetic.builder()
                .dailyRecord(dailyRecord)
                .userCosmetic(cosmetic)
                .usagePeriod(period.getValue())
                .productNameSnapshot(product.getProductName())
                .brandNameSnapshot(product.getBrandName())
                .productTypeSnapshot(product.getProductType())
                .customNameSnapshot(cosmetic.getCustomName())
                .ingredientsSnapshot(writeIngredients(
                        ingredientsByProductId.getOrDefault(product.getId(), List.of())
                ))
                .sortOrder(sortOrder)
                .build();
    }

    private String selectionKey(
            String usagePeriod,
            DailyRecordSelectionType type,
            Long sourceId
    ) {
        return usagePeriod + ":" + type + ":" + sourceId;
    }

    private String cosmeticKey(String usagePeriod, Long cosmeticId) {
        return usagePeriod + ":" + cosmeticId;
    }

    private boolean isDuplicateRecordConstraint(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException constraintViolation) {
                return "uk_daily_records_user_date"
                        .equalsIgnoreCase(constraintViolation.getConstraintName());
            }
            current = current.getCause();
        }
        return false;
    }

    private SkinStatus validateRequest(DailyRecordCreateRequestDto request) {
        if (request.date() == null
                || request.morningCosmeticIds() == null
                || request.nightCosmeticIds() == null) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
        if (request.date().isAfter(LocalDate.now(KOREA_ZONE))) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        SkinStatus skinStatus = SkinStatus.from(request.skinStatus())
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.BAD_REQUEST));
        if (skinStatus.requiresMemo()
                && (request.memo() == null || request.memo().isBlank())) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        validateNoDuplicates(request.morningCosmeticIds());
        validateNoDuplicates(request.nightCosmeticIds());
        validateNoDuplicates(request.morningCosmeticSetIds());
        validateNoDuplicates(request.nightCosmeticSetIds());

        validateRequiredPeriodSelections(
                request.morningCosmeticIds(),
                request.morningCosmeticSetIds(),
                request.nightCosmeticIds(),
                request.nightCosmeticSetIds()
        );
        return skinStatus;
    }

    private void validateRequiredPeriodSelections(
            List<Long> morningCosmeticIds,
            List<Long> morningCosmeticSetIds,
            List<Long> nightCosmeticIds,
            List<Long> nightCosmeticSetIds
    ) {
        boolean noMorningSelection = morningCosmeticIds.isEmpty()
                && morningCosmeticSetIds.isEmpty();
        boolean noNightSelection = nightCosmeticIds.isEmpty()
                && nightCosmeticSetIds.isEmpty();
        if (noMorningSelection || noNightSelection) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void validateNoDuplicates(List<Long> ids) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private SelectionResources loadSelectionResources(
            List<Long> morningCosmeticIds,
            List<Long> morningCosmeticSetIds,
            List<Long> nightCosmeticIds,
            List<Long> nightCosmeticSetIds,
            User user
    ) {
        Set<Long> cosmeticIds = combineIds(
                morningCosmeticIds,
                nightCosmeticIds
        );
        List<UserCosmetic> cosmetics = cosmeticIds.isEmpty()
                ? List.of()
                : userCosmeticRepository.findAllActiveByIdsAndUserForRecord(cosmeticIds, user);
        if (cosmetics.size() != cosmeticIds.size()) {
            throw new GeneralException(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND);
        }

        Set<Long> setIds = combineIds(
                morningCosmeticSetIds,
                nightCosmeticSetIds
        );
        List<CosmeticSet> cosmeticSets = setIds.isEmpty()
                ? List.of()
                : cosmeticSetRepository.findAllByIdInAndUserOrderById(setIds, user);
        if (cosmeticSets.size() != setIds.size()) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND);
        }

        List<CosmeticSetItem> setItems = cosmeticSets.isEmpty()
                ? List.of()
                : cosmeticSetItemRepository
                        .findAllActiveByCosmeticSetsOrderBySetAndCosmeticId(cosmeticSets);
        Map<Long, List<CosmeticSetItem>> setItemsBySetId = setItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getCosmeticSet().getId(),
                        LinkedHashMap::new,
                        Collectors.toList()
                ));
        Map<Long, List<UserCosmetic>> cosmeticsBySetId = setItemsBySetId.entrySet()
                .stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().stream()
                                .map(CosmeticSetItem::getUserCosmetic)
                                .toList(),
                        (left, right) -> left,
                        LinkedHashMap::new
                ));

        if (cosmeticSets.stream().anyMatch(set ->
                cosmeticsBySetId.getOrDefault(set.getId(), List.of()).isEmpty())) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return new SelectionResources(
                cosmetics.stream().collect(Collectors.toMap(UserCosmetic::getId, Function.identity())),
                cosmeticSets.stream().collect(Collectors.toMap(CosmeticSet::getId, Function.identity())),
                cosmeticsBySetId,
                setItemsBySetId
        );
    }

    private PeriodSelection resolvePeriod(
            RecordUsagePeriod period,
            List<Long> selectedSetIds,
            List<Long> selectedCosmeticIds,
            SelectionResources resources
    ) {
        List<CosmeticSet> orderedSets = selectedSetIds.stream()
                .sorted()
                .map(resources.setById()::get)
                .toList();
        if (orderedSets.stream().anyMatch(set -> !period.supports(set.getUsageTime()))) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        LinkedHashMap<Long, UserCosmetic> orderedCosmetics = new LinkedHashMap<>();
        for (CosmeticSet cosmeticSet : orderedSets) {
            resources.cosmeticsBySetId()
                    .getOrDefault(cosmeticSet.getId(), List.of())
                    .stream()
                    .sorted(java.util.Comparator.comparing(UserCosmetic::getId))
                    .forEach(cosmetic -> orderedCosmetics.putIfAbsent(cosmetic.getId(), cosmetic));
        }
        List<UserCosmetic> orderedDirectCosmetics = selectedCosmeticIds.stream()
                .sorted()
                .map(resources.cosmeticById()::get)
                .toList();
        orderedDirectCosmetics.forEach(cosmetic ->
                orderedCosmetics.putIfAbsent(cosmetic.getId(), cosmetic));

        return new PeriodSelection(
                period,
                orderedSets,
                new ArrayList<>(orderedCosmetics.values()),
                orderedDirectCosmetics
        );
    }

    private void saveSelectionSnapshots(
            DailyRecord dailyRecord,
            PeriodSelection morning,
            PeriodSelection night,
            SelectionResources resources
    ) {
        Set<Long> directProductIds = Stream.concat(
                        morning.directCosmetics().stream(),
                        night.directCosmetics().stream()
                )
                .map(UserCosmetic::getCosmeticProduct)
                .map(CosmeticProduct::getId)
                .collect(Collectors.toSet());
        Map<Long, List<String>> mainIngredientsByProductId =
                loadMainIngredientNames(directProductIds);
        Map<Long, List<CosmeticTag>> tagsByProductId = loadSetTags(resources);

        List<DailyRecordSelection> snapshots = new ArrayList<>();
        snapshots.addAll(toSelectionSnapshots(
                dailyRecord,
                morning,
                resources,
                mainIngredientsByProductId,
                tagsByProductId
        ));
        snapshots.addAll(toSelectionSnapshots(
                dailyRecord,
                night,
                resources,
                mainIngredientsByProductId,
                tagsByProductId
        ));
        dailyRecordSelectionRepository.saveAll(snapshots);
    }

    private List<DailyRecordSelection> toSelectionSnapshots(
            DailyRecord dailyRecord,
            PeriodSelection selection,
            SelectionResources resources,
            Map<Long, List<String>> mainIngredientsByProductId,
            Map<Long, List<CosmeticTag>> tagsByProductId
    ) {
        List<DailyRecordSelection> snapshots = new ArrayList<>();
        for (int index = 0; index < selection.sets().size(); index++) {
            CosmeticSet set = selection.sets().get(index);
            List<String> tags = CosmeticSetTagSelector.select(
                    set,
                    resources.setItemsBySetId().getOrDefault(set.getId(), List.of()),
                    tagsByProductId
            );
            snapshots.add(DailyRecordSelection.builder()
                    .dailyRecord(dailyRecord)
                    .usagePeriod(selection.period().getValue())
                    .selectionType(DailyRecordSelectionType.SET)
                    .sourceId(set.getId())
                    .nameSnapshot(set.getName())
                    .tagsSnapshot(tags)
                    .sortOrder(index + 1)
                    .build());
        }

        for (int index = 0; index < selection.directCosmetics().size(); index++) {
            UserCosmetic cosmetic = selection.directCosmetics().get(index);
            CosmeticProduct product = cosmetic.getCosmeticProduct();
            snapshots.add(DailyRecordSelection.builder()
                    .dailyRecord(dailyRecord)
                    .usagePeriod(selection.period().getValue())
                    .selectionType(DailyRecordSelectionType.COSMETIC)
                    .sourceId(cosmetic.getId())
                    .nameSnapshot(product.getProductName())
                    .tagsSnapshot(mainIngredientsByProductId
                            .getOrDefault(product.getId(), List.of()))
                    .sortOrder(index + 1)
                    .build());
        }
        return snapshots;
    }

    private Map<Long, List<String>> loadMainIngredientNames(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return cosmeticIngredientRepository.findAllMainByCosmeticProductIds(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        ingredient -> ingredient.getCosmeticProduct().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(
                                CosmeticIngredient::getIngredientName,
                                Collectors.toList()
                        )
                ));
    }

    private Map<Long, List<CosmeticTag>> loadSetTags(SelectionResources resources) {
        Set<Long> productIds = resources.setItemsBySetId().values().stream()
                .flatMap(Collection::stream)
                .map(CosmeticSetItem::getUserCosmetic)
                .map(UserCosmetic::getCosmeticProduct)
                .map(CosmeticProduct::getId)
                .collect(Collectors.toSet());
        if (productIds.isEmpty()) {
            return Map.of();
        }
        return cosmeticTagRepository.findAllByCosmeticProductIds(productIds)
                .stream()
                .collect(Collectors.groupingBy(
                        tag -> tag.getCosmeticProduct().getId(),
                        HashMap::new,
                        Collectors.toList()
                ));
    }

    private List<DailyRecordDetailResponseDto.Selection> toSelectionResponses(
            List<DailyRecordSelection> selections,
            RecordUsagePeriod period
    ) {
        return selections.stream()
                .filter(selection -> selection.getUsagePeriod().equals(period.getValue()))
                .sorted(Comparator
                        .comparingInt((DailyRecordSelection selection) ->
                                selection.getSelectionType() == DailyRecordSelectionType.SET
                                        ? 0
                                        : 1)
                        .thenComparingInt(DailyRecordSelection::getSortOrder))
                .map(this::toSelectionResponse)
                .toList();
    }

    private DailyRecordDetailResponseDto.Selection toSelectionResponse(
            DailyRecordSelection selection
    ) {
        List<String> tags = List.copyOf(selection.getTagsSnapshot());
        if (selection.getSelectionType() == DailyRecordSelectionType.SET) {
            return new DailyRecordDetailResponseDto.SetSelection(
                    selection.getSourceId(),
                    selection.getNameSnapshot(),
                    tags
            );
        }
        return new DailyRecordDetailResponseDto.CosmeticSelection(
                selection.getSourceId(),
                selection.getNameSnapshot(),
                tags
        );
    }

    private DailyRecordDetailResponseDto.Image toImageResponse(DailyRecordImage image) {
        return new DailyRecordDetailResponseDto.Image(
                image.getObjectKey(),
                imageReadUrlService.issueReadUrl(image.getObjectKey())
        );
    }

    private void saveSelectedSets(
            DailyRecord dailyRecord,
            PeriodSelection morning,
            PeriodSelection night,
            SelectionResources resources
    ) {
        List<DailyRecordCosmeticSet> snapshots = new ArrayList<>();
        snapshots.addAll(toSetSnapshots(dailyRecord, morning));
        snapshots.addAll(toSetSnapshots(dailyRecord, night));
        dailyRecordCosmeticSetRepository.saveAll(snapshots);

        Map<String, DailyRecordCosmeticSet> snapshotBySource = snapshots.stream()
                .collect(Collectors.toMap(
                        snapshot -> setKey(
                                snapshot.getUsagePeriod(),
                                snapshot.getSourceCosmeticSetId()),
                        Function.identity()
                ));
        List<DailyRecordCosmeticSetItem> itemSnapshots = Stream.concat(
                        morning.sets().stream().map(set -> Map.entry(morning.period(), set)),
                        night.sets().stream().map(set -> Map.entry(night.period(), set))
                )
                .flatMap(entry -> IntStream.range(
                                0,
                                resources.cosmeticsBySetId()
                                        .getOrDefault(entry.getValue().getId(), List.of())
                                        .size()
                        )
                        .mapToObj(index -> DailyRecordCosmeticSetItem.builder()
                                .dailyRecordCosmeticSet(snapshotBySource.get(setKey(
                                        entry.getKey().getValue(),
                                        entry.getValue().getId()
                                )))
                                .userCosmetic(resources.cosmeticsBySetId()
                                        .get(entry.getValue().getId()).get(index))
                                .sortOrder(index + 1)
                                .build()))
                .toList();
        dailyRecordCosmeticSetItemRepository.saveAll(itemSnapshots);
    }

    private String setKey(String usagePeriod, Long sourceId) {
        return usagePeriod + ":" + sourceId;
    }

    private List<DailyRecordCosmeticSet> toSetSnapshots(
            DailyRecord dailyRecord,
            PeriodSelection selection
    ) {
        return IntStream.range(0, selection.sets().size())
                .mapToObj(index -> {
                    CosmeticSet set = selection.sets().get(index);
                    return DailyRecordCosmeticSet.builder()
                            .dailyRecord(dailyRecord)
                            .sourceCosmeticSetId(set.getId())
                            .setNameSnapshot(set.getName())
                            .setUsageTimeSnapshot(set.getUsageTime().getValue())
                            .usagePeriod(selection.period().getValue())
                            .sortOrder(index + 1)
                            .build();
                })
                .toList();
    }

    private void saveUsedCosmetics(
            DailyRecord dailyRecord,
            PeriodSelection morning,
            PeriodSelection night
    ) {
        Set<Long> productIds = Stream.concat(
                        morning.cosmetics().stream(),
                        night.cosmetics().stream()
                )
                .map(UserCosmetic::getCosmeticProduct)
                .map(CosmeticProduct::getId)
                .collect(Collectors.toSet());
        Map<Long, List<IngredientSnapshot>> ingredientsByProductId = loadIngredients(productIds);

        List<DailyRecordCosmetic> snapshots = new ArrayList<>();
        snapshots.addAll(toCosmeticSnapshots(dailyRecord, morning, ingredientsByProductId));
        snapshots.addAll(toCosmeticSnapshots(dailyRecord, night, ingredientsByProductId));
        dailyRecordCosmeticRepository.saveAll(snapshots);
    }

    private List<DailyRecordCosmetic> toCosmeticSnapshots(
            DailyRecord dailyRecord,
            PeriodSelection selection,
            Map<Long, List<IngredientSnapshot>> ingredientsByProductId
    ) {
        return IntStream.range(0, selection.cosmetics().size())
                .mapToObj(index -> {
                    UserCosmetic userCosmetic = selection.cosmetics().get(index);
                    CosmeticProduct product = userCosmetic.getCosmeticProduct();
                    return DailyRecordCosmetic.builder()
                            .dailyRecord(dailyRecord)
                            .userCosmetic(userCosmetic)
                            .usagePeriod(selection.period().getValue())
                            .productNameSnapshot(product.getProductName())
                            .brandNameSnapshot(product.getBrandName())
                            .productTypeSnapshot(product.getProductType())
                            .customNameSnapshot(userCosmetic.getCustomName())
                            .ingredientsSnapshot(writeIngredients(
                                    ingredientsByProductId.getOrDefault(product.getId(), List.of())))
                            .sortOrder(index + 1)
                            .build();
                })
                .toList();
    }

    private Map<Long, List<IngredientSnapshot>> loadIngredients(Collection<Long> productIds) {
        if (productIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<IngredientSnapshot>> result = new HashMap<>();
        for (CosmeticIngredient cosmeticIngredient : cosmeticIngredientRepository
                .findAllByCosmeticProductIdsOrderByIngredientOrder(productIds)) {
            Long ingredientId = cosmeticIngredient.getIngredient() == null
                    ? null
                    : cosmeticIngredient.getIngredient().getId();
            result.computeIfAbsent(cosmeticIngredient.getCosmeticProduct().getId(), ignored -> new ArrayList<>())
                    .add(new IngredientSnapshot(ingredientId, cosmeticIngredient.getIngredientName()));
        }
        return result;
    }

    private String writeIngredients(List<IngredientSnapshot> ingredients) {
        try {
            return objectMapper.writeValueAsString(ingredients);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize cosmetic ingredients", e);
        }
    }

    private Set<Long> combineIds(List<Long> first, List<Long> second) {
        Set<Long> ids = new HashSet<>(first);
        ids.addAll(second);
        return ids;
    }

    private record SelectionResources(
            Map<Long, UserCosmetic> cosmeticById,
            Map<Long, CosmeticSet> setById,
            Map<Long, List<UserCosmetic>> cosmeticsBySetId,
            Map<Long, List<CosmeticSetItem>> setItemsBySetId
    ) {
    }

    private record PeriodSelection(
            RecordUsagePeriod period,
            List<CosmeticSet> sets,
            List<UserCosmetic> cosmetics,
            List<UserCosmetic> directCosmetics
    ) {
    }

    private record IngredientSnapshot(Long ingredientId, String name) {
    }

    private record ExistingRecordState(
            List<DailyRecordSelection> selections,
            List<DailyRecordCosmeticSet> sets,
            List<DailyRecordCosmeticSetItem> setItems,
            List<DailyRecordCosmetic> cosmetics,
            List<String> imageKeys
    ) {
    }

    private record UpdateValues(
            String skinStatus,
            List<Long> morningCosmeticIds,
            List<Long> morningCosmeticSetIds,
            List<Long> nightCosmeticIds,
            List<Long> nightCosmeticSetIds,
            String foodMemo,
            List<String> imageKeys,
            String memo,
            boolean morningCosmeticIdsChanged,
            boolean morningCosmeticSetIdsChanged,
            boolean nightCosmeticIdsChanged,
            boolean nightCosmeticSetIdsChanged
    ) {
    }

    private record SetChoice(
            RecordUsagePeriod period,
            Long sourceId,
            String name,
            String usageTime,
            List<String> tags,
            List<UserCosmetic> members,
            boolean refreshed
    ) {
    }

    private record DirectChoice(
            RecordUsagePeriod period,
            UserCosmetic cosmetic,
            String name,
            List<String> tags,
            boolean refreshed
    ) {
    }

    private record EffectiveCosmetic(
            RecordUsagePeriod period,
            UserCosmetic cosmetic,
            boolean refreshed
    ) {
    }
}
