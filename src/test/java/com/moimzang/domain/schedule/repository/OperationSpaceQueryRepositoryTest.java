package com.moimzang.domain.schedule.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.schedule.dto.response.OperationSpaceItemResponse;
import com.moimzang.domain.schedule.dto.response.OperationSpaceListResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.enums.OperationSpaceSort;
import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceType;
import com.moimzang.domain.schedule.service.OperationSpaceService;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

// 두 테이블을 따로 조회해 합치는 구조라, 페이지를 넘겨도 빠지거나 겹치는 항목이 없는지는 실제 DB로 끝까지 넘겨 봐야 확인된다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class, OperationSpaceQueryRepository.class, OperationSpaceService.class})
class OperationSpaceQueryRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OperationSpaceService operationSpaceService;

    private GroupTerm groupTerm;
    private MeetingCategory category;

    @BeforeEach
    void setUp() {
        groupTerm = em.persist(GroupTerm.create(em.persist(ClubGroup.create("테스트 동아리")), "26기"));
        category = em.persist(MeetingCategory.create(groupTerm, "정기회의"));
    }

    @Test
    void 진행_중_목록은_운영_공간을_켠_끝나지_않은_일정과_진행_중인_회의만_담는다() {
        LocalDateTime future = LocalDateTime.now().plusDays(10);
        Schedule ongoing = persistSchedule("다가오는 공연", future, true);
        persistSchedule("운영 공간 없음", future, false);
        persistSchedule("끝난 공연", LocalDateTime.now().minusDays(10), true);
        Meeting ongoingMeeting = persistMeeting("진행 중 회의", LocalDate.now(), MeetingStatus.IN_PROGRESS);
        persistMeeting("끝난 회의", LocalDate.now(), MeetingStatus.COMPLETED);
        persistOtherTermSchedule();
        em.flush();

        OperationSpaceListResponse response = operationSpaceService.listOperationSpaces(groupTerm.getId(),
                OperationSpaceStatus.ONGOING, null, null, null, null, null);

        assertThat(response.getItems()).extracting(OperationSpaceItemResponse::getId)
                .containsExactlyInAnyOrder(ongoing.getId(), ongoingMeeting.getId());
        assertThat(response.getTotalCount()).isEqualTo(2L);
    }

    @Test
    void 지난_기록에서_검색어와_유형으로_거른다() {
        LocalDateTime past = LocalDateTime.now().minusDays(10);
        Schedule pastConcert = persistSchedule("가을 공연", past, true);
        persistSchedule("가을 MT", past, true);
        persistMeeting("공연 회의", LocalDate.now(), MeetingStatus.COMPLETED);
        em.flush();

        OperationSpaceListResponse response = operationSpaceService.listOperationSpaces(groupTerm.getId(),
                OperationSpaceStatus.PAST, OperationSpaceType.SCHEDULE, "공연", null, null, null);

        assertThat(response.getItems()).extracting(OperationSpaceItemResponse::getId)
                .containsExactly(pastConcert.getId());
        assertThat(response.getTotalCount()).isEqualTo(1L);
    }

    @Test
    void 최신순으로_페이지를_끝까지_넘기면_빠짐없이_겹치지_않게_나온다() {
        LocalDate base = LocalDate.now().plusDays(30);
        // 같은 날짜의 회의(0시)와 일정(17시)·0시 일정이 섞여 키가 겹치는 경우까지 만든다
        persistSchedule("일정 A", base.atTime(17, 0), true);
        persistSchedule("일정 B", base.atStartOfDay(), true);
        persistMeeting("회의 C", base, MeetingStatus.IN_PROGRESS);
        persistMeeting("회의 D", base.minusDays(1), MeetingStatus.IN_PROGRESS);
        persistSchedule("일정 E", base.minusDays(1).atTime(9, 0), true);
        persistMeeting("회의 F", base.plusDays(1), MeetingStatus.IN_PROGRESS);
        em.flush();

        List<String> titles = pageThrough(OperationSpaceSort.LATEST, 2);
        OperationSpaceListResponse firstPage = operationSpaceService.listOperationSpaces(groupTerm.getId(),
                OperationSpaceStatus.ONGOING, null, null, OperationSpaceSort.LATEST, null, 2);

        // 첫 페이지가 2건이어도 전체 수는 6
        assertThat(firstPage.getTotalCount()).isEqualTo(6L);
        // 날짜 키 내림차순, 키가 같으면 회의(유형 순서 1)가 일정(0)보다 먼저
        assertThat(titles).containsExactly("회의 F", "일정 A", "회의 C", "일정 B", "일정 E", "회의 D");
    }

    @Test
    void 다가오는순과_이름순도_페이지를_넘겨도_순서가_유지된다() {
        LocalDate base = LocalDate.now().plusDays(30);
        persistSchedule("다 공연", base.atTime(17, 0), true);
        persistMeeting("가 회의", base, MeetingStatus.IN_PROGRESS);
        persistSchedule("나 공연", base.plusDays(2).atTime(10, 0), true);
        persistMeeting("라 회의", base.plusDays(1), MeetingStatus.IN_PROGRESS);
        em.flush();

        assertThat(pageThrough(OperationSpaceSort.UPCOMING, 1))
                .containsExactly("가 회의", "다 공연", "라 회의", "나 공연");
        assertThat(pageThrough(OperationSpaceSort.NAME, 1))
                .containsExactly("가 회의", "나 공연", "다 공연", "라 회의");
    }

    private List<String> pageThrough(OperationSpaceSort sort, int size) {
        List<String> titles = new ArrayList<>();
        String cursor = null;
        do {
            OperationSpaceListResponse page = operationSpaceService.listOperationSpaces(groupTerm.getId(),
                    OperationSpaceStatus.ONGOING, null, null, sort, cursor, size);
            page.getItems().forEach(item -> titles.add(item.getTitle()));
            cursor = page.getNextCursor();
        } while (cursor != null);
        return titles;
    }

    private Schedule persistSchedule(String title, LocalDateTime startAt, boolean hasOperationSpace) {
        return em.persist(Schedule.create(groupTerm, null, title, "RED", false, startAt, startAt.plusHours(3),
                null, null, hasOperationSpace));
    }

    private Meeting persistMeeting(String title, LocalDate date, MeetingStatus status) {
        Meeting meeting = Meeting.create(groupTerm, null, category, title, date, null, null);
        ReflectionTestUtils.setField(meeting, "status", status);
        return em.persist(meeting);
    }

    private void persistOtherTermSchedule() {
        GroupTerm otherTerm = em.persist(GroupTerm.create(em.persist(ClubGroup.create("다른 동아리")), "25기"));
        LocalDateTime future = LocalDateTime.now().plusDays(10);
        em.persist(Schedule.create(otherTerm, null, "남의 공연", "RED", false, future, future.plusHours(3),
                null, null, true));
    }
}
