package com.moimzang.domain.chat.service.tool;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.global.ai.AiTool;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 회의 검색 도구. get_meeting_detail이 meetingId를 요구하므로 이 도구가 id를 찾아주는 입구가 된다
@Component
@RequiredArgsConstructor
public class MeetingSearchTool implements ChatTool {

    // 한 기수의 회의가 이보다 많아도 프롬프트에 다 싣지 않는다 — 토큰이 터지고 정확도도 떨어진다
    private static final int MAX_ROWS = 50;

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "search_meetings",
                "이 기수의 회의를 제목·참석자·한 줄 요약으로 검색해 회의 ID·제목·날짜·카테고리·"
                        + "상태·참석자·안건 진행률을 반환한다. 회의에서 무엇을 논의했는지 알려면 "
                        + "이 도구로 회의 ID를 먼저 얻은 뒤 get_meeting_detail을 호출해야 한다. "
                        + "검색 결과가 0건이면 availableTitles에 등록된 회의 제목이 함께 오므로, "
                        + "그중 사용자가 말한 회의와 같은 것이 있으면 그 제목으로 다시 검색한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "회의 제목·참석자·요약 부분 일치 검색어. 생략하면 전체 회의")),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        String keyword = ChatToolArgs.optionalText(args, "keyword");

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<Meeting> found = search(termId, keyword, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<Meeting> page = truncated ? found.subList(0, MAX_ROWS) : found;

        // 검색어가 안 맞아 0건이면 등록된 제목을 함께 돌려준다 — 사용자가 회의를 줄여 부르면
        // 부분 일치로는 잡히지 않아서, 실제 제목을 보여줘야 AI가 "없다"고 단정하지 않는다
        List<String> availableTitles = null;
        if (page.isEmpty() && keyword != null) {
            availableTitles = search(termId, null, MAX_ROWS).stream().map(Meeting::getTitle).toList();
        }

        List<Long> meetingIds = page.stream().map(Meeting::getId).toList();
        Map<Long, Long> totalAgendas = meetingAgendaRepository.countByMeetingIds(meetingIds);
        Map<Long, Long> doneAgendas = meetingAgendaRepository.countCompletedByMeetingIds(meetingIds);

        List<Row> rows = page.stream()
                .map(meeting -> Row.from(
                        meeting,
                        totalAgendas.getOrDefault(meeting.getId(), 0L),
                        doneAgendas.getOrDefault(meeting.getId(), 0L)))
                .toList();

        return new Result(rows.size(), truncated, rows, availableTitles);
    }

    private List<Meeting> search(Long termId, String keyword, int limit) {
        return meetingRepository.search(termId, null, null, keyword, null, null, limit);
    }

    // availableTitles는 검색어가 0건일 때만 채워진다 — 비어 있으면 JSON에서 빠진다
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record Result(int count, boolean truncated, List<Row> meetings, List<String> availableTitles) {}

    private record Row(Long meetingId, String title, String meetingDate, String categoryName, String status,
                       String attendees, String listSummary, boolean hasSummary,
                       long agendaCount, long doneAgendaCount, int progressPercentage) {

        static Row from(Meeting meeting, long agendaCount, long doneAgendaCount) {
            return new Row(
                    meeting.getId(),
                    meeting.getTitle(),
                    meeting.getMeetingDate() == null ? null : meeting.getMeetingDate().toString(),
                    meeting.getMeetingCategory() == null ? null : meeting.getMeetingCategory().getName(),
                    meeting.getStatus().name(),
                    meeting.getAttendees(),
                    meeting.getListSummary(),
                    meeting.getSummaryContent() != null,
                    agendaCount,
                    doneAgendaCount,
                    percentage(agendaCount, doneAgendaCount));
        }

        // 진행률은 회의 상세 화면과 같은 공식(완료 안건 / 전체 안건)으로 맞춘다
        private static int percentage(long agendaCount, long doneAgendaCount) {
            return agendaCount == 0 ? 0 : Math.round(doneAgendaCount * 100f / agendaCount);
        }
    }
}
