package com.moimzang.domain.schedule.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.budget.entity.LedgerEntry;
import com.moimzang.domain.budget.enums.LedgerType;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.domain.settlement.entity.SettlementTarget;
import com.moimzang.domain.settlement.enums.PaymentStatus;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

// 일정 삭제는 FK가 전부 RESTRICT인 여러 테이블을 순서대로 지워야 해서, 순서가 맞는지는 실제 DB에서만 확인된다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class, ScheduleCascadeDeleter.class})
class ScheduleCascadeDeleterTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private ScheduleCascadeDeleter scheduleCascadeDeleter;

    @Autowired
    private ScheduleRepository scheduleRepository;

    private GroupTerm groupTerm;
    private Schedule target;
    private Schedule other;
    private MeetingCategory meetingCategory;

    @BeforeEach
    void setUp() {
        ClubGroup group = em.persist(ClubGroup.create("테스트 동아리"));
        groupTerm = em.persist(GroupTerm.create(group, "26기"));
        meetingCategory = em.persist(MeetingCategory.create(groupTerm, "정기회의"));
        target = em.persist(schedule("가을 정기공연"));
        other = em.persist(schedule("신입생 공연"));
        em.flush();
    }

    @Test
    void 일정에_딸린_데이터를_전부_지워도_FK_에러_없이_일정까지_삭제된다() {
        // 이 일정에서 만든 회의록과 안건
        Meeting ownMeeting = em.persist(meeting(target, "공연 준비 회의"));
        MeetingAgenda ownAgenda = em.persist(MeetingAgenda.create(ownMeeting, "무대 배치", 0));
        // 일정과 무관한 회의록의 안건을 이 일정에 연결
        Meeting independentMeeting = em.persist(meeting(null, "운영진 정기회의"));
        MeetingAgenda linkedAgenda = em.persist(MeetingAgenda.create(independentMeeting, "홍보 계획", 0));
        em.persist(link(target, linkedAgenda));
        em.persist(link(target, ownAgenda));

        em.persist(OperationNote.create(target, NoteType.TODO, "포스터", null, "인쇄"));

        ScheduleAttendee attendee = em.persist(ScheduleAttendee.createOnSite(target, "강서준", "컴퓨터공학과", "4827"));
        ScheduleRound round = em.persist(ScheduleRound.create(target, 1, "1차"));
        em.persist(RoundAttendee.create(round, attendee));
        em.persist(settlementTarget(round, attendee));
        LedgerEntry ledger = em.persist(ledger(round));

        em.persist(File.upload(groupTerm, target, "공연장 계약서", "pdf", 1024L, FileCategory.ACTIVITY_DOC, "key/a"));
        File softDeleted = File.upload(groupTerm, target, "옛 계약서", "pdf", 1024L, FileCategory.ACTIVITY_DOC, "key/b");
        ReflectionTestUtils.setField(softDeleted, "deletedAt", LocalDateTime.of(2026, 11, 1, 10, 0));
        em.persist(softDeleted);
        em.flush();

        scheduleCascadeDeleter.deleteChildren(target.getId());
        scheduleRepository.deleteById(target.getId());
        em.flush();
        em.clear();

        assertThat(count("schedule", "id", target.getId())).isZero();
        assertThat(count("meeting", "schedule_id", target.getId())).isZero();
        assertThat(count("meeting_agenda", "id", ownAgenda.getId())).isZero();
        assertThat(count("schedule_agenda_link", "schedule_id", target.getId())).isZero();
        assertThat(count("operation_note", "schedule_id", target.getId())).isZero();
        assertThat(count("schedule_round", "schedule_id", target.getId())).isZero();
        assertThat(count("schedule_attendee", "schedule_id", target.getId())).isZero();
        assertThat(count("file", "schedule_id", target.getId())).isZero();

        // 연결만 끊기고 원본 회의록·안건은 남는다
        assertThat(count("meeting", "id", independentMeeting.getId())).isEqualTo(1);
        assertThat(count("meeting_agenda", "id", linkedAgenda.getId())).isEqualTo(1);
        // 장부 기록은 남고 차수 참조만 비워진다
        assertThat(count("ledger_entry", "id", ledger.getId())).isEqualTo(1);
        assertThat(em.getEntityManager()
                .createNativeQuery("SELECT schedule_round_id FROM ledger_entry WHERE id = ?")
                .setParameter(1, ledger.getId())
                .getSingleResult()).isNull();
    }

    @Test
    void 다른_일정의_데이터는_건드리지_않는다() {
        Meeting otherMeeting = em.persist(meeting(other, "신입생 공연 회의"));
        em.persist(MeetingAgenda.create(otherMeeting, "곡 순서", 0));
        em.persist(OperationNote.create(other, NoteType.GENERAL, "대관", null, "소강당"));
        em.persist(ScheduleRound.create(other, 1, "1차"));
        em.persist(File.upload(groupTerm, other, "신입생 공연 포스터", "png", 2048L, FileCategory.PHOTO, "key/c"));
        em.flush();

        scheduleCascadeDeleter.deleteChildren(target.getId());
        scheduleRepository.deleteById(target.getId());
        em.flush();
        em.clear();

        assertThat(count("schedule", "id", other.getId())).isEqualTo(1);
        assertThat(count("meeting", "schedule_id", other.getId())).isEqualTo(1);
        assertThat(count("operation_note", "schedule_id", other.getId())).isEqualTo(1);
        assertThat(count("schedule_round", "schedule_id", other.getId())).isEqualTo(1);
        assertThat(count("file", "schedule_id", other.getId())).isEqualTo(1);
    }

    private long count(String table, String column, Long value) {
        Number result = (Number) em.getEntityManager()
                .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?")
                .setParameter(1, value)
                .getSingleResult();
        return result.longValue();
    }

    private Schedule schedule(String title) {
        return Schedule.create(groupTerm, null, title, "RED", false, LocalDateTime.of(2026, 11, 14, 17, 0),
                LocalDateTime.of(2026, 11, 14, 21, 0), "얼라이브홀", null, true);
    }

    private Meeting meeting(Schedule schedule, String title) {
        return Meeting.create(groupTerm, schedule, meetingCategory, title, LocalDate.of(2026, 11, 7), null, null);
    }

    private ScheduleAgendaLink link(Schedule schedule, MeetingAgenda agenda) {
        ScheduleAgendaLink link = BeanUtils.instantiateClass(ScheduleAgendaLink.class);
        ReflectionTestUtils.setField(link, "schedule", schedule);
        ReflectionTestUtils.setField(link, "meetingAgenda", agenda);
        return link;
    }

    private SettlementTarget settlementTarget(ScheduleRound round, ScheduleAttendee attendee) {
        SettlementTarget settlementTarget = BeanUtils.instantiateClass(SettlementTarget.class);
        ReflectionTestUtils.setField(settlementTarget, "scheduleRound", round);
        ReflectionTestUtils.setField(settlementTarget, "scheduleAttendee", attendee);
        ReflectionTestUtils.setField(settlementTarget, "billedAmount", 4000);
        ReflectionTestUtils.setField(settlementTarget, "paymentStatus", PaymentStatus.PAID);
        return settlementTarget;
    }

    private LedgerEntry ledger(ScheduleRound round) {
        LedgerEntry entry = BeanUtils.instantiateClass(LedgerEntry.class);
        ReflectionTestUtils.setField(entry, "groupTerm", groupTerm);
        ReflectionTestUtils.setField(entry, "scheduleRound", round);
        ReflectionTestUtils.setField(entry, "type", LedgerType.EXPENSE);
        ReflectionTestUtils.setField(entry, "itemName", "뒤풀이 영수증");
        ReflectionTestUtils.setField(entry, "amount", 89000);
        ReflectionTestUtils.setField(entry, "entryDate", LocalDate.of(2026, 11, 14));
        return entry;
    }
}
