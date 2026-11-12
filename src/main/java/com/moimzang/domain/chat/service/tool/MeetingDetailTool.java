package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 회의 상세 조회 도구.
// 안건의 논의 내용·결정 사항을 요약하지 않고 원문 그대로 넘긴다 — "어떤 파트장이 운영 맡기로 했지?"
// 같은 질문은 답이 본문 텍스트 안에 있어서, 줄여 보내면 AI가 읽어낼 근거가 사라진다
@Component
@RequiredArgsConstructor
public class MeetingDetailTool implements ChatTool {

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "get_meeting_detail",
                "회의 하나의 안건을 논의 내용·결정 사항 원문 그대로 반환하고 AI 요약본이 있으면 함께 준다. "
                        + "회의에서 무엇을 논의했는지, 누가 무엇을 맡기로 했는지 같은 질문은 이 도구로 답한다. "
                        + "meetingId가 필요하므로 회의 이름만 아는 경우 search_meetings를 먼저 호출한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "meetingId", Map.of(
                                        "type", "integer",
                                        "description", "search_meetings로 얻은 회의 ID")),
                        "required", List.of("meetingId")));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        Long meetingId = ChatToolArgs.requiredLong(args, "meetingId");

        // 모델이 ID를 지어냈거나 다른 기수의 회의를 가리킬 수 있다. AI가 고칠 수 있는 실수라
        // 예외로 올려 error JSON으로 돌려주고 search_meetings부터 다시 하게 둔다
        Meeting meeting = meetingRepository.findForAssistant(termId, meetingId)
                .orElseThrow(() -> new ChatToolArgumentException(
                        "meetingId " + meetingId + "에 해당하는 회의가 이 기수에 없습니다. "
                                + "search_meetings로 회의 ID를 먼저 확인하세요."));

        List<MeetingAgenda> agendas = meetingAgendaRepository.findOrderedAgendas(meetingId);
        long doneCount = agendas.stream().filter(agenda -> agenda.getStatus() == AgendaStatus.COMPLETED).count();

        return new Result(
                meeting.getId(),
                meeting.getTitle(),
                meeting.getMeetingDate() == null ? null : meeting.getMeetingDate().toString(),
                meeting.getMeetingCategory() == null ? null : meeting.getMeetingCategory().getName(),
                meeting.getStatus().name(),
                meeting.getAttendees(),
                meeting.getListSummary(),
                meeting.getSummaryContent(),
                agendas.size(),
                (int) doneCount,
                agendas.stream().map(Row::from).toList());
    }

    private record Result(Long meetingId, String title, String meetingDate, String categoryName, String status,
                          String attendees, String listSummary, String aiSummary,
                          int agendaCount, int doneAgendaCount, List<Row> agendas) {}

    private record Row(Long agendaId, int orderNo, String content, String discussionNote, String decision,
                       String status) {

        static Row from(MeetingAgenda agenda) {
            return new Row(
                    agenda.getId(),
                    agenda.getOrderNo(),
                    agenda.getContent(),
                    agenda.getDiscussionNote(),
                    agenda.getDecision(),
                    agenda.getStatus().name());
        }
    }
}
