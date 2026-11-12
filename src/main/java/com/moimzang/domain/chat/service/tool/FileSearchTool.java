package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.global.ai.AiTool;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 자료실 검색 도구. 소프트 삭제 제외와 기수 스코프는 기존 목록 조회 쿼리가 이미 처리한다
@Component
@RequiredArgsConstructor
public class FileSearchTool implements ChatTool {

    public static final String NAME = "search_files";

    // 기수 전체 자료실이 대상이라 파일이 쌓이면 그대로 프롬프트 비용이 된다
    private static final int MAX_ROWS = 50;

    private final FileRepository fileRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                NAME,
                "자료실에 올라온 자료를 이름이나 분류로 검색해 자료명·분류·확장자·크기·등록일을 반환한다. "
                        + "'무슨 자료가 있는지', '예산안 올라왔는지' 같은 질문에 쓴다. "
                        + "다운로드 링크는 제공하지 않으므로 자료실 화면에서 받도록 안내한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "keyword", Map.of(
                                        "type", "string",
                                        "description", "자료명 부분 일치 검색어. 생략하면 전체 자료"),
                                "category", Map.of(
                                        "type", "string",
                                        "description", "자료 분류. ACTIVITY_DOC(활동문서), BUDGET_PLAN(예산안), "
                                                + "PHOTO(활동사진), LEDGER_FILE(장부) 중 하나")),
                        "required", List.of()));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        String keyword = ChatToolArgs.optionalText(args, "keyword");
        FileCategory category = parseCategory(args);

        // 상한보다 1건 더 조회해서 잘렸는지 판단한다 — AI가 "일부만 보여드렸다"고 말할 수 있어야 한다
        List<File> found = fileRepository.search(
                termId, category, keyword, null, FileSortOption.LATEST, null, null, null, MAX_ROWS + 1);
        boolean truncated = found.size() > MAX_ROWS;
        List<File> page = truncated ? found.subList(0, MAX_ROWS) : found;

        // 잘리지 않았으면 가져온 건수가 곧 전체 건수다 — 같은 답을 얻으려고 count 쿼리를 더 날리지 않는다
        long matchedFileCount = truncated
                ? fileRepository.count(termId, category, keyword, null)
                : page.size();

        return new Result(
                keyword,
                category == null ? null : category.name(),
                matchedFileCount,
                truncated,
                page.stream().map(Row::from).toList());
    }

    // 모델이 '예산안'처럼 한글이나 없는 분류명을 보낼 수 있다. AI가 고칠 수 있는 실수라
    // 쓸 수 있는 값을 알려주는 예외로 올린다
    private FileCategory parseCategory(JsonNode args) {
        String text = ChatToolArgs.optionalText(args, "category");
        if (text == null) {
            return null;
        }
        try {
            return FileCategory.valueOf(text.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ChatToolArgumentException("category는 " + Arrays.toString(FileCategory.values())
                    + " 중 하나여야 합니다. 받은 값: " + text);
        }
    }

    // 어떤 조건으로 걸러진 수인지 알 수 있게 적용된 검색 조건을 그대로 돌려준다
    private record Result(String keyword, String category, long matchedFileCount, boolean truncated,
                          List<Row> files) {}

    private record Row(Long fileId, String name, String extension, Long size, String category,
                       Long scheduleId, String uploadedAt) {

        static Row from(File file) {
            return new Row(
                    file.getId(),
                    file.getName(),
                    file.getExtension(),
                    file.getSize(),
                    file.getCategory().name(),
                    file.getSchedule() == null ? null : file.getSchedule().getId(),
                    file.getCreatedAt() == null ? null : file.getCreatedAt().toString());
        }
    }
}
