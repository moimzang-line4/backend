package com.moimzang.domain.budget.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerAutoCategory;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    // API-069의 currentBalance·totalIncome·totalExpense는 기수가 바뀌어도 이월되는 그룹 전체
    // 누적값이라, 같은 그룹의 다른(이전) 기수 장부도 합산 대상에 포함돼야 한다
    @Test
    void aggregateByTypeForGroup_같은_그룹의_다른_기수_장부도_합산한다() {
        persistEntry(groupTerm, LedgerType.INCOME, "이번 기수 수입", 500000, LocalDate.of(2026, 9, 1));
        GroupTerm previousTerm = persistGroupTerm(groupTerm.getGroup(), "25기");
        persistEntry(previousTerm, LedgerType.INCOME, "이전 기수 수입", 300000, LocalDate.of(2025, 9, 1));
        persistEntry(previousTerm, LedgerType.EXPENSE, "이전 기수 지출", 100000, LocalDate.of(2025, 10, 1));

        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByTypeForGroup(groupTerm.getGroup().getId());

        assertThat(totals).containsExactlyInAnyOrder(
                new LedgerTypeTotal(LedgerType.INCOME, 800000L, 2L),
                new LedgerTypeTotal(LedgerType.EXPENSE, 100000L, 1L));
    }

    @Test
    void aggregateByTypeForGroup_다른_그룹의_장부는_합산하지_않는다() {
        persistEntry(groupTerm, LedgerType.INCOME, "우리 수입", 500000, LocalDate.of(2026, 9, 1));
        GroupTerm otherGroupTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "26기");
        persistEntry(otherGroupTerm, LedgerType.INCOME, "남의 수입", 999999, LocalDate.of(2026, 9, 1));

        List<LedgerTypeTotal> totals = ledgerEntryRepository.aggregateByTypeForGroup(groupTerm.getGroup().getId());

        assertThat(totals).containsExactly(new LedgerTypeTotal(LedgerType.INCOME, 500000L, 1L));
    }

    @Test
    void search_다른_기수_내역은_포함하지_않는다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "이번 기수", 10000, LocalDate.of(2026, 11, 1));
        GroupTerm otherTerm = persistGroupTerm(groupTerm.getGroup(), "25기");
        persistEntry(otherTerm, LedgerType.EXPENSE, "다른 기수", 20000, LocalDate.of(2026, 11, 2));

        List<LedgerEntry> result = ledgerEntryRepository.search(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(LedgerEntry::getItemName).containsExactly("이번 기수");
    }

    @Test
    void search_type_필터를_적용한다() {
        persistEntry(groupTerm, LedgerType.INCOME, "수입", 10000, LocalDate.of(2026, 11, 1));
        persistEntry(groupTerm, LedgerType.EXPENSE, "지출", 20000, LocalDate.of(2026, 11, 2));

        List<LedgerEntry> result = ledgerEntryRepository.search(groupTerm.getId(), LedgerType.EXPENSE, null, null, 10);

        assertThat(result).extracting(LedgerEntry::getItemName).containsExactly("지출");
    }

    // entryDate만으로는 같은 날짜 내역의 순서가 안정적이지 않아 id desc를 보조 정렬로 쓴다
    @Test
    void search_entryDate_내림차순이고_같은_날짜는_id_내림차순이다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "오래된 지출", 10000, LocalDate.of(2026, 11, 1));
        persistEntry(groupTerm, LedgerType.EXPENSE, "같은날_먼저입력", 20000, LocalDate.of(2026, 11, 10));
        persistEntry(groupTerm, LedgerType.EXPENSE, "같은날_나중입력", 30000, LocalDate.of(2026, 11, 10));

        List<LedgerEntry> result = ledgerEntryRepository.search(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(LedgerEntry::getItemName)
                .containsExactly("같은날_나중입력", "같은날_먼저입력", "오래된 지출");
    }

    @Test
    void search_cursor_이후의_항목만_반환한다() {
        LedgerEntry first = persistEntry(groupTerm, LedgerType.EXPENSE, "최신", 10000, LocalDate.of(2026, 11, 10));
        persistEntry(groupTerm, LedgerType.EXPENSE, "중간", 20000, LocalDate.of(2026, 11, 5));
        persistEntry(groupTerm, LedgerType.EXPENSE, "가장 오래됨", 30000, LocalDate.of(2026, 11, 1));

        List<LedgerEntry> result = ledgerEntryRepository.search(
                groupTerm.getId(), null, first.getEntryDate(), first.getId(), 10);

        assertThat(result).extracting(LedgerEntry::getItemName).containsExactly("중간", "가장 오래됨");
    }

    @Test
    void search_limit만큼만_반환한다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "1", 10000, LocalDate.of(2026, 11, 1));
        persistEntry(groupTerm, LedgerType.EXPENSE, "2", 20000, LocalDate.of(2026, 11, 2));
        persistEntry(groupTerm, LedgerType.EXPENSE, "3", 30000, LocalDate.of(2026, 11, 3));

        List<LedgerEntry> result = ledgerEntryRepository.search(groupTerm.getId(), null, null, null, 2);

        assertThat(result).hasSize(2);
    }

    // 장부 엑셀 다운로드(API-075)용 — cursor 없이 그 기수 전체를 한 번에 가져온다
    @Test
    void findAllForExport_다른_기수_내역은_포함하지_않는다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "이번 기수", 10000, LocalDate.of(2026, 11, 1));
        GroupTerm otherTerm = persistGroupTerm(groupTerm.getGroup(), "25기");
        persistEntry(otherTerm, LedgerType.EXPENSE, "다른 기수", 20000, LocalDate.of(2026, 11, 2));

        List<LedgerEntry> result = ledgerEntryRepository.findAllForExport(groupTerm.getId());

        assertThat(result).extracting(LedgerEntry::getItemName).containsExactly("이번 기수");
    }

    // API-069(search)의 entryDate 내림차순과 방향만 반대다. id를 2차 정렬로 둬서 같은 날짜
    // 내역의 순서가 재호출해도 항상 같다
    @Test
    void findAllForExport_entryDate_오름차순이고_같은_날짜는_id_오름차순이다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "최근 지출", 10000, LocalDate.of(2026, 11, 20));
        LedgerEntry sameDateFirst = persistEntry(groupTerm, LedgerType.EXPENSE, "같은날_먼저입력", 20000, LocalDate.of(2026, 11, 1));
        LedgerEntry sameDateSecond = persistEntry(groupTerm, LedgerType.EXPENSE, "같은날_나중입력", 30000, LocalDate.of(2026, 11, 1));

        List<LedgerEntry> result = ledgerEntryRepository.findAllForExport(groupTerm.getId());

        assertThat(sameDateFirst.getId()).isLessThan(sameDateSecond.getId());
        assertThat(result).extracting(LedgerEntry::getItemName)
                .containsExactly("같은날_먼저입력", "같은날_나중입력", "최근 지출");
    }

    @Test
    void findAllForExport_페이지네이션_없이_전체를_반환한다() {
        for (int i = 1; i <= 25; i++) {
            persistEntry(groupTerm, LedgerType.EXPENSE, "항목" + i, 1000 * i, LocalDate.of(2026, 11, 1));
        }

        List<LedgerEntry> result = ledgerEntryRepository.findAllForExport(groupTerm.getId());

        assertThat(result).hasSize(25);
    }

    // 정산 내역 새로고침(API-074) 대상 일정 선정 쿼리 검증
    @Test
    void findAutoScheduleIds_자동기록이_있는_일정을_반환한다() {
        Schedule schedule = persistSchedule(groupTerm, "가을 공연");
        persistAutoEntry(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE, 30000);

        List<Long> scheduleIds = ledgerEntryRepository.findAutoScheduleIds(groupTerm.getId());

        assertThat(scheduleIds).containsExactly(schedule.getId());
    }

    @Test
    void findAutoScheduleIds_직접_입력만_있으면_제외한다() {
        persistEntry(groupTerm, LedgerType.EXPENSE, "간식비", 15000, LocalDate.of(2026, 11, 1));

        List<Long> scheduleIds = ledgerEntryRepository.findAutoScheduleIds(groupTerm.getId());

        assertThat(scheduleIds).isEmpty();
    }

    // clearScheduleReference(일정 삭제)로 schedule이 null이 된 고아 자동기록은 e.schedule.id가
    // 비교 대상 termId와 결코 같아질 수 없어 자연히 제외된다
    @Test
    void findAutoScheduleIds_schedule이_null인_고아_자동기록은_제외한다() {
        Schedule schedule = persistSchedule(groupTerm, "삭제된 일정");
        LedgerEntry orphan = persistAutoEntry(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE, 30000);
        ReflectionTestUtils.setField(orphan, "schedule", null);
        em.persistAndFlush(orphan);

        List<Long> scheduleIds = ledgerEntryRepository.findAutoScheduleIds(groupTerm.getId());

        assertThat(scheduleIds).isEmpty();
    }

    @Test
    void findAutoScheduleIds_다른_기수의_자동기록은_제외한다() {
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        Schedule otherSchedule = persistSchedule(otherTerm, "다른 기수 행사");
        persistAutoEntry(otherTerm, otherSchedule, LedgerAutoCategory.RECEIPT_EXPENSE, 30000);

        List<Long> scheduleIds = ledgerEntryRepository.findAutoScheduleIds(groupTerm.getId());

        assertThat(scheduleIds).isEmpty();
    }

    @Test
    void findByScheduleIdAndAutoCategoryIsNotNull_직접_입력은_제외하고_그_일정의_자동기록만_반환한다() {
        Schedule schedule = persistSchedule(groupTerm, "가을 공연");
        persistEntry(groupTerm, LedgerType.EXPENSE, "직접 입력", 5000, LocalDate.of(2026, 11, 1));
        LedgerEntry receiptExpense = persistAutoEntry(groupTerm, schedule, LedgerAutoCategory.RECEIPT_EXPENSE, 30000);
        LedgerEntry subsidyExpense = persistAutoEntry(groupTerm, schedule, LedgerAutoCategory.CLUB_SUBSIDY_EXPENSE, 10000);
        Schedule otherSchedule = persistSchedule(groupTerm, "다른 일정");
        persistAutoEntry(groupTerm, otherSchedule, LedgerAutoCategory.RECEIPT_EXPENSE, 99999);

        List<LedgerEntry> result = ledgerEntryRepository.findByScheduleIdAndAutoCategoryIsNotNull(schedule.getId());

        assertThat(result).containsExactlyInAnyOrder(receiptExpense, subsidyExpense);
    }

    private Schedule persistSchedule(GroupTerm term, String title) {
        Schedule schedule = Schedule.create(term, null, title, "RED", false,
                LocalDateTime.of(2026, 11, 20, 18, 0), LocalDateTime.of(2026, 11, 20, 21, 0),
                null, null, false);
        return em.persistAndFlush(schedule);
    }

    private LedgerEntry persistAutoEntry(GroupTerm term, Schedule schedule, LedgerAutoCategory category, int amount) {
        LedgerEntry entry = LedgerEntry.createAuto(term, schedule, category, schedule.getTitle() + " " + category.label(),
                amount, LocalDate.of(2026, 11, 20));
        return em.persistAndFlush(entry);
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
