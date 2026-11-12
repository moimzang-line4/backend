package com.moimzang.domain.chat.service.tool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.repository.FileRepository;
import java.time.LocalDateTime;
import java.util.List;
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
class FileSearchToolTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private FileRepository fileRepository;

    @InjectMocks
    private FileSearchTool tool;

    @Test
    void execute_자료명과_분류와_크기를_내려준다() throws Exception {
        givenSearch(null, null, List.of(file(700L, "26기 예산안", "xlsx", 51200L, FileCategory.BUDGET_PLAN)));

        JsonNode file = execute("{}").get("files").get(0);

        assertThat(file.get("fileId").asLong()).isEqualTo(700L);
        assertThat(file.get("name").asText()).isEqualTo("26기 예산안");
        assertThat(file.get("extension").asText()).isEqualTo("xlsx");
        assertThat(file.get("size").asLong()).isEqualTo(51200L);
        assertThat(file.get("category").asText()).isEqualTo("BUDGET_PLAN");
        assertThat(file.get("uploadedAt").asText()).startsWith("2026-11-10");
        // 다운로드 링크는 만들 수 없는 값이라 아예 싣지 않는다
        assertThat(file.has("storageKey")).isFalse();
    }

    @Test
    void execute_분류를_주면_그_분류만_조회한다() throws Exception {
        givenSearch(FileCategory.BUDGET_PLAN, null,
                List.of(file(700L, "26기 예산안", "xlsx", 51200L, FileCategory.BUDGET_PLAN)));

        JsonNode result = execute("{\"category\":\"BUDGET_PLAN\"}");

        assertThat(result.get("category").asText()).isEqualTo("BUDGET_PLAN");
        assertThat(result.get("matchedFileCount").asLong()).isEqualTo(1);
    }

    @Test
    void execute_분류를_소문자로_줘도_받아들인다() throws Exception {
        givenSearch(FileCategory.PHOTO, null,
                List.of(file(701L, "공연 사진", "jpg", 2048L, FileCategory.PHOTO)));

        JsonNode result = execute("{\"category\":\"photo\"}");

        assertThat(result.get("files")).hasSize(1);
    }

    @Test
    void execute_없는_분류를_주면_쓸_수_있는_값을_알려주는_예외를_던진다() {
        // 모델이 '예산안'처럼 한글 분류명을 보낼 수 있다 — AI가 고쳐 부를 수 있게 한다
        assertThatThrownBy(() -> tool.execute(1L, objectMapper.readTree("{\"category\":\"예산안\"}")))
                .isInstanceOf(ChatToolArgumentException.class)
                .hasMessageContaining("BUDGET_PLAN");
    }

    @Test
    void execute_상한을_넘으면_잘라내고_전체_건수를_알려준다() throws Exception {
        List<File> many = IntStream.rangeClosed(1, 51)
                .mapToObj(i -> file((long) i, "자료" + i, "pdf", 1024L, FileCategory.ACTIVITY_DOC))
                .toList();
        givenSearch(null, null, many);
        given(fileRepository.count(eq(1L), isNull(), isNull(), isNull())).willReturn(51L);

        JsonNode result = execute("{}");

        assertThat(result.get("files")).hasSize(50);
        assertThat(result.get("truncated").asBoolean()).isTrue();
        assertThat(result.get("matchedFileCount").asLong()).isEqualTo(51);
    }

    // 매처는 given 호출 안에서 한 번에 만들어야 한다 — 헬퍼 인자로 미리 만들어 넘기면 순서가 꼬인다
    private void givenSearch(FileCategory category, String keyword, List<File> result) {
        given(fileRepository.search(eq(1L), eq(category), eq(keyword), isNull(), eq(FileSortOption.LATEST),
                isNull(), isNull(), isNull(), anyInt())).willReturn(result);
    }

    private JsonNode execute(String argsJson) throws Exception {
        Object result = tool.execute(1L, objectMapper.readTree(argsJson));
        return objectMapper.readTree(objectMapper.writeValueAsString(result));
    }

    private File file(Long id, String name, String extension, Long size, FileCategory category) {
        File file = BeanUtils.instantiateClass(File.class);
        ReflectionTestUtils.setField(file, "id", id);
        ReflectionTestUtils.setField(file, "name", name);
        ReflectionTestUtils.setField(file, "extension", extension);
        ReflectionTestUtils.setField(file, "size", size);
        ReflectionTestUtils.setField(file, "category", category);
        ReflectionTestUtils.setField(file, "storageKey", "seed/" + name);
        ReflectionTestUtils.setField(file, "createdAt", LocalDateTime.of(2026, 11, 10, 9, 0));
        return file;
    }
}
