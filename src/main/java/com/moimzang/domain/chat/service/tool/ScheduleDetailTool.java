package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.ai.AiTool;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 일정 하나에 딸린 운영 데이터를 한 번에 돌려주는 도구.
// 차수·참가자·안건·자료를 따로 묻게 하면 도구 라운드만 늘고 상한(5회)에 걸리므로 묶어서 내려준다
@Component
@RequiredArgsConstructor
public class ScheduleDetailTool implements ChatTool {

    public static final String NAME = "get_schedule_detail";

    // 한 일정의 참가자·자료가 이보다 많아도 프롬프트에 다 싣지 않는다 — 토큰이 터지고 정확도도 떨어진다
    private static final int MAX_ATTENDEES = 100;
    private static final int MAX_FILES = 50;

    private final ScheduleRepository scheduleRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;
    private final MeetingRepository meetingRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;
    private final FileRepository fileRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                NAME,
                "일정 하나의 상세 정보를 조회해 차수별 영수증(가게·금액·회비 지원금)과 입금 안내 계좌, "
                        + "참가자 명단, 이 일정에서 만든 회의록, 연결된 회의 안건, 첨부 자료를 반환한다. "
                        + "이 일정에 속한 회의록은 scheduleMeetings에 담긴 것이 전부다 — "
                        + "제목이 비슷하다는 이유로 search_meetings에서 찾은 다른 회의록을 "
                        + "이 일정의 회의록인 것처럼 섞어 말하지 않는다. "
                        + "영수증 금액(receiptAmount, totalReceiptAmount)은 가게에서 실제 결제한 총액이다 — "
                        + "'얼마 나왔어', '얼마 썼어'처럼 결제한 금액을 물으면 이 도구를 쓴다. "
                        + "부원에게 걷을 청구 금액은 여기가 아니라 get_settlement_status에 있다. "
                        + "장소·차수·계좌·참가자처럼 일정에 딸린 내용을 물을 때도 이 도구를 쓴다. "
                        + "scheduleId가 필요하므로 일정 이름만 아는 경우 search_schedules를 먼저 호출한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "scheduleId", Map.of(
                                        "type", "integer",
                                        "description", "search_schedules로 얻은 일정 ID")),
                        "required", List.of("scheduleId")));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        Long scheduleId = ChatToolArgs.requiredLong(args, "scheduleId");

        // 모델이 ID를 지어냈거나 다른 기수의 일정을 가리킬 수 있다. 이건 AI가 고칠 수 있는 실수라
        // 예외로 올려 error JSON으로 돌려주고 search_schedules부터 다시 하게 둔다
        Schedule schedule = scheduleRepository.findForAssistant(termId, scheduleId)
                .orElseThrow(() -> new ChatToolArgumentException(
                        "scheduleId " + scheduleId + "에 해당하는 일정이 이 기수에 없습니다. "
                                + "search_schedules로 일정 ID를 먼저 확인하세요."));

        List<ScheduleRound> rounds = scheduleRoundRepository.findOrderedRounds(scheduleId);
        List<ScheduleAttendee> attendees = scheduleAttendeeRepository.findByScheduleIdOrderByNameAsc(scheduleId);
        boolean attendeesTruncated = attendees.size() > MAX_ATTENDEES;
        List<ScheduleAttendee> attendeePage =
                attendeesTruncated ? attendees.subList(0, MAX_ATTENDEES) : attendees;

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        // 일정 상세에서 만든 회의록(일정에 통째로 속한 회의)과 개별로 연결한 안건은 서로 다른 목록이다
        List<Meeting> scheduleMeetings = meetingRepository.findByScheduleForAssistant(scheduleId);
        Map<Long, Long> agendaCounts = meetingAgendaRepository.countByMeetingIds(
                scheduleMeetings.stream().map(Meeting::getId).toList());

        List<File> files = fileRepository.search(
                termId, null, null, scheduleId, FileSortOption.LATEST, null, null, null, MAX_FILES + 1);
        boolean filesTruncated = files.size() > MAX_FILES;
        List<File> filePage = filesTruncated ? files.subList(0, MAX_FILES) : files;

        return new Result(
                schedule.getId(),
                schedule.getTitle(),
                text(schedule.getStartAt()),
                text(schedule.getEndAt()),
                Boolean.TRUE.equals(schedule.getIsAllDay()),
                schedule.getLocation(),
                schedule.getScheduleCategory() == null ? null : schedule.getScheduleCategory().getName(),
                Boolean.TRUE.equals(schedule.getHasOperationSpace()),
                schedule.getMemo(),
                sumAmount(rounds, ScheduleRound::getTotalAmount),
                sumAmount(rounds, ScheduleRound::getClubSubsidyAmount),
                rounds.stream().map(RoundRow::from).toList(),
                attendees.size(),
                attendeesTruncated,
                attendeePage.stream().map(AttendeeRow::from).toList(),
                scheduleMeetings.stream()
                        .map(m -> MeetingRow.from(m, agendaCounts.getOrDefault(m.getId(), 0L))).toList(),
                scheduleAgendaLinkRepository.findByScheduleForAssistant(scheduleId).stream()
                        .map(AgendaRow::from).toList(),
                filesTruncated,
                filePage.stream().map(FileRow::from).toList());
    }

    private static String text(LocalDateTime value) {
        return value == null ? null : value.toString();
    }

    // 차수 합계는 여기서 더해 내려준다 — AI가 차수 금액을 직접 더하면 틀린다.
    // 영수증을 아직 안 올린 차수는 금액이 비어 있어 0으로 친다
    private static int sumAmount(List<ScheduleRound> rounds, Function<ScheduleRound, Integer> field) {
        return rounds.stream()
                .map(field)
                .filter(Objects::nonNull)
                .mapToInt(Integer::intValue)
                .sum();
    }

    // totalReceiptAmount는 모든 차수에서 실제 결제한 금액의 합이고, totalSubsidyAmount는 그중
    // 동아리 회비로 지원한 몫이다. 부원에게 걷는 청구액은 "영수증 − 지원금"이라 이 값과 다르다
    private record Result(Long scheduleId, String title, String startAt, String endAt, boolean allDay,
                          String location, String categoryName, boolean hasOperationSpace, String memo,
                          int totalReceiptAmount, int totalSubsidyAmount, List<RoundRow> rounds,
                          int attendeeCount, boolean attendeesTruncated, List<AttendeeRow> attendees,
                          List<MeetingRow> scheduleMeetings, List<AgendaRow> linkedAgendas,
                          boolean filesTruncated, List<FileRow> files) {}

    // 이 일정에서 만든 회의록. 안건 내용까지는 싣지 않는다 — 필요하면 AI가 get_meeting_detail로 더 판다
    private record MeetingRow(Long meetingId, String title, String meetingDate, String categoryName,
                              String status, String listSummary, long agendaCount) {

        static MeetingRow from(Meeting meeting, long agendaCount) {
            return new MeetingRow(
                    meeting.getId(),
                    meeting.getTitle(),
                    meeting.getMeetingDate() == null ? null : meeting.getMeetingDate().toString(),
                    meeting.getMeetingCategory() == null ? null : meeting.getMeetingCategory().getName(),
                    meeting.getStatus().name(),
                    meeting.getListSummary(),
                    agendaCount);
        }
    }

    // 영수증 이미지 URL은 싣지 않는다 — 답변 문장에 넣을 수 없는 값이라 토큰만 쓴다.
    // 영수증이 등록됐는지 여부만 알려주면 AI가 "영수증은 화면에서 보라"고 안내할 수 있다
    private record RoundRow(Integer roundNo, String label, String storeName, String transactedAt,
                            Integer receiptAmount, Integer clubSubsidyAmount, boolean hasReceiptImage,
                            String bankName, String accountNumber, String accountHolder) {

        static RoundRow from(ScheduleRound round) {
            return new RoundRow(
                    round.getRoundNo(),
                    round.getLabel(),
                    round.getStoreName(),
                    text(round.getTransactedAt()),
                    round.getTotalAmount(),
                    round.getClubSubsidyAmount(),
                    round.getImageUrl() != null,
                    round.getBankName(),
                    round.getAccountNumber(),
                    round.getAccountHolder());
        }
    }

    private record AttendeeRow(Long attendeeId, String name, String affiliation, String phoneLast4, String source) {

        static AttendeeRow from(ScheduleAttendee attendee) {
            return new AttendeeRow(
                    attendee.getId(),
                    attendee.getName(),
                    attendee.getAffiliation(),
                    attendee.getPhoneLast4(),
                    attendee.getSource().name());
        }
    }

    private record AgendaRow(Long meetingId, String meetingTitle, String meetingDate,
                             Long agendaId, String content, String status) {

        static AgendaRow from(ScheduleAgendaLink link) {
            MeetingAgenda agenda = link.getMeetingAgenda();
            Meeting meeting = agenda.getMeeting();
            return new AgendaRow(
                    meeting.getId(),
                    meeting.getTitle(),
                    meeting.getMeetingDate() == null ? null : meeting.getMeetingDate().toString(),
                    agenda.getId(),
                    agenda.getContent(),
                    agenda.getStatus().name());
        }
    }

    private record FileRow(Long fileId, String name, String extension, Long size, String category,
                           String uploadedAt) {

        static FileRow from(File file) {
            return new FileRow(
                    file.getId(),
                    file.getName(),
                    file.getExtension(),
                    file.getSize(),
                    file.getCategory().name(),
                    text(file.getCreatedAt()));
        }
    }
}
