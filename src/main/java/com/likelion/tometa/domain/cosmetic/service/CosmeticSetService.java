package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetUpdateRequestDto;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSetCreateResponseDto;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSet;
import com.likelion.tometa.domain.cosmetic.entity.CosmeticSetItem;
import com.likelion.tometa.domain.cosmetic.entity.UserCosmetic;
import com.likelion.tometa.domain.cosmetic.enums.CosmeticSetUsageTime;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetItemRepository;
import com.likelion.tometa.domain.cosmetic.repository.CosmeticSetRepository;
import com.likelion.tometa.domain.cosmetic.repository.UserCosmeticRepository;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import com.likelion.tometa.global.code.GlobalErrorCode;
import com.likelion.tometa.global.exception.GeneralException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class CosmeticSetService {

    private final AnonymousSessionUserResolver sessionUserResolver;
    private final UserCosmeticRepository userCosmeticRepository;
    private final CosmeticSetRepository cosmeticSetRepository;
    private final CosmeticSetItemRepository cosmeticSetItemRepository;

    @Transactional
    public CosmeticSetCreateResponseDto createCosmeticSet(
            CosmeticSetCreateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        List<Long> userCosmeticIds = request.userCosmeticIds();

        validateUserCosmeticIds(userCosmeticIds);
        CosmeticSetUsageTime usageTime = parseUsageTime(request.usageTime());
        Map<Long, UserCosmetic> userCosmeticById = findUserCosmeticsById(
                userCosmeticIds,
                user
        );

        CosmeticSet cosmeticSet = cosmeticSetRepository.save(
                CosmeticSet.builder()
                        .user(user)
                        .name(request.name())
                        .usageTime(usageTime)
                        .build()
        );

        List<CosmeticSetItem> items = createItems(
                cosmeticSet,
                userCosmeticIds,
                userCosmeticById
        );
        cosmeticSetItemRepository.saveAll(items);

        return new CosmeticSetCreateResponseDto(cosmeticSet.getId());
    }

    @Transactional
    public void updateCosmeticSet(
            Long setId,
            CosmeticSetUpdateRequestDto request,
            String sessionToken
    ) {
        User user = sessionUserResolver.resolve(sessionToken);
        CosmeticSet cosmeticSet = cosmeticSetRepository.findByIdAndUser(setId, user)
                .orElseThrow(() -> new GeneralException(
                        CosmeticErrorCode.COSMETIC_SET_NOT_FOUND));

        validateUpdateRequest(request);

        CosmeticSetUsageTime usageTime = request.usageTime() == null
                ? null
                : parseUsageTime(request.usageTime());
        Map<Long, UserCosmetic> userCosmeticById = null;

        if (request.userCosmeticIds() != null) {
            validateUserCosmeticIds(request.userCosmeticIds());
            userCosmeticById = findUserCosmeticsById(request.userCosmeticIds(), user);
        }

        if (request.name() != null) {
            cosmeticSet.updateName(request.name());
        }

        if (usageTime != null) {
            cosmeticSet.updateUsageTime(usageTime);
        }

        if (userCosmeticById != null) {
            replaceItems(cosmeticSet, request.userCosmeticIds(), userCosmeticById);
        }
    }

    private void validateUpdateRequest(CosmeticSetUpdateRequestDto request) {
        if (request.name() == null
                && request.usageTime() == null
                && request.userCosmeticIds() == null) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }
    }

    private void replaceItems(
            CosmeticSet cosmeticSet,
            List<Long> userCosmeticIds,
            Map<Long, UserCosmetic> userCosmeticById
    ) {
        cosmeticSetItemRepository.deleteAllByCosmeticSet(cosmeticSet);
        cosmeticSetItemRepository.saveAll(
                createItems(cosmeticSet, userCosmeticIds, userCosmeticById));
    }

    private Map<Long, UserCosmetic> findUserCosmeticsById(
            List<Long> userCosmeticIds,
            User user
    ) {
        List<UserCosmetic> userCosmetics = userCosmeticRepository
                .findAllByIdInAndUserAndDeletedAtIsNull(userCosmeticIds, user);

        if (userCosmetics.size() != userCosmeticIds.size()) {
            throw new GeneralException(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND);
        }

        Map<Long, UserCosmetic> userCosmeticById = new HashMap<>();
        for (UserCosmetic userCosmetic : userCosmetics) {
            userCosmeticById.put(userCosmetic.getId(), userCosmetic);
        }
        return userCosmeticById;
    }

    private void validateUserCosmeticIds(List<Long> userCosmeticIds) {
        if (userCosmeticIds == null || userCosmeticIds.isEmpty()) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED);
        }

        if (userCosmeticIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new GeneralException(GlobalErrorCode.BAD_REQUEST);
        }

        if (new HashSet<>(userCosmeticIds).size() != userCosmeticIds.size()) {
            throw new GeneralException(CosmeticErrorCode.COSMETIC_SET_DUPLICATE_ITEM);
        }
    }

    private CosmeticSetUsageTime parseUsageTime(String usageTime) {
        return CosmeticSetUsageTime.from(usageTime)
                .orElseThrow(() -> new GeneralException(GlobalErrorCode.BAD_REQUEST));
    }

    private List<CosmeticSetItem> createItems(
            CosmeticSet cosmeticSet,
            List<Long> userCosmeticIds,
            Map<Long, UserCosmetic> userCosmeticById
    ) {
        return java.util.stream.IntStream.range(0, userCosmeticIds.size())
                .mapToObj(index -> CosmeticSetItem.builder()
                        .cosmeticSet(cosmeticSet)
                        .userCosmetic(userCosmeticById.get(userCosmeticIds.get(index)))
                        .itemOrder(index + 1)
                        .build())
                .toList();
    }
}
