package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.code.CosmeticErrorCode;
import com.likelion.tometa.domain.cosmetic.dto.request.CosmeticSetCreateRequestDto;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmeticSetServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;

    @Mock
    private UserCosmeticRepository userCosmeticRepository;

    @Mock
    private CosmeticSetRepository cosmeticSetRepository;

    @Mock
    private CosmeticSetItemRepository cosmeticSetItemRepository;

    @InjectMocks
    private CosmeticSetService cosmeticSetService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
    }

    @Test
    void createCosmeticSet_savesSetAndItemsInRequestOrder() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "  진정템  ",
                "morning",
                List.of(11L, 12L, 15L)
        );
        UserCosmetic cosmetic11 = userCosmetic(11L);
        UserCosmetic cosmetic12 = userCosmetic(12L);
        UserCosmetic cosmetic15 = userCosmetic(15L);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic15, cosmetic11, cosmetic12));
        when(cosmeticSetRepository.save(any(CosmeticSet.class)))
                .thenAnswer(invocation -> {
                    CosmeticSet cosmeticSet = invocation.getArgument(0);
                    ReflectionTestUtils.setField(cosmeticSet, "id", 7L);
                    return cosmeticSet;
                });

        CosmeticSetCreateResponseDto result = cosmeticSetService
                .createCosmeticSet(request, SESSION_TOKEN);

        assertEquals(7L, result.setId());

        ArgumentCaptor<CosmeticSet> setCaptor = ArgumentCaptor.forClass(CosmeticSet.class);
        verify(cosmeticSetRepository).save(setCaptor.capture());

        CosmeticSet savedSet = setCaptor.getValue();
        assertSame(user, savedSet.getUser());
        assertEquals("진정템", savedSet.getName());
        assertSame(CosmeticSetUsageTime.MORNING, savedSet.getUsageTime());

        verify(cosmeticSetItemRepository).saveAll(argThat(savedItems -> {
            List<CosmeticSetItem> items = StreamSupport
                    .stream(savedItems.spliterator(), false)
                    .toList();

            assertEquals(3, items.size());
            assertSame(savedSet, items.get(0).getCosmeticSet());
            assertSame(cosmetic11, items.get(0).getUserCosmetic());
            assertEquals(1, items.get(0).getItemOrder());
            assertSame(cosmetic12, items.get(1).getUserCosmetic());
            assertEquals(2, items.get(1).getItemOrder());
            assertSame(cosmetic15, items.get(2).getUserCosmetic());
            assertEquals(3, items.get(2).getItemOrder());
            return true;
        }));
    }

    @Test
    void createCosmeticSet_rejectsEmptyCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                List.of()
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsNullCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                null
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_ITEMS_REQUIRED, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsDuplicateCosmeticIds() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "morning",
                List.of(11L, 11L, 12L)
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_DUPLICATE_ITEM, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsUnsupportedUsageTime() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "MORNING",
                List.of(11L)
        );
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(GlobalErrorCode.BAD_REQUEST, exception.getErrorCode());
        verifyNoInteractions(
                userCosmeticRepository,
                cosmeticSetRepository,
                cosmeticSetItemRepository
        );
    }

    @Test
    void createCosmeticSet_rejectsMissingOrUnownedCosmetic() {
        CosmeticSetCreateRequestDto request = new CosmeticSetCreateRequestDto(
                "진정템",
                "night",
                List.of(11L, 12L)
        );
        UserCosmetic cosmetic11 = mock(UserCosmetic.class);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(userCosmeticRepository.findAllByIdInAndUserAndDeletedAtIsNull(
                request.userCosmeticIds(),
                user
        )).thenReturn(List.of(cosmetic11));

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.createCosmeticSet(request, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.USER_COSMETIC_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetRepository, cosmeticSetItemRepository);
    }

    @Test
    void deleteCosmeticSet_deletesItemsBeforeOwnedSet() {
        CosmeticSet cosmeticSet = mock(CosmeticSet.class);

        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.of(cosmeticSet));

        cosmeticSetService.deleteCosmeticSet(7L, SESSION_TOKEN);

        InOrder deletionOrder = inOrder(
                cosmeticSetItemRepository,
                cosmeticSetRepository
        );
        deletionOrder.verify(cosmeticSetItemRepository).deleteAllByCosmeticSetId(7L);
        deletionOrder.verify(cosmeticSetRepository).delete(cosmeticSet);
    }

    @Test
    void deleteCosmeticSet_rejectsMissingOrUnownedSet() {
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
        when(cosmeticSetRepository.findByIdAndUser(7L, user))
                .thenReturn(Optional.empty());

        GeneralException exception = assertThrows(
                GeneralException.class,
                () -> cosmeticSetService.deleteCosmeticSet(7L, SESSION_TOKEN)
        );

        assertSame(CosmeticErrorCode.COSMETIC_SET_NOT_FOUND, exception.getErrorCode());
        verifyNoInteractions(cosmeticSetItemRepository);
        verify(cosmeticSetRepository, never()).delete(any(CosmeticSet.class));
    }

    private UserCosmetic userCosmetic(Long id) {
        UserCosmetic userCosmetic = mock(UserCosmetic.class);
        when(userCosmetic.getId()).thenReturn(id);
        return userCosmetic;
    }
}
