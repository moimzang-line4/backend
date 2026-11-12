package com.moimzang.domain.budget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 합계는 목록이 상한에 잘려도 맞아야 하는 값이라 DB 집계를 실제로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class LedgerEntryRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private LedgerEntryRepository ledgerEntryRepository;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
    }

    @Test
    void aggregateByType_수입과_지출을_유형별로_합산한다() {
        persistEntry(groupTerm, LedgerType.INCOME, "회비 수납", 800000, LocalDate.of(2026, 9, 1));
        persistEntry(groupTerm, LedgerType.INCOME, "후원금", 400000, LocalDate.of(2026, 10, 1));
        persistEntry(groupTerm, LedgerType.EXPENSE, "공연 대관료", 450000, LocalDate.of(2026, 11, 20));

        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByType(groupTerm.getId());

        assertThat(totals).containsExactlyInAnyOrder(
                new LedgerTypeTotal(LedgerType.INCOME, 1200000L, 2L),
                new LedgerTypeTotal(LedgerType.EXPENSE, 450000L, 1L));
    }

    @Test
    void aggregateByType_항목이_없는_유형은_결과에서_빠진다() {
        persistEntry(groupTerm, LedgerType.INCOME, "회비 수납", 800000, LocalDate.of(2026, 9, 1));

        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByType(groupTerm.getId());

        // 호출하는 쪽에서 0으로 채워야 하는 케이스다
        assertThat(totals).extracting(LedgerTypeTotal::type).containsExactly(LedgerType.INCOME);
    }

    @Test
    void aggregateByType_다른_기수의_장부는_합산하지_않는다() {
        persistEntry(groupTerm, LedgerType.INCOME, "회비 수납", 800000, LocalDate.of(2026, 9, 1));
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        persistEntry(otherTerm, LedgerType.INCOME, "남의 회비", 500000, LocalDate.of(2026, 9, 1));

        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByType(groupTerm.getId());

        assertThat(totals).containsExactly(new LedgerTypeTotal(LedgerType.INCOME, 800000L, 1L));
    }

    @Test
    void findForAssistant_최근_거래일_순으로_limit만큼_반환한다() {
        persistEntry(groupTerm, LedgerType.INCOME, "오래된 수입", 100000, LocalDate.of(2026, 9, 1));
        persistEntry(groupTerm, LedgerType.EXPENSE, "최근 지출", 50000, LocalDate.of(2026, 11, 20));
        persistEntry(groupTerm, LedgerType.EXPENSE, "중간 지출", 30000, LocalDate.of(2026, 10, 15));

        List<LedgerEntry> result = ledgerEntryRepository.findForAssistant(groupTerm.getId(), 2);

        assertThat(result).extracting(LedgerEntry::getItemName).containsExactly("최근 지출", "중간 지출");
    }

    private ClubGroup persistClubGroup(String name) {
        ClubGroup group = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(group, "name", name);
        return em.persistAndFlush(group);
    }

    private GroupTerm persistGroupTerm(ClubGroup group, String name) {
        GroupTerm term = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(term, "group", group);
        ReflectionTestUtils.setField(term, "name", name);
        return em.persistAndFlush(term);
    }

    private LedgerEntry persistEntry(GroupTerm term, LedgerType type, String itemName, int amount,
                                      LocalDate entryDate) {
        LedgerEntry entry = BeanUtils.instantiateClass(LedgerEntry.class);
        ReflectionTestUtils.setField(entry, "groupTerm", term);
        ReflectionTestUtils.setField(entry, "type", type);
        ReflectionTestUtils.setField(entry, "itemName", itemName);
        ReflectionTestUtils.setField(entry, "amount", amount);
        ReflectionTestUtils.setField(entry, "entryDate", entryDate);
        return em.persistAndFlush(entry);
    }
}
