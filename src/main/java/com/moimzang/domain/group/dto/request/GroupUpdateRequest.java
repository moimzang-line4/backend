package com.moimzang.domain.group.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 반영하는 부분 수정 요청이라 전부 선택값이다 — null이면 그 항목은 바뀌지 않는다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class GroupUpdateRequest {

    @Schema(description = "그룹명(선택)", example = "음샘")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String name;

    @Schema(description = "새 기수명(선택). 보내면 새 기수를 만든다", example = "27기 (2027)")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String termName;
}
