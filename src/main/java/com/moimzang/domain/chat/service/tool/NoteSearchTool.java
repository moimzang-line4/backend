package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.note.entity.OperationNote;
import com.moimzang.domain.note.enums.NoteType;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.global.ai.AiTool;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 운영 메모 검색 도구. 메모는 일정에 딸려 있어 "그 행사 때 뭐 하기로 했더라" 류 질문을 받는다
@Component
@RequiredArgsConstructor
public class NoteSearchTool implements ChatTool {

    public static final String NAME = "search_notes";

    // 기수 전체를 대상으로 하는 검색이라 메모가 쌓이면 그대로 프롬프트 비용이 된다
    private static final int MAX_ROWS = 50;

    private final OperationNoteRepository operationNoteRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                NAME,
                "일정에 적어둔 운영 메모와 할 일을 검색해 유형·제목·내용·담당자·완료 여부를 반환한다. "
                        + "'무엇을 하기로 했는지', '누가 맡았는지', '남은 할 일'을 묻는 질문에 쓴다. "
                        + "완료 여부는 검색어로 거를 수 없다 — '안 끝난 할 일'은 noteType=TODO로 전체를 받아 "
                        + "todoDone이 false인 것만 골라서 답한다. "
                        + "특정 일정의 메모만 보려면 search_schedules로 scheduleId를 먼저 얻어 함께 넘긴다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "메모 제목·내용·담당자 부분 일치 검색어. 생략하면 전체 메모"),
                                "scheduleId", Map.of(
                                        "type", "integer",
                                        "description", "search_schedules로 얻은 일정 ID. 생략하면 기수 전체 일정의 메모"),
                                "noteType", Map.of(
                                        "type", "string",
                                        "description", "메모 유형. 할 일만 보려면 TODO, 일반 메모만 보려면 MEMO. "
                                                + "생략하면 둘 다")),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        String keyword = ChatToolArgs.optionalText(args, "keyword");
        Long scheduleId = ChatToolArgs.optionalLong(args, "scheduleId");
        NoteType noteType = parseNoteType(args);

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<OperationNote> found =
                operationNoteRepository.searchForAssistant(termId, keyword, scheduleId, noteType, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<OperationNote> page = truncated ? found.subList(0, MAX_ROWS) : found;

        // 잘리지 않았으면 가져온 건수가 곧 전체 건수다 — 같은 답을 얻으려고 count 쿼리를 더 날리지 않는다
        long matchedNoteCount = truncated
                ? operationNoteRepository.countForAssistant(termId, keyword, scheduleId, noteType)
                : page.size();

        return new Result(keyword, scheduleId, noteType == null ? null : noteType.name(),
                matchedNoteCount, truncated, page.stream().map(Row::from).toList());
    }

    // 모델이 '할일'처럼 없는 유형명을 보낼 수 있다. AI가 고칠 수 있는 실수라
    // 쓸 수 있는 값을 알려주는 예외로 올린다
    private NoteType parseNoteType(JsonNode args) {
        String text = ChatToolArgs.optionalText(args, "noteType");
        if (text == null) {
            return null;
        }
        try {
            return NoteType.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ChatToolArgumentException("noteType은 " + Arrays.toString(NoteType.values())
                    + " 중 하나여야 합니다. 받은 값: " + text);
        }
    }

    // 어떤 조건으로 걸러진 수인지 알 수 있게 적용된 검색 조건을 그대로 돌려준다
    private record Result(String keyword, Long scheduleId, String noteType, long matchedNoteCount,
                          boolean truncated, List<Row> notes) {}

    private record Row(Long noteId, Long scheduleId, String scheduleTitle, String type, String title,
                       String content, String assignee, Boolean todoDone) {

        static Row from(OperationNote note) {
            return new Row(
                    note.getId(),
                    note.getSchedule().getId(),
                    note.getSchedule().getTitle(),
                    note.getType().name(),
                    note.getTitle(),
                    note.getContent(),
                    note.getAssignee(),
                    note.getIsTodoDone());
        }
    }
}
