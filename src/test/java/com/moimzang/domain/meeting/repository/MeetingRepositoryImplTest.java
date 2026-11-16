package com.moimzang.domain.meeting.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.MeetingStatus;
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

// 이 프로젝트 최초의 Repository 슬라이스 테스트다 — QueryDSL 동적 조건·cursor tiebreaker는
// Mockito 단위 테스트로는 검증할 수 없는 영역이라 실제 MySQL로 검증한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class MeetingRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private MeetingRepository meetingRepository;

    private GroupTerm groupTerm;
    private MeetingCategory category;

    @BeforeEach
    void setUp() {
        ClubGroup clubGroup = persistClubGroup("테스트 동아리");
        groupTerm = persistGroupTerm(clubGroup, "26기");
        category = persistCategory(groupTerm, "운영진 회의");
    }

    @Test
    void search_필터없이_meetingDate_내림차순으로_정렬한다() {
        Meeting old = persistMeeting("1차 회의", LocalDate.of(2026, 10, 1), null, null);
        Meeting recent = persistMeeting("3차 회의", LocalDate.of(2026, 11, 7), null, null);
        Meeting middle = persistMeeting("2차 회의", LocalDate.of(2026, 10, 20), null, null);

        List<Meeting> result = meetingRepository.search(groupTerm.getId(), null, null, null, null, null, null, 10);

        assertThat(result).extracting(Meeting::getId)
                .containsExactly(recent.getId(), middle.getId(), old.getId());
    }

    @Test
    void search_status로_필터링하면_해당_상태의_회의만_반환한다() {
        Meeting inProgress = persistMeeting("진행중 회의", LocalDate.of(2026, 11, 7), null, null);
        Meeting completed = persistMeeting("완료 회의", LocalDate.of(2026, 11, 6), null, null);
        ReflectionTestUtils.setField(completed, "status", MeetingStatus.COMPLETED);
        em.persistAndFlush(completed);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), MeetingStatus.COMPLETED, null, null, null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(completed.getId());
        assertThat(result).extracting(Meeting::getId).doesNotContain(inProgress.getId());
    }

    @Test
    void search_categoryId로_필터링하면_해당_카테고리의_회의만_반환한다() {
        MeetingCategory otherCategory = persistCategory(groupTerm, "홍보팀 회의");
        Meeting target = persistMeeting("운영진 회의", LocalDate.of(2026, 11, 7), null, null);
        Meeting other = Meeting.create(groupTerm, null, otherCategory, "홍보팀 회의", LocalDate.of(2026, 11, 6), null);
        em.persistAndFlush(other);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, category.getId(), null, null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(target.getId());
    }

    @Test
    void search_keyword가_title_attendees_중_하나라도_일치하면_반환한다() {
        Meeting byTitle = persistMeeting("정기 운영진 회의", LocalDate.of(2026, 11, 7), null);
        Meeting byAttendees = persistMeeting("2차 회의", LocalDate.of(2026, 11, 5), "운영진 전원");
        Meeting notMatched = persistMeeting("무관한 회의", LocalDate.of(2026, 11, 4), "홍보팀");

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, "운영진", null, null, null, 10);

        assertThat(result).extracting(Meeting::getId)
                .containsExactlyInAnyOrder(byTitle.getId(), byAttendees.getId())
                .doesNotContain(notMatched.getId());
    }

    @Test
    void search_동일한_meetingDate에서는_cursor가_id로_다음_페이지를_구분한다() {
        LocalDate sameDate = LocalDate.of(2026, 11, 7);
        Meeting first = persistMeeting("1번째", sameDate, null, null);
        Meeting second = persistMeeting("2번째", sameDate, null, null);
        Meeting third = persistMeeting("3번째", sameDate, null, null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, null, null, sameDate, second.getId(), 10);

        // (date, id) 내림차순이므로 cursor(second) 다음은 id가 더 작은 first만 남는다
        assertThat(result).extracting(Meeting::getId).containsExactly(first.getId());
    }

    @Test
    void search_cursor는_이전_날짜의_회의까지_포함한다() {
        Meeting newer = persistMeeting("최근 회의", LocalDate.of(2026, 11, 7), null, null);
        Meeting older = persistMeeting("이전 회의", LocalDate.of(2026, 11, 6), null, null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, null, null, newer.getMeetingDate(), newer.getId(), 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(older.getId());
    }

    // 줄바꿈이 섞인 제목이 저장돼 전체 문장으로 검색이 안 되던 회귀를 막는다.
    // 서비스가 저장 전에 한 줄로 펴므로 DB에는 공백으로 들어간다
    @Test
    void search_한_줄로_펴진_제목은_전체_문장_keyword로_걸린다() {
        Meeting flattened = persistMeeting("가을 정기공연 운영진 회의", LocalDate.of(2026, 11, 7), null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, "가을 정기공연 운영진 회의", null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(flattened.getId());
    }

    @Test
    void search_검색어의_연속_공백도_접어서_저장값과_맞춘다() {
        // 저장값은 한 칸으로 접히므로(decisions #84) 검색어만 안 접으면 0건이 된다
        Meeting target = persistMeeting("가을 정기공연 운영진 회의", LocalDate.of(2026, 11, 7), null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, "가을  정기공연   운영진 회의", null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(target.getId());
    }

    @Test
    void search_검색어의_앞뒤_공백은_무시한다() {
        Meeting target = persistMeeting("가을 정기공연", LocalDate.of(2026, 11, 7), null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, "  가을 정기공연  ", null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(target.getId());
    }

    @Test
    void search_scheduleId를_주면_그_일정에_속한_회의만_반환한다() {
        Schedule schedule = persistSchedule("가을 정기공연");
        Meeting owned = persistMeeting("일정에서 만든 회의", LocalDate.of(2026, 11, 7), null, schedule);
        persistMeeting("일정과 무관한 회의", LocalDate.of(2026, 11, 8), null, null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, null, schedule.getId(), null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(owned.getId());
    }

    @Test
    void search_scheduleId가_없으면_일정_소속_회의도_함께_반환한다() {
        Schedule schedule = persistSchedule("가을 정기공연");
        Meeting owned = persistMeeting("일정에서 만든 회의", LocalDate.of(2026, 11, 7), null, schedule);
        Meeting free = persistMeeting("일정과 무관한 회의", LocalDate.of(2026, 11, 8), null, null);

        List<Meeting> result = meetingRepository.search(
                groupTerm.getId(), null, null, null, null, null, null, 10);

        assertThat(result).extracting(Meeting::getId).containsExactly(free.getId(), owned.getId());
    }

    @Test
    void findByScheduleForAssistant_그_일정에_속한_회의만_최근_순서로_반환한다() {
        Schedule schedule = persistSchedule("가을 정기공연");
        Schedule other = persistSchedule("MT");
        Meeting older = persistMeeting("1차 회의", LocalDate.of(2026, 10, 1), null, schedule);
        Meeting newer = persistMeeting("2차 회의", LocalDate.of(2026, 11, 7), null, schedule);
        persistMeeting("MT 회의", LocalDate.of(2026, 11, 8), null, other);
        persistMeeting("일정과 무관한 회의", LocalDate.of(2026, 11, 9), null, null);

        List<Meeting> result = meetingRepository.findByScheduleForAssistant(schedule.getId());

        assertThat(result).extracting(Meeting::getId).containsExactly(newer.getId(), older.getId());
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

    private MeetingCategory persistCategory(GroupTerm term, String name) {
        MeetingCategory newCategory = BeanUtils.instantiateClass(MeetingCategory.class);
        ReflectionTestUtils.setField(newCategory, "groupTerm", term);
        ReflectionTestUtils.setField(newCategory, "name", name);
        return em.persistAndFlush(newCategory);
    }

    @Test
    void count_cursor와_무관하게_필터에_맞는_전체_건수를_센다() {
        persistMeeting("1차 회의", LocalDate.of(2026, 10, 1), null, null);
        persistMeeting("2차 회의", LocalDate.of(2026, 10, 20), null, null);
        Meeting completed = persistMeeting("완료 회의", LocalDate.of(2026, 11, 7), null, null);
        ReflectionTestUtils.setField(completed, "status", MeetingStatus.COMPLETED);
        em.persistAndFlush(completed);

        assertThat(meetingRepository.count(groupTerm.getId(), null, null, null, null)).isEqualTo(3);
        assertThat(meetingRepository.count(groupTerm.getId(), MeetingStatus.COMPLETED, null, null, null))
                .isEqualTo(1);
        assertThat(meetingRepository.count(groupTerm.getId(), null, null, "2차", null)).isEqualTo(1);
    }

    @Test
    void count_다른_기수의_회의는_세지_않는다() {
        persistMeeting("우리 기수 회의", LocalDate.of(2026, 11, 7), null, null);
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        MeetingCategory otherCategory = persistCategory(otherTerm, "다른 기수 카테고리");
        em.persistAndFlush(Meeting.create(otherTerm, null, otherCategory, "다른 기수 회의",
                LocalDate.of(2026, 11, 7), null));

        assertThat(meetingRepository.count(groupTerm.getId(), null, null, null, null)).isEqualTo(1);
    }

    private Meeting persistMeeting(String title, LocalDate date, String attendees) {
        return persistMeeting(title, date, attendees, null);
    }

    private Meeting persistMeeting(String title, LocalDate date, String attendees, Schedule schedule) {
        Meeting meeting = Meeting.create(groupTerm, schedule, category, title, date, attendees);
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
}
