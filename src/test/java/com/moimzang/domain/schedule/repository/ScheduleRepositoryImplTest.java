package com.moimzang.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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

// AI 비서가 넘기는 scheduleId는 모델이 만들어낸 값이라 기수 경계를 실제 DB로 확인해야 한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class ScheduleRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
    }

    @Test
    void findForAssistant_카테고리까지_함께_조회한다() {
        ScheduleCategory category = persistCategory(groupTerm, "공연");
        Schedule schedule = persistSchedule(groupTerm, category, "가을 정기공연");

        Optional<Schedule> result = scheduleRepository.findForAssistant(groupTerm.getId(), schedule.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getScheduleCategory().getName()).isEqualTo("공연");
    }

    @Test
    void findForAssistant_카테고리가_없는_일정도_조회한다() {
        Schedule schedule = persistSchedule(groupTerm, null, "MT");

        Optional<Schedule> result = scheduleRepository.findForAssistant(groupTerm.getId(), schedule.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getScheduleCategory()).isNull();
    }

    @Test
    void findForAssistant_다른_기수의_일정은_반환하지_않는다() {
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        Schedule other = persistSchedule(otherTerm, null, "다른 기수 공연");

        Optional<Schedule> result = scheduleRepository.findForAssistant(groupTerm.getId(), other.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findForAssistant_없는_일정이면_비어_있다() {
        Optional<Schedule> result = scheduleRepository.findForAssistant(groupTerm.getId(), 99999L);

        assertThat(result).isEmpty();
    }

    private ClubGroup persistClubGroup(String name) {
        ClubGroup group = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(group, "name", name);
        return em.persistAndFlush(group);
    }

    @Test
    void searchCalendar_기간과_겹치는_일정만_시작_시각순으로_반환한다() {
        Schedule before = persistSchedule(groupTerm, "10월 일정", at(10, 30, 18), at(10, 30, 20), true);
        Schedule overlap = persistSchedule(groupTerm, "10월 말부터 11월 초", at(10, 31, 18), at(11, 2, 12), false);
        Schedule inside = persistSchedule(groupTerm, "11월 공연", at(11, 14, 17), at(11, 14, 21), true);
        Schedule after = persistSchedule(groupTerm, "12월 일정", at(12, 1, 18), at(12, 1, 20), true);
        persistSchedule(persistGroupTerm(persistClubGroup("다른 동아리"), "25기"), "남의 일정",
                at(11, 10, 18), at(11, 10, 20), true);

        List<Schedule> result = scheduleRepository.searchCalendar(groupTerm.getId(),
                at(11, 1, 0), LocalDateTime.of(2026, 11, 30, 23, 59, 59), null, null, 10);

        assertThat(result).extracting(Schedule::getId).containsExactly(overlap.getId(), inside.getId())
                .doesNotContain(before.getId(), after.getId());
    }

    @Test
    void searchCalendar_cursor는_그_지점_이후부터_반환하고_같은_시각이면_id로_가른다() {
        Schedule a = persistSchedule(groupTerm, "A", at(11, 14, 17), at(11, 14, 21), true);
        Schedule b = persistSchedule(groupTerm, "B", at(11, 14, 17), at(11, 14, 21), true);
        Schedule c = persistSchedule(groupTerm, "C", at(11, 15, 17), at(11, 15, 21), true);

        List<Schedule> result = scheduleRepository.searchCalendar(groupTerm.getId(),
                at(11, 1, 0), at(11, 30, 23), a.getStartAt(), a.getId(), 10);

        assertThat(result).extracting(Schedule::getId).containsExactly(b.getId(), c.getId());
    }

    @Test
    void findUpcomingOperationSchedules_운영_공간이_있고_끝나지_않은_일정만_시작_시각순으로_반환한다() {
        LocalDateTime now = at(11, 14, 18);
        persistSchedule(groupTerm, "이미 끝남", at(11, 10, 18), at(11, 10, 20), true);
        Schedule ongoing = persistSchedule(groupTerm, "진행 중", at(11, 14, 17), at(11, 14, 21), true);
        persistSchedule(groupTerm, "운영 공간 없음", at(11, 15, 18), at(11, 15, 20), false);
        Schedule later = persistSchedule(groupTerm, "다음 주", at(11, 21, 18), at(11, 21, 20), true);
        Schedule latest = persistSchedule(groupTerm, "다다음 주", at(11, 28, 18), at(11, 28, 20), true);

        List<Schedule> result = scheduleRepository.findUpcomingOperationSchedules(groupTerm.getId(), now, 2);

        assertThat(result).extracting(Schedule::getId).containsExactly(ongoing.getId(), later.getId())
                .doesNotContain(latest.getId());
    }

    private LocalDateTime at(int month, int day, int hour) {
        return LocalDateTime.of(2026, month, day, hour, 0);
    }

    private Schedule persistSchedule(GroupTerm term, String title, LocalDateTime startAt, LocalDateTime endAt,
                                     boolean hasOperationSpace) {
        Schedule schedule = persistSchedule(term, null, title);
        ReflectionTestUtils.setField(schedule, "startAt", startAt);
        ReflectionTestUtils.setField(schedule, "endAt", endAt);
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", hasOperationSpace);
        return em.persistAndFlush(schedule);
    }

    private GroupTerm persistGroupTerm(ClubGroup group, String name) {
        GroupTerm term = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(term, "group", group);
        ReflectionTestUtils.setField(term, "name", name);
        return em.persistAndFlush(term);
    }

    private ScheduleCategory persistCategory(GroupTerm term, String name) {
        ScheduleCategory category = BeanUtils.instantiateClass(ScheduleCategory.class);
        ReflectionTestUtils.setField(category, "groupTerm", term);
        ReflectionTestUtils.setField(category, "name", name);
        return em.persistAndFlush(category);
    }

    private Schedule persistSchedule(GroupTerm term, ScheduleCategory category, String title) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "groupTerm", term);
        ReflectionTestUtils.setField(schedule, "scheduleCategory", category);
        ReflectionTestUtils.setField(schedule, "title", title);
        ReflectionTestUtils.setField(schedule, "color", "RED");
        ReflectionTestUtils.setField(schedule, "isAllDay", false);
        ReflectionTestUtils.setField(schedule, "startAt", LocalDateTime.of(2026, 11, 20, 18, 0));
        ReflectionTestUtils.setField(schedule, "endAt", LocalDateTime.of(2026, 11, 20, 22, 0));
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", true);
        return em.persistAndFlush(schedule);
    }
}
