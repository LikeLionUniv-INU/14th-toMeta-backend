package com.likelion.tometa.domain.cosmetic.service;

import com.likelion.tometa.domain.cosmetic.client.OpenAiCosmeticSearchClient;
import com.likelion.tometa.domain.cosmetic.dto.response.CosmeticSearchResponseDto;
import com.likelion.tometa.domain.cosmetic.support.CosmeticSearchCandidate;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.support.AnonymousSessionUserResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CosmeticSearchServiceTest {

    private static final String SESSION_TOKEN = "session-token";

    @Mock
    private AnonymousSessionUserResolver sessionUserResolver;

    @Mock
    private OpenAiCosmeticSearchClient cosmeticSearchClient;

    @Mock
    private CosmeticSearchCacheService cosmeticSearchCacheService;

    @InjectMocks
    private CosmeticSearchService cosmeticSearchService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        ReflectionTestUtils.setField(user, "id", 1L);
        when(sessionUserResolver.resolve(SESSION_TOKEN)).thenReturn(user);
    }

    @Test
    void search_formatsSingleResultWithBrandName() {
        List<CosmeticSearchCandidate> candidates = List.of(
                candidate("어성초 토너", "  아누아  ")
        );
        when(cosmeticSearchClient.search("토너")).thenReturn(candidates);
        when(cosmeticSearchCacheService.save(1L, candidates)).thenReturn("search-id");

        CosmeticSearchResponseDto result = cosmeticSearchService.search(
                "  토너  ",
                SESSION_TOKEN
        );

        assertEquals("search-id", result.searchId());
        assertEquals(1, result.items().size());
        assertEquals("아누아 어성초 토너", result.items().getFirst().productName());
        assertEquals("아누아", result.items().getFirst().brandName());
    }

    @Test
    void search_formatsMultipleResultsWithoutNullTextOrExtraSpaces() {
        List<CosmeticSearchCandidate> candidates = List.of(
                candidate("다이브인 세럼", "토리든"),
                candidate("진정 크림", null),
                candidate("수분 로션", "   ")
        );
        when(cosmeticSearchClient.search("수분 진정")).thenReturn(candidates);
        when(cosmeticSearchCacheService.save(1L, candidates)).thenReturn("search-id");

        CosmeticSearchResponseDto result = cosmeticSearchService.search(
                "수분 진정",
                SESSION_TOKEN
        );

        assertEquals(
                List.of("토리든 다이브인 세럼", "진정 크림", "수분 로션"),
                result.items().stream()
                        .map(CosmeticSearchResponseDto.Item::productName)
                        .toList()
        );
        assertEquals(
                List.of("토리든", "-", "-"),
                result.items().stream()
                        .map(CosmeticSearchResponseDto.Item::brandName)
                        .toList()
        );
        verify(cosmeticSearchCacheService).save(1L, candidates);
    }

    private CosmeticSearchCandidate candidate(String productName, String brandName) {
        return new CosmeticSearchCandidate(
                productName,
                brandName,
                "serum",
                "https://example.com/image.jpg",
                "진정",
                List.of("판테놀")
        );
    }
}
