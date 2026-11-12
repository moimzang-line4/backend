package com.moimzang.domain.chat.service.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.ai.AiTool;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 일정 검색 도구. 다른 도구(정산·출석)가 scheduleId를 요구하므로 이 도구가 id를 찾아주는 입구가 된다
@Component
@RequiredArgsConstructor
public class ScheduleSearchTool implements ChatTool {

    // 한 기수의 일정이 이보다 많아도 프롬프트에 다 싣지 않는다 — 토큰이 터지고 정확도도 떨어진다
    private static final int MAX_ROWS = 50;

    private final ScheduleRepository scheduleRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "search_schedules",
                "이 기수의 일정(행사)을 제목이나 기간으로 검색해 일정 ID·제목·일시·장소·카테고리와 "
                        + "차수 번호 목록을 반환한다. 정산·출석·메모 조회는 scheduleId가 필요하므로, "
                        + "사용자가 일정을 이름으로만 말했다면 반드시 이 도구를 먼저 호출해 ID를 얻어야 한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "일정 제목 부분 일치 검색어. 생략하면 전체 일정"),
                                "from", Map.of(
                                        "type", "string",
                                        "description", "조회 시작일 YYYY-MM-DD. 이 날짜 이후 시작하는 일정만"),
                                "to", Map.of(
                                        "type", "string",
                                        "description", "조회 종료일 YYYY-MM-DD. 이 날짜 이전 시작하는 일정만")),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        String keyword = ChatToolArgs.optionalText(args, "keyword");
        LocalDate from = parseDate(args, "from");
        LocalDate to = parseDate(args, "to");

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<Schedule> found = scheduleRepository.searchForAssistant(termId, keyword, from, to, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<Schedule> page = truncated ? found.subList(0, MAX_ROWS) : found;

        return new Result(page.size(), truncated, page.stream().map(Row::from).toList());
    }

    private LocalDate parseDate(JsonNode args, String field) {
        String text = ChatToolArgs.optionalText(args, field);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new ChatToolArgumentException(field + "은(는) YYYY-MM-DD 형식이어야 합니다. 받은 값: " + text);
        }
    }

    private record Result(int count, boolean truncated, List<Row> schedules) {}

    private record Row(Long scheduleId, String title, String startAt, String endAt, boolean allDay,
                       String location, String categoryName, boolean hasOperationSpace, String memo) {

        static Row from(Schedule schedule) {
            return new Row(
                    schedule.getId(),
                    schedule.getTitle(),
                    schedule.getStartAt().toString(),
                    schedule.getEndAt().toString(),
                    Boolean.TRUE.equals(schedule.getIsAllDay()),
                    schedule.getLocation(),
                    schedule.getScheduleCategory() == null ? null : schedule.getScheduleCategory().getName(),
                    Boolean.TRUE.equals(schedule.getHasOperationSpace()),
                    schedule.getMemo());
        }
    }
}
