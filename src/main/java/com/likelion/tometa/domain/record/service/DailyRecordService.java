package com.likelion.tometa.domain.record.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticIngredient;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticProduct;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
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
import com.likelion.tometa.domain.record.enums.RecordUsagePeriod;
import com.likelion.tometa.domain.record.enums.SkinStatus;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordCosmeticSetRepository;
import com.likelion.tometa.domain.record.repository.DailyRecordRepository;
import com.likelion.tometa.domain.report.entity.DailyReport;
import com.likelion.tometa.domain.report.repository.DailyReportRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DailyRecordService {

    private static final ZoneId KOREA_ZONE = ZoneId.of("Asia/Seoul");

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final DailyRecordRepository dailyRecordRepository;
    private final DailyRecordCosmeticRepository dailyRecordCosmeticRepository;
    private final DailyRecordCosmeticSetRepository dailyRecordCosmeticSetRepository;
    private final DailyReportRepository dailyReportRepository;
    private final UserCosmeticRepository userCosmeticRepository;
    private final CosmeticSetRepository cosmeticSetRepository;
    private final CosmeticSetItemRepository cosmeticSetItemRepository;
    private final CosmeticIngredientRepository cosmeticIngredientRepository;
    private final DailyRecordImageAttachmentService imageAttachmentService;
    private final ObjectMapper objectMapper;

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

        SelectionResources resources = loadSelectionResources(request, user);
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
            throw new GeneralException(RecordErrorCode.DAILY_RECORD_ALREADY_EXISTS);
        }

        saveSelectedSets(dailyRecord, morning, night);
        saveUsedCosmetics(dailyRecord, morning, night);
        imageAttachmentService.attach(dailyRecord, user, request.imageKeys());
        dailyReportRepository.save(DailyReport.builder()
                .dailyRecord(dailyRecord)
                .build());

        return new DailyRecordCreateResponseDto(dailyRecord.getId(), request.date());
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

        boolean noCosmeticSelection = request.morningCosmeticIds().isEmpty()
                && request.nightCosmeticIds().isEmpty()
                && request.morningCosmeticSetIds().isEmpty()
                && request.nightCosmeticSetIds().isEmpty();
        if (noCosmeticSelection) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
        return skinStatus;
    }

    private void validateNoDuplicates(List<Long> ids) {
        if (ids.stream().anyMatch(id -> id == null || id <= 0)
                || new HashSet<>(ids).size() != ids.size()) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private SelectionResources loadSelectionResources(
            DailyRecordCreateRequestDto request,
            User user
    ) {
        Set<Long> cosmeticIds = combineIds(
                request.morningCosmeticIds(),
                request.nightCosmeticIds()
        );
        List<UserCosmetic> cosmetics = cosmeticIds.isEmpty()
                ? List.of()
                : userCosmeticRepository.findAllActiveByIdsAndUserForRecord(cosmeticIds, user);
        if (cosmetics.size() != cosmeticIds.size()) {
            throw new GeneralException(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND);
        }

        Set<Long> setIds = combineIds(
                request.morningCosmeticSetIds(),
                request.nightCosmeticSetIds()
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
        Map<Long, List<UserCosmetic>> cosmeticsBySetId = setItems.stream()
                .collect(Collectors.groupingBy(
                        item -> item.getCosmeticSet().getId(),
                        LinkedHashMap::new,
                        Collectors.mapping(CosmeticSetItem::getUserCosmetic, Collectors.toList())
                ));

        if (cosmeticSets.stream().anyMatch(set ->
                cosmeticsBySetId.getOrDefault(set.getId(), List.of()).isEmpty())) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        return new SelectionResources(
                cosmetics.stream().collect(Collectors.toMap(UserCosmetic::getId, Function.identity())),
                cosmeticSets.stream().collect(Collectors.toMap(CosmeticSet::getId, Function.identity())),
                cosmeticsBySetId
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
        selectedCosmeticIds.stream()
                .sorted()
                .map(resources.cosmeticById()::get)
                .forEach(cosmetic -> orderedCosmetics.putIfAbsent(cosmetic.getId(), cosmetic));

        return new PeriodSelection(period, orderedSets, new ArrayList<>(orderedCosmetics.values()));
    }

    private void saveSelectedSets(
            DailyRecord dailyRecord,
            PeriodSelection morning,
            PeriodSelection night
    ) {
        List<DailyRecordCosmeticSet> snapshots = new ArrayList<>();
        snapshots.addAll(toSetSnapshots(dailyRecord, morning));
        snapshots.addAll(toSetSnapshots(dailyRecord, night));
        dailyRecordCosmeticSetRepository.saveAll(snapshots);
    }

    private List<DailyRecordCosmeticSet> toSetSnapshots(
            DailyRecord dailyRecord,
            PeriodSelection selection
    ) {
        return java.util.stream.IntStream.range(0, selection.sets().size())
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
        Set<Long> productIds = java.util.stream.Stream.concat(
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
        return java.util.stream.IntStream.range(0, selection.cosmetics().size())
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
            Map<Long, List<UserCosmetic>> cosmeticsBySetId
    ) {
    }

    private record PeriodSelection(
            RecordUsagePeriod period,
            List<CosmeticSet> sets,
            List<UserCosmetic> cosmetics
    ) {
    }

    private record IngredientSnapshot(Long ingredientId, String name) {
    }
}
