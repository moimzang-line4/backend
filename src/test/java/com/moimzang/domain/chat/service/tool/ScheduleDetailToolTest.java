package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.enums.AttendeeSource;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.BeanUtils;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class ScheduleDetailToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private ScheduleRoundRepository scheduleRoundRepository;

    @Mock
    private ScheduleAttendeeRepository scheduleAttendeeRepository;

    @Mock
    private ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private ScheduleDetailTool tool;

    @Test
    void execute_차수의_영수증과_입금_계좌를_내려준다() throws Exception {
        givenSchedule();
        given(scheduleRoundRepository.findOrderedRounds(10L))
                .willReturn(List.of(round(1, "1차", "삼겹살집", 320000, 100000)));

        JsonNode round = execute("{\"scheduleId\":10}").get("rounds").get(0);

        assertThat(round.get("roundNo").asInt()).isEqualTo(1);
        assertThat(round.get("storeName").asText()).isEqualTo("삼겹살집");
        assertThat(round.get("receiptAmount").asInt()).isEqualTo(320000);
        assertThat(round.get("clubSubsidyAmount").asInt()).isEqualTo(100000);
        assertThat(round.get("bankName").asText()).isEqualTo("국민은행");
        assertThat(round.get("accountNumber").asText()).isEqualTo("123456-78-901234");
        assertThat(round.get("accountHolder").asText()).isEqualTo("김총무");
        // 영수증 이미지는 답변 문장에 쓸 수 없는 값이라 URL 대신 등록 여부만 내려준다
        assertThat(round.get("hasReceiptImage").asBoolean()).isTrue();
        assertThat(round.has("imageUrl")).isFalse();
    }

    @Test
    void execute_차수_영수증과_지원금의_합계를_내려준다() throws Exception {
        givenSchedule();
        given(scheduleRoundRepository.findOrderedRounds(10L))
                .willReturn(List.of(
                        round(1, "1차", "삼겹살집", 320000, 100000),
                        round(2, "2차", "코인노래방", 135000, 0)));

        JsonNode result = execute("{\"scheduleId\":10}");

        // 차수 금액을 AI가 직접 더하면 틀린다 — 합계는 백엔드가 확정해 내려준다
        assertThat(result.get("totalReceiptAmount").asInt()).isEqualTo(455000);
        assertThat(result.get("totalSubsidyAmount").asInt()).isEqualTo(100000);
    }

    @Test
    void execute_영수증을_아직_안_올린_차수는_금액을_0으로_친다() throws Exception {
        givenSchedule();
        ScheduleRound noReceipt = BeanUtils.instantiateClass(ScheduleRound.class);
        ReflectionTestUtils.setField(noReceipt, "roundNo", 2);
        given(scheduleRoundRepository.findOrderedRounds(10L))
                .willReturn(List.of(round(1, "1차", "삼겹살집", 320000, 100000), noReceipt));

        JsonNode result = execute("{\"scheduleId\":10}");

        // totalAmount가 nullable이라 그대로 더하면 NPE가 난다
        assertThat(result.get("totalReceiptAmount").asInt()).isEqualTo(320000);
        assertThat(result.get("rounds").get(1).get("receiptAmount").isNull()).isTrue();
    }

    @Test
    void execute_참가자와_연결된_안건과_첨부_자료를_함께_내려준다() throws Exception {
        givenSchedule();
        given(scheduleAttendeeRepository.findByScheduleIdOrderByNameAsc(10L))
                .willReturn(List.of(attendee(401L, "강민준", "광고홍보학과", "1140")));
        given(scheduleAgendaLinkRepository.findByScheduleForAssistant(10L))
                .willReturn(List.of(agendaLink(900L, "3차 정기회의", 901L, "공연 운영 담당 배정")));
        given(fileRepository.search(eq(1L), isNull(), isNull(), eq(10L), eq(FileSortOption.LATEST),
                isNull(), isNull(), isNull(), anyInt()))
                .willReturn(List.of(file(700L, "공연 큐시트", "pdf", 20480L)));

        JsonNode result = execute("{\"scheduleId\":10}");

        assertThat(result.get("attendeeCount").asInt()).isEqualTo(1);
        assertThat(result.get("attendees").get(0).get("name").asText()).isEqualTo("강민준");
        assertThat(result.get("attendees").get(0).get("phoneLast4").asText()).isEqualTo("1140");
        assertThat(result.get("linkedAgendas").get(0).get("meetingTitle").asText()).isEqualTo("3차 정기회의");
        assertThat(result.get("linkedAgendas").get(0).get("content").asText()).isEqualTo("공연 운영 담당 배정");
        assertThat(result.get("files").get(0).get("name").asText()).isEqualTo("공연 큐시트");
        assertThat(result.get("files").get(0).get("extension").asText()).isEqualTo("pdf");
    }

    @Test
    void execute_참가자가_상한을_넘으면_잘라내고_전체_인원을_알려준다() throws Exception {
        List<ScheduleAttendee> many = IntStream.rangeClosed(1, 101)
                .mapToObj(i -> attendee((long) i, "부원" + i, "경영학과", "0000"))
                .toList();
        givenSchedule();
        given(scheduleAttendeeRepository.findByScheduleIdOrderByNameAsc(10L)).willReturn(many);

        JsonNode result = execute("{\"scheduleId\":10}");

        assertThat(result.get("attendees")).hasSize(100);
        assertThat(result.get("attendeesTruncated").asBoolean()).isTrue();
        // 잘렸어도 전체 인원은 알려줘야 AI가 "101명 중 일부"라고 말할 수 있다
        assertThat(result.get("attendeeCount").asInt()).isEqualTo(101);
    }

    @Test
    void execute_하위_데이터가_없어도_빈_목록으로_답한다() throws Exception {
        givenSchedule();

        JsonNode result = execute("{\"scheduleId\":10}");

        assertThat(result.get("rounds")).isEmpty();
        assertThat(result.get("attendees")).isEmpty();
        assertThat(result.get("linkedAgendas")).isEmpty();
        assertThat(result.get("files")).isEmpty();
        assertThat(result.get("title").asText()).isEqualTo("가을 정기공연");
    }

    @Test
    void execute_다른_기수의_일정이면_ChatToolArgumentException을_던진다() {
        given(scheduleRepository.findForAssistant(any(), any())).willReturn(Optional.empty());

        // 모델이 지어낸 ID는 AI가 고칠 수 있는 실수라 예외로 올려 다시 검색하게 한다
        assertThatThrownBy(() -> tool.execute(1L, objectMapper.readTree("{\"scheduleId\":99}")))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("search_schedules");
    }

    @Test
    void execute_scheduleId가_없으면_ChatToolArgumentException을_던진다() {
        assertThatThrownBy(() -> tool.execute(1L, objectMapper.createObjectNode()))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("필수");
    }

    private void givenSchedule() {
        given(scheduleRepository.findForAssistant(eq(1L), eq(10L))).willReturn(Optional.of(schedule()));
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private Schedule schedule() {
        Schedule schedule = BeanUtils.instantiateClass(Schedule.class);
        ReflectionTestUtils.setField(schedule, "id", 10L);
        ReflectionTestUtils.setField(schedule, "title", "가을 정기공연");
        ReflectionTestUtils.setField(schedule, "startAt", LocalDateTime.of(2026, 11, 20, 18, 0));
        ReflectionTestUtils.setField(schedule, "endAt", LocalDateTime.of(2026, 11, 20, 22, 0));
        ReflectionTestUtils.setField(schedule, "isAllDay", false);
        ReflectionTestUtils.setField(schedule, "location", "학생회관 대강당");
        ReflectionTestUtils.setField(schedule, "hasOperationSpace", true);
        return schedule;
    }

    private ScheduleRound round(int roundNo, String label, String storeName, int totalAmount, int subsidy) {
        ScheduleRound round = BeanUtils.instantiateClass(ScheduleRound.class);
        ReflectionTestUtils.setField(round, "roundNo", roundNo);
        ReflectionTestUtils.setField(round, "label", label);
        ReflectionTestUtils.setField(round, "storeName", storeName);
        ReflectionTestUtils.setField(round, "transactedAt", LocalDateTime.of(2026, 11, 20, 22, 30));
        ReflectionTestUtils.setField(round, "totalAmount", totalAmount);
        ReflectionTestUtils.setField(round, "clubSubsidyAmount", subsidy);
        ReflectionTestUtils.setField(round, "imageUrl", "https://storage.example.com/receipt-1.jpg");
        ReflectionTestUtils.setField(round, "bankName", "국민은행");
        ReflectionTestUtils.setField(round, "accountNumber", "123456-78-901234");
        ReflectionTestUtils.setField(round, "accountHolder", "김총무");
        return round;
    }

    private ScheduleAttendee attendee(Long id, String name, String affiliation, String phoneLast4) {
        ScheduleAttendee attendee = BeanUtils.instantiateClass(ScheduleAttendee.class);
        ReflectionTestUtils.setField(attendee, "id", id);
        ReflectionTestUtils.setField(attendee, "name", name);
        ReflectionTestUtils.setField(attendee, "affiliation", affiliation);
        ReflectionTestUtils.setField(attendee, "phoneLast4", phoneLast4);
        ReflectionTestUtils.setField(attendee, "source", AttendeeSource.LINK);
        return attendee;
    }

    private ScheduleAgendaLink agendaLink(Long meetingId, String meetingTitle, Long agendaId, String content) {
        Meeting meeting = BeanUtils.instantiateClass(Meeting.class);
        ReflectionTestUtils.setField(meeting, "id", meetingId);
        ReflectionTestUtils.setField(meeting, "title", meetingTitle);
        ReflectionTestUtils.setField(meeting, "meetingDate", LocalDate.of(2026, 11, 10));

        MeetingAgenda agenda = BeanUtils.instantiateClass(MeetingAgenda.class);
        ReflectionTestUtils.setField(agenda, "id", agendaId);
        ReflectionTestUtils.setField(agenda, "meeting", meeting);
        ReflectionTestUtils.setField(agenda, "content", content);
        ReflectionTestUtils.setField(agenda, "status", AgendaStatus.COMPLETED);

        ScheduleAgendaLink link = BeanUtils.instantiateClass(ScheduleAgendaLink.class);
        ReflectionTestUtils.setField(link, "meetingAgenda", agenda);
        return link;
    }

    private File file(Long id, String name, String extension, Long size) {
        File file = BeanUtils.instantiateClass(File.class);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "name", name);
        ReflectionTestUtils.setField(file, "extension", extension);
        ReflectionTestUtils.setField(file, "size", size);
        ReflectionTestUtils.setField(file, "category", FileCategory.ACTIVITY_DOC);
        return file;
    }
}
