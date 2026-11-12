package com.moimzang.domain.attendance.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
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

// AI 비서용 조회(findByScheduleForAssistant)만 다룬다. 같은 Impl의 출석 현황 조회(search)는
// RoundAttendeeRepositoryImplTest가 맡고 있어 파일을 나눴다.
// 차수를 거쳐 일정·기수까지 올라가는 조인이라 경계와 필터를 실제 MySQL로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class RoundAttendeeRepositoryAssistantQueryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private RoundAttendeeRepository roundAttendeeRepository;

    private GroupTerm groupTerm;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
        schedule = persistSchedule(groupTerm, "가을 정기공연");
    }

    @Test
    void findByScheduleForAssistant_차수_번호와_이름순으로_전체_차수를_반환한다() {
        ScheduleRound first = persistRound(schedule, 1);
        ScheduleRound second = persistRound(schedule, 2);
        ScheduleAttendee minjun = persistAttendee(schedule, "강민준");
        ScheduleAttendee gildong = persistAttendee(schedule, "홍길동");
        persistRecord(second, gildong);
        persistRecord(first, gildong);
        persistRecord(first, minjun);

        List<RoundAttendee> result =
                roundAttendeeRepository.findByScheduleForAssistant(groupTerm.getId(), schedule.getId(), null);

        assertThat(result).hasSize(3);
        assertThat(result).extracting(record -> record.getScheduleRound().getRoundNo())
                .containsExactly(1, 1, 2);
        assertThat(result).extracting(record -> record.getScheduleAttendee().getName())
                .containsExactly("강민준", "홍길동", "홍길동");
    }

    @Test
    void findByScheduleForAssistant_roundNo를_주면_그_차수만_반환한다() {
        ScheduleRound first = persistRound(schedule, 1);
        ScheduleRound second = persistRound(schedule, 2);
        ScheduleAttendee minjun = persistAttendee(schedule, "강민준");
        persistRecord(first, minjun);
        persistRecord(second, minjun);

        List<RoundAttendee> result =
                roundAttendeeRepository.findByScheduleForAssistant(groupTerm.getId(), schedule.getId(), 2);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getScheduleRound().getRoundNo()).isEqualTo(2);
    }

    @Test
    void findByScheduleForAssistant_다른_기수의_일정이면_반환하지_않는다() {
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        Schedule otherSchedule = persistSchedule(otherTerm, "다른 기수 공연");
        persistRecord(persistRound(otherSchedule, 1), persistAttendee(otherSchedule, "남의 부원"));

        List<RoundAttendee> result =
                roundAttendeeRepository.findByScheduleForAssistant(groupTerm.getId(), otherSchedule.getId(), null);

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

    private ScheduleRound persistRound(Schedule target, int roundNo) {
        return em.persistAndFlush(ScheduleRound.create(target, roundNo, roundNo + "차"));
    }

    private ScheduleAttendee persistAttendee(Schedule target, String name) {
        return em.persistAndFlush(
                ScheduleAttendee.createOnSite(target, name, "광고홍보학과", "1140"));
    }

    private RoundAttendee persistRecord(ScheduleRound round, ScheduleAttendee attendee) {
        return em.persistAndFlush(RoundAttendee.create(round, attendee));
    }
}
