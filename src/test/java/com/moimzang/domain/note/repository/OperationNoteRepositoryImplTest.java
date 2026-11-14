package com.moimzang.domain.note.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
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

// 메모는 일정을 거쳐야 기수 조건이 걸리고 검색어가 3개 컬럼을 OR로 보므로 실제 DB로 확인한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class OperationNoteRepositoryImplTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private OperationNoteRepository operationNoteRepository;

    private GroupTerm groupTerm;
    private Schedule schedule;

    @BeforeEach
    void setUp() {
        groupTerm = persistGroupTerm(persistClubGroup("테스트 동아리"), "26기");
        schedule = persistSchedule(groupTerm, "가을 정기공연");
    }

    @Test
    void searchForAssistant_제목_내용_담당자_중_하나라도_맞으면_반환한다() {
        OperationNote byTitle = persistNote(schedule, "티켓 인쇄", "150장 맡기기", "김서경");
        OperationNote byContent = persistNote(schedule, "대관 확인", "티켓 부스 자리 확보", "이수민");
        OperationNote byAssignee = persistNote(schedule, "포스터", "디자인 시안", "티켓팀 강민준");
        OperationNote notMatched = persistNote(schedule, "뒤풀이 예약", "삼겹살집 20인", "홍길동");

        List<OperationNote> result =
                operationNoteRepository.searchForAssistant(groupTerm.getId(), "티켓", null, null, 10);

        assertThat(result).extracting(OperationNote::getId)
                .containsExactlyInAnyOrder(byTitle.getId(), byContent.getId(), byAssignee.getId())
                .doesNotContain(notMatched.getId());
    }

    @Test
    void searchForAssistant_담당자가_없는_메모도_검색에서_빠지지_않는다() {
        OperationNote noAssignee = persistNote(schedule, "티켓 인쇄", "150장 맡기기", null);

        List<OperationNote> result =
                operationNoteRepository.searchForAssistant(groupTerm.getId(), "티켓", null, null, 10);

        // assignee가 NULL이면 OR 비교에서 통째로 빠질 수 있어 제목 일치로 잡히는지 확인한다
        assertThat(result).extracting(OperationNote::getId).containsExactly(noAssignee.getId());
    }

    @Test
    void searchForAssistant_scheduleId를_주면_그_일정의_메모만_반환한다() {
        OperationNote mine = persistNote(schedule, "티켓 인쇄", "150장 맡기기", "김서경");
        Schedule other = persistSchedule(groupTerm, "가을 MT");
        persistNote(other, "버스 예약", "45인승 1대", "이수민");

        List<OperationNote> result =
                operationNoteRepository.searchForAssistant(groupTerm.getId(), null, schedule.getId(), null, 10);

        assertThat(result).extracting(OperationNote::getId).containsExactly(mine.getId());
    }

    @Test
    void searchForAssistant_다른_기수의_메모는_반환하지_않는다() {
        OperationNote mine = persistNote(schedule, "티켓 인쇄", "150장 맡기기", "김서경");
        GroupTerm otherTerm = persistGroupTerm(persistClubGroup("다른 동아리"), "25기");
        persistNote(persistSchedule(otherTerm, "다른 기수 공연"), "남의 메모", "내용", "남의 담당자");

        List<OperationNote> result =
                operationNoteRepository.searchForAssistant(groupTerm.getId(), null, null, null, 10);

        assertThat(result).extracting(OperationNote::getId).containsExactly(mine.getId());
    }

    @Test
    void countForAssistant_같은_조건의_전체_건수를_센다() {
        persistNote(schedule, "티켓 인쇄", "150장 맡기기", "김서경");
        persistNote(schedule, "대관 확인", "대강당 예약 완료", "이수민");

        assertThat(operationNoteRepository.countForAssistant(groupTerm.getId(), null, null, null)).isEqualTo(2);
        assertThat(operationNoteRepository.countForAssistant(groupTerm.getId(), "티켓", null, null)).isEqualTo(1);
    }

    @Test
    void search_그_일정의_메모만_최신_작성순으로_반환한다() {
        OperationNote first = persistNote(schedule, "티켓 인쇄", "150장", "김서경");
        OperationNote second = persistNote(schedule, "대관 확인", "대강당", "이수민");
        persistNote(persistSchedule(groupTerm, "가을 MT"), "버스 예약", "45인승", "홍길동");
        setCreatedAt(first, LocalDateTime.of(2026, 11, 1, 10, 0));
        setCreatedAt(second, LocalDateTime.of(2026, 11, 2, 10, 0));

        List<OperationNote> result = operationNoteRepository.search(schedule.getId(), null, null, null, null, 10);

        assertThat(result).extracting(OperationNote::getId).containsExactly(second.getId(), first.getId());
    }

    @Test
    void search_유형과_검색어_조건을_함께_건다() {
        OperationNote todo = persistNote(schedule, "포스터 인쇄", "A2 30장", "김가은");
        ReflectionTestUtils.setField(todo, "type", NoteType.TODO);
        em.persistAndFlush(todo);
        persistNote(schedule, "포스터 시안", "색감 결정", "김가은");
        OperationNote otherTodo = persistNote(schedule, "대관 확인", "대강당", "이수민");
        ReflectionTestUtils.setField(otherTodo, "type", NoteType.TODO);
        em.persistAndFlush(otherTodo);

        List<OperationNote> result =
                operationNoteRepository.search(schedule.getId(), NoteType.TODO, "포스터", null, null, 10);

        assertThat(result).extracting(OperationNote::getId).containsExactly(todo.getId());
    }

    @Test
    void countForList_목록과_같은_유형_검색어_조건으로_그_일정의_전체_개수를_센다() {
        OperationNote todo = persistNote(schedule, "포스터 인쇄", "A2 30장", "김가은");
        ReflectionTestUtils.setField(todo, "type", NoteType.TODO);
        em.persistAndFlush(todo);
        persistNote(schedule, "포스터 시안", "색감 결정", "김가은");
        persistNote(schedule, "대관 확인", "대강당", "이수민");

        assertThat(operationNoteRepository.countForList(schedule.getId(), null, null)).isEqualTo(3);
        assertThat(operationNoteRepository.countForList(schedule.getId(), null, "포스터")).isEqualTo(2);
        assertThat(operationNoteRepository.countForList(schedule.getId(), NoteType.TODO, "포스터")).isEqualTo(1);
    }

    @Test
    void search_cursor는_그_지점_이후부터_반환하고_같은_시각이면_id로_가른다() {
        LocalDateTime sameTime = LocalDateTime.of(2026, 11, 3, 10, 0);
        OperationNote a = persistNote(schedule, "A", "내용", null);
        OperationNote b = persistNote(schedule, "B", "내용", null);
        OperationNote c = persistNote(schedule, "C", "내용", null);
        setCreatedAt(a, sameTime);
        setCreatedAt(b, sameTime);
        setCreatedAt(c, sameTime.plusHours(1));

        // cursor는 서비스가 DB에서 읽어온 값으로 만들기 때문에 테스트도 다시 조회한 값을 쓴다
        OperationNote reloadedB = operationNoteRepository.findById(b.getId()).orElseThrow();

        List<OperationNote> result = operationNoteRepository.search(
                schedule.getId(), null, null, reloadedB.getCreatedAt(), reloadedB.getId(), 10);

        assertThat(result).extracting(OperationNote::getId).containsExactly(a.getId());
    }

    // 작성 시각은 감사 기능이 자동으로 넣어서, 정렬을 확인하려면 저장 후 직접 덮어쓴다
    private void setCreatedAt(OperationNote note, LocalDateTime createdAt) {
        em.getEntityManager()
                .createNativeQuery("UPDATE operation_note SET created_at = ? WHERE id = ?")
                .setParameter(1, createdAt)
                .setParameter(2, note.getId())
                .executeUpdate();
        em.clear();
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

    private OperationNote persistNote(Schedule target, String title, String content, String assignee) {
        OperationNote note = BeanUtils.instantiateClass(OperationNote.class);
        ReflectionTestUtils.setField(note, "schedule", target);
        ReflectionTestUtils.setField(note, "type", NoteType.GENERAL);
        ReflectionTestUtils.setField(note, "title", title);
        ReflectionTestUtils.setField(note, "content", content);
        ReflectionTestUtils.setField(note, "assignee", assignee);
        return em.persistAndFlush(note);
    }
}
