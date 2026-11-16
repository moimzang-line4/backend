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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// findOrderedAgendas·findLastAgendas는 파생 쿼리명이 길어져 JPQL(@Query)로 바꾼 것이라 직접 검증한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class MeetingAgendaRepositoryTest {

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
    void findOrderedAgendas_안건을_orderNo_오름차순으로_반환한다() {
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건2", 1));
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건1", 0));

        List<MeetingAgenda> result = meetingAgendaRepository.findOrderedAgendas(meeting.getId());

        assertThat(result).extracting(MeetingAgenda::getContent).containsExactly("안건1", "안건2");
    }

    @Test
    void findOrderedAgendas_안건이_없으면_빈_목록을_반환한다() {
        Meeting meeting = persistMeeting("안건 없는 회의");

        List<MeetingAgenda> result = meetingAgendaRepository.findOrderedAgendas(meeting.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findLastAgendas_orderNo가_가장_큰_안건을_반환한다() {
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건2", 1));
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건3", 2));

        List<MeetingAgenda> result = meetingAgendaRepository.findLastAgendas(meeting.getId(), PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("안건3");
    }

    @Test
    void findLastAgendas_안건이_없으면_빈_목록을_반환한다() {
        Meeting meeting = persistMeeting("안건 없는 회의");

        List<MeetingAgenda> result = meetingAgendaRepository.findLastAgendas(meeting.getId(), PageRequest.of(0, 1));

        assertThat(result).isEmpty();
    }

    @Test
    void findLastAgendas_다른_회의의_안건은_섞이지_않는다() {
        Meeting meetingA = persistMeeting("A 회의");
        Meeting meetingB = persistMeeting("B 회의");
        em.persistAndFlush(MeetingAgenda.create(meetingA, "A 안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meetingB, "B 안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meetingB, "B 안건2", 1));

        List<MeetingAgenda> result = meetingAgendaRepository.findLastAgendas(meetingA.getId(), PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getContent()).isEqualTo("A 안건1");
    }

    @Test
    void findLastAgendas_orderNo가_중복돼도_예외_없이_1건만_반환한다() {
        // 동시 추가로 orderNo가 중복됐다고 가정(유니크 제약이 없어 실제로 생길 수 있는 상황)
        Meeting meeting = persistMeeting("회의");
        em.persistAndFlush(MeetingAgenda.create(meeting, "안건1", 0));
        em.persistAndFlush(MeetingAgenda.create(meeting, "동시추가된 안건A", 1));
        em.persistAndFlush(MeetingAgenda.create(meeting, "동시추가된 안건B", 1));

        List<MeetingAgenda> result = meetingAgendaRepository.findLastAgendas(meeting.getId(), PageRequest.of(0, 1));

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getOrderNo()).isEqualTo(1);
    }

    private Meeting persistMeeting(String title) {
        Meeting meeting = Meeting.create(groupTerm, null, category, title, LocalDate.of(2026, 11, 7), null);
        return em.persistAndFlush(meeting);
    }
}
