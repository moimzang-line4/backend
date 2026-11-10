package com.moimzang.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
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

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class MeetingAgendaRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MeetingAgendaRepository meetingAgendaRepository;

    private GroupTerm groupTerm;
    private MeetingCategory category;

    @BeforeEach
    void setUp() {
        ClubGroup clubGroup = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(clubGroup, "name", "테스트 동아리");
        em.persistAndFlush(clubGroup);

        groupTerm = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(groupTerm, "group", clubGroup);
        ReflectionTestUtils.setField(groupTerm, "name", "26기");
        em.persistAndFlush(groupTerm);

        category = BeanUtils.instantiateClass(MeetingCategory.class);
        ReflectionTestUtils.setField(category, "groupTerm", groupTerm);
        ReflectionTestUtils.setField(category, "name", "운영진 회의");
        em.persistAndFlush(category);
    }

    @Test
    void countByMeetingIds_안건이_있는_회의는_개수를_반환한다() {
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건2", 1));

        Map<Long, Long> result = meetingAgendaRepository.countByMeetingIds(List.of(meeting.getId()));

        assertThat(result).containsEntry(meeting.getId(), 2L);
    }

    @Test
    void countByMeetingIds_안건이_없는_회의는_결과_맵에_포함되지_않는다() {
        Meeting meeting = persistMeeting("안건 없는 회의");

        Map<Long, Long> result = meetingAgendaRepository.countByMeetingIds(List.of(meeting.getId()));

        assertThat(result).doesNotContainKey(meeting.getId());
    }

    @Test
    void countByMeetingIds_여러_회의의_안건_개수를_각각_구분해서_반환한다() {
        Meeting meetingA = persistMeeting("A 회의");
        Meeting meetingB = persistMeeting("B 회의");
        em.persistAndFlush(MeetingAgenda.create(meetingA, "A 안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meetingB, "B 안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meetingB, "B 안건2", 1));

        Map<Long, Long> result = meetingAgendaRepository.countByMeetingIds(List.of(meetingA.getId(), meetingB.getId()));

        assertThat(result).containsEntry(meetingA.getId(), 1L);
        assertThat(result).containsEntry(meetingB.getId(), 2L);
    }

    @Test
    void countByMeetingIds_빈_목록을_넘기면_빈_맵을_반환한다() {
        Map<Long, Long> result = meetingAgendaRepository.countByMeetingIds(List.of());

        assertThat(result).isEmpty();
    }

    private Meeting persistMeeting(String title) {
        Meeting meeting = Meeting.create(groupTerm, category, title, LocalDate.of(2026, 11, 7), null, null);
        return em.persistAndFlush(meeting);
    }
}
