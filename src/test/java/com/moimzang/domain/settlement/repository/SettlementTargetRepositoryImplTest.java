package com.moimzang.domain.settlement.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// 정산 내역 새로고침(API-074) 대상 일정 선정 쿼리를 검증한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class SettlementTargetRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private SettlementTargetRepository settlementTargetRepository;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
    }

    @Test
    void findScheduleIdsByGroupTerm_정산대상이_있는_일정을_반환한다() {
        Schedule schedule = persistSchedule(groupTerm, "가을 공연");
        ScheduleRound round = persistRound(schedule, 1, 50000, 10000);
        ScheduleAttendee attendee = persistAttendee(schedule, "홍길동");
        persistTarget(round, attendee, 20000);

        List<Long> scheduleIds = settlementTargetRepository.findScheduleIdsByGroupTerm(groupTerm.getId());

        assertThat(scheduleIds).containsExactly(schedule.getId());
    }

    @Test
    void findScheduleIdsByGroupTerm_정산대상이_없는_일정은_제외한다() {
        Schedule schedule = persistSchedule(groupTerm, "정산대상 없는 일정");
        persistRound(schedule, 1, 50000, 10000);

        List<Long> scheduleIds = settlementTargetRepository.findScheduleIdsByGroupTerm(groupTerm.getId());

        assertThat(scheduleIds).isEmpty();
    }

    @Test
    void findScheduleIdsByGroupTerm_다른_기수의_일정은_제외한다() {
        GroupTerm otherTerm = persistGroupTerm(groupTerm.getGroup(), "25기");
        Schedule otherSchedule = persistSchedule(otherTerm, "이전 기수 행사");
        ScheduleRound otherRound = persistRound(otherSchedule, 1, 50000, 10000);
        ScheduleAttendee attendee = persistAttendee(otherSchedule, "홍길동");
        persistTarget(otherRound, attendee, 20000);

        List<Long> scheduleIds = settlementTargetRepository.findScheduleIdsByGroupTerm(groupTerm.getId());

        assertThat(scheduleIds).isEmpty();
    }

    // 정산 내역 새로고침(API-074)이 재사용하는 조회 쿼리 — 영수증은 있지만 정산 대상이 0명인
    // 차수는 settlement_target 행 자체가 없어 결과에 전혀 나타나지 않는다
    @Test
    void findByScheduleForAssistant_영수증은_있지만_정산대상이_0명인_차수는_반환하지_않는다() {
        Schedule schedule = persistSchedule(groupTerm, "가을 공연");
        ScheduleRound roundWithTarget = persistRound(schedule, 1, 50000, 10000);
        ScheduleAttendee attendee = persistAttendee(schedule, "홍길동");
        SettlementTarget target = persistTarget(roundWithTarget, attendee, 20000);
        persistRound(schedule, 2, 30000, 0);

        List<SettlementTarget> result = settlementTargetRepository.findByScheduleForAssistant(schedule.getId(), null);

        assertThat(result).containsExactly(target);
        assertThat(result).extracting(t -> t.getScheduleRound().getId())
                .containsOnly(roundWithTarget.getId());
    }

    private ClubGroup persistClubGroup(String name) {
        return em.persistAndFlush(ClubGroup.create(name));
    }

    private GroupTerm persistGroupTerm(ClubGroup group, String name) {
        return em.persistAndFlush(GroupTerm.create(group, name));
    }

    private Schedule persistSchedule(GroupTerm term, String title) {
        Schedule schedule = Schedule.create(term, null, title, "RED", false,
                LocalDateTime.of(2026, 11, 20, 18, 0), LocalDateTime.of(2026, 11, 20, 21, 0),
                null, null, false);
        return em.persistAndFlush(schedule);
    }

    private ScheduleRound persistRound(Schedule schedule, int roundNo, int totalAmount, int clubSubsidyAmount) {
        ScheduleRound round = ScheduleRound.create(schedule, roundNo, roundNo + "차");
        round.registerReceipt("가게", LocalDateTime.of(2026, 11, 20, 19, 0), totalAmount, clubSubsidyAmount, null);
        return em.persistAndFlush(round);
    }

    private ScheduleAttendee persistAttendee(Schedule schedule, String name) {
        return em.persistAndFlush(ScheduleAttendee.createOnSite(schedule, name, null, null));
    }

    private SettlementTarget persistTarget(ScheduleRound round, ScheduleAttendee attendee, int billedAmount) {
        return em.persistAndFlush(SettlementTarget.create(round, attendee, billedAmount));
    }
}
