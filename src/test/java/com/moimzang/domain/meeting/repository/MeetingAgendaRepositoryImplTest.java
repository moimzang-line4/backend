package com.moimzang.domain.meeting.repository;

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

    @Test
    void searchLinkable_어느_일정에도_속하지_않은_안건만_반환한다() {
        Schedule schedule = persistSchedule("가을 정기공연");

        Meeting free = persistMeeting("일정과 무관한 회의");
        MeetingAgenda candidate = em.persistAndFlush(MeetingAgenda.create(free, "관객 참여 이벤트", 0));
        MeetingAgenda alreadyLinked = em.persistAndFlush(MeetingAgenda.create(free, "이미 연결된 안건", 1));
        persistLink(schedule, alreadyLinked);

        // 일정 상세에서 만든 회의록 — 안건 전체가 이미 그 일정에 속해 후보가 아니다
        Meeting owned = persistMeeting("일정에서 만든 회의", schedule);
        em.persistAndFlush(MeetingAgenda.create(owned, "일정 소속 안건", 0));

        List<MeetingAgenda> result =
                meetingAgendaRepository.searchLinkable(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(MeetingAgenda::getId).containsExactly(candidate.getId());
    }

    @Test
    void searchLinkable_keyword는_안건_내용으로_검색한다() {
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "관객 참여 이벤트", 0));
        MeetingAgenda target = em.persistAndFlush(MeetingAgenda.create(meeting, "공연 홍보 계획", 1));

        List<MeetingAgenda> result =
                meetingAgendaRepository.searchLinkable(groupTerm.getId(), "홍보", null, null, 10);

        assertThat(result).extracting(MeetingAgenda::getId).containsExactly(target.getId());
    }

    // 안건 내용은 여러 줄이 정상이라 저장할 때도 검색할 때도 펴지 않는다(decisions #84).
    // 누군가 다른 조건 빌더에 맞춰 contentContains에 정규화를 넣으면 이 테스트가 깨진다
    @Test
    void searchLinkable_안건_내용의_줄바꿈은_펴지_않으므로_그대로_검색된다() {
        Meeting meeting = persistMeeting("회의");
        MeetingAgenda target = em.persistAndFlush(
                MeetingAgenda.create(meeting, "관객 참여 이벤트\n세부 기획", 0));

        List<MeetingAgenda> result = meetingAgendaRepository.searchLinkable(
                groupTerm.getId(), "이벤트\n세부", null, null, 10);

        assertThat(result).extracting(MeetingAgenda::getId).containsExactly(target.getId());
    }

    @Test
    void searchLinkable_최근_회의부터_같은_회의_안에서는_안건_순서대로_반환한다() {
        Meeting older = persistMeeting("이전 회의");
        MeetingAgenda olderFirst = em.persistAndFlush(MeetingAgenda.create(older, "이전 안건1", 0));
        Meeting newer = persistMeeting("최근 회의");
        MeetingAgenda newerFirst = em.persistAndFlush(MeetingAgenda.create(newer, "최근 안건1", 0));
        MeetingAgenda newerSecond = em.persistAndFlush(MeetingAgenda.create(newer, "최근 안건2", 1));

        List<MeetingAgenda> result =
                meetingAgendaRepository.searchLinkable(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(MeetingAgenda::getId)
                .containsExactly(newerFirst.getId(), newerSecond.getId(), olderFirst.getId());
    }

    @Test
    void searchLinkable_cursor_다음_항목부터_이어서_반환한다() {
        Meeting older = persistMeeting("이전 회의");
        MeetingAgenda olderFirst = em.persistAndFlush(MeetingAgenda.create(older, "이전 안건1", 0));
        Meeting newer = persistMeeting("최근 회의");
        MeetingAgenda newerFirst = em.persistAndFlush(MeetingAgenda.create(newer, "최근 안건1", 0));
        MeetingAgenda newerSecond = em.persistAndFlush(MeetingAgenda.create(newer, "최근 안건2", 1));

        // 첫 페이지가 최근 회의의 안건1까지 내려갔다고 보고 그 다음부터 이어 받는다
        List<MeetingAgenda> result = meetingAgendaRepository.searchLinkable(
                groupTerm.getId(), null, newer.getId(), newerFirst.getId(), 10);

        assertThat(result).extracting(MeetingAgenda::getId)
                .containsExactly(newerSecond.getId(), olderFirst.getId());
    }

    @Test
    void searchLinkable_다른_기수의_안건은_반환하지_않는다() {
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "관객 참여 이벤트", 0));

        List<MeetingAgenda> result =
                meetingAgendaRepository.searchLinkable(groupTerm.getId() + 1, null, null, null, 10);

        assertThat(result).isEmpty();
    }

    @Test
    void countLinkable_후보_조건을_그대로_적용한_전체_개수를_반환한다() {
        Schedule schedule = persistSchedule("가을 정기공연");

        Meeting free = persistMeeting("일정과 무관한 회의");
        em.persistAndFlush(MeetingAgenda.create(free, "관객 참여 이벤트", 0));
        persistLink(schedule, em.persistAndFlush(MeetingAgenda.create(free, "이미 연결된 안건", 1)));

        Meeting owned = persistMeeting("일정에서 만든 회의", schedule);
        em.persistAndFlush(MeetingAgenda.create(owned, "일정 소속 안건", 0));

        long result = meetingAgendaRepository.countLinkable(groupTerm.getId(), null);

        assertThat(result).isEqualTo(1L);
    }

    private Meeting persistMeeting(String title) {
        return persistMeeting(title, null);
    }

    private Meeting persistMeeting(String title, Schedule schedule) {
        Meeting meeting = Meeting.create(groupTerm, schedule, category, title, LocalDate.of(2026, 11, 7), null);
        return em.persistAndFlush(meeting);
    }

    private Schedule persistSchedule(String title) {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "groupTerm", groupTerm);
        ReflectionTestUtils.setField(schedule, "title", title);
        ReflectionTestUtils.setField(schedule, "color", "RED");
        ReflectionTestUtils.setField(schedule, "isAllDay", false);
        ReflectionTestUtils.setField(schedule, "startAt", LocalDateTime.of(2026, 11, 20, 18, 0));
        ReflectionTestUtils.setField(schedule, "endAt", LocalDateTime.of(2026, 11, 20, 22, 0));
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", true);
        return em.persistAndFlush(schedule);
    }

    private ScheduleAgendaLink persistLink(Schedule schedule, MeetingAgenda agenda) {
        ScheduleAgendaLink link = BeanUtils.instantiateClass(ScheduleAgendaLink.class);
        ReflectionTestUtils.setField(link, "schedule", schedule);
        ReflectionTestUtils.setField(link, "meetingAgenda", agenda);
        return em.persistAndFlush(link);
    }
}
