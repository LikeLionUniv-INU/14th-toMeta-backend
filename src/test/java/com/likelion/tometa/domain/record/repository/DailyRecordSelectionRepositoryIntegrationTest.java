package com.likelion.tometa.domain.record.repository;

import com.likelion.tometa.domain.record.entity.DailyRecord;
import com.likelion.tometa.domain.record.entity.DailyRecordSelection;
import com.likelion.tometa.domain.record.enums.DailyRecordSelectionType;
import com.likelion.tometa.domain.user.entity.User;
import com.likelion.tometa.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.flyway.enabled=false"
})
class DailyRecordSelectionRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private DailyRecordRepository dailyRecordRepository;
    @Autowired
    private DailyRecordSelectionRepository selectionRepository;
    @Autowired
    private EntityManager entityManager;

    @Test
    void saveAndRead_preservesOrderedTagJson() {
        User user = userRepository.save(User.builder().build());
        DailyRecord dailyRecord = dailyRecordRepository.save(DailyRecord.builder()
                .user(user)
                .recordDate(LocalDate.of(2026, 8, 12))
                .skinStatus("normal")
                .build());
        selectionRepository.saveAndFlush(DailyRecordSelection.builder()
                .dailyRecord(dailyRecord)
                .usagePeriod("morning")
                .selectionType(DailyRecordSelectionType.SET)
                .sourceId(3L)
                .nameSnapshot("calming set")
                .tagsSnapshot(List.of("heartleaf", "panthenol"))
                .sortOrder(1)
                .build());
        entityManager.clear();

        DailyRecord reloadedRecord = dailyRecordRepository
                .findById(dailyRecord.getId())
                .orElseThrow();
        DailyRecordSelection selection = selectionRepository
                .findAllByDailyRecord(reloadedRecord)
                .getFirst();

        assertEquals(List.of("heartleaf", "panthenol"), selection.getTagsSnapshot());
    }
}
