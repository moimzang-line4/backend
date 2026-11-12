package com.moimzang.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDateTime;
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
