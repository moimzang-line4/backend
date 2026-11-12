package com.moimzang.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
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

// 안건 내용과 회의 정보를 한 번에 가져오는 fetch join이라 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class ScheduleAgendaLinkRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;

    private GroupTerm groupTerm;
    private MeetingCategory meetingCategory;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
        meetingCategory = persistMeetingCategory(groupTerm, "운영진 회의");
        schedule = persistSchedule(groupTerm, "가을 정기공연");
    }

    @Test
    void findByScheduleForAssistant_연결된_안건을_회의_정보와_함께_반환한다() {
        Meeting meeting = persistMeeting("3차 정기회의", LocalDate.of(2026, 11, 10));
        MeetingAgenda agenda = persistAgenda(meeting, "공연 운영 담당 배정", 1);
        persistLink(schedule, agenda);

        List<ScheduleAgendaLink> result =
                scheduleAgendaLinkRepository.findByScheduleForAssistant(schedule.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getMeetingAgenda().getContent()).isEqualTo("공연 운영 담당 배정");
        assertThat(result.get(0).getMeetingAgenda().getMeeting().getTitle()).isEqualTo("3차 정기회의");
    }

    @Test
    void findByScheduleForAssistant_최근_회의의_안건부터_순서대로_반환한다() {
        Meeting older = persistMeeting("1차 정기회의", LocalDate.of(2026, 10, 1));
        Meeting newer = persistMeeting("3차 정기회의", LocalDate.of(2026, 11, 10));
        persistLink(schedule, persistAgenda(older, "예산 확정", 1));
        persistLink(schedule, persistAgenda(newer, "두 번째 안건", 2));
        persistLink(schedule, persistAgenda(newer, "첫 번째 안건", 1));

        List<ScheduleAgendaLink> result =
                scheduleAgendaLinkRepository.findByScheduleForAssistant(schedule.getId());

        assertThat(result).extracting(link -> link.getMeetingAgenda().getContent())
                .containsExactly("첫 번째 안건", "두 번째 안건", "예산 확정");
    }

    @Test
    void findByScheduleForAssistant_다른_일정의_안건은_반환하지_않는다() {
        Schedule otherSchedule = persistSchedule(groupTerm, "MT");
        Meeting meeting = persistMeeting("3차 정기회의", LocalDate.of(2026, 11, 10));
        persistLink(otherSchedule, persistAgenda(meeting, "MT 장소 선정", 1));

        List<ScheduleAgendaLink> result =
                scheduleAgendaLinkRepository.findByScheduleForAssistant(schedule.getId());

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

    private MeetingCategory persistMeetingCategory(GroupTerm term, String name) {
        MeetingCategory category = BeanUtils.instantiateClass(MeetingCategory.class);
        ReflectionTestUtils.setField(category, "groupTerm", term);
        ReflectionTestUtils.setField(category, "name", name);
        return em.persistAndFlush(category);
    }

    private Schedule persistSchedule(GroupTerm term, String title) {
        Schedule newSchedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(newSchedule, "groupTerm", term);
        ReflectionTestUtils.setField(newSchedule, "title", title);
        ReflectionTestUtils.setField(newSchedule, "color", "RED");
        ReflectionTestUtils.setField(newSchedule, "isAllDay", false);
        ReflectionTestUtils.setField(newSchedule, "startAt", LocalDateTime.of(2026, 11, 20, 18, 0));
        ReflectionTestUtils.setField(newSchedule, "endAt", LocalDateTime.of(2026, 11, 20, 22, 0));
        ReflectionTestUtils.setField(newSchedule, "hasOperationSpace", true);
        return em.persistAndFlush(newSchedule);
    }

    private Meeting persistMeeting(String title, LocalDate meetingDate) {
        return em.persistAndFlush(Meeting.create(groupTerm, meetingCategory, title, meetingDate, null, null));
    }

    private MeetingAgenda persistAgenda(Meeting meeting, String content, int orderNo) {
        return em.persistAndFlush(MeetingAgenda.create(meeting, content, orderNo));
    }

    private ScheduleAgendaLink persistLink(Schedule target, MeetingAgenda agenda) {
        ScheduleAgendaLink link = BeanUtils.instantiateClass(ScheduleAgendaLink.class);
        ReflectionTestUtils.setField(link, "schedule", target);
        ReflectionTestUtils.setField(link, "meetingAgenda", agenda);
        return em.persistAndFlush(link);
    }
}
