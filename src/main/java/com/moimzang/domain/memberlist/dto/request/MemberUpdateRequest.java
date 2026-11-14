package com.moimzang.domain.memberlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 보낸 필드만 수정하는 부분 수정 요청이라 전부 선택값이다. 수정 화면에 학번·파트·학년은 없다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberUpdateRequest {

    @Schema(description = "이름(선택)", example = "한지우")
    @Size(max = 50)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String name;

    @Schema(description = "학교(선택)", example = "동국대학교")
    @Size(max = 100)
    private String school;

    @Schema(description = "학과(선택)", example = "전자전기공학부")
    @Size(max = 100)
    @Pattern(regexp = "(?s).*\\S.*", message = "공백일 수 없습니다")
    private String department;

    @Schema(description = "전화번호(선택)", example = "010-6789-0123")
    @Size(max = 20)
    private String phone;
}
