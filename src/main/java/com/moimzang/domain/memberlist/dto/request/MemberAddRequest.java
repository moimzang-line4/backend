package com.moimzang.domain.memberlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 운영진이 명단 조회 화면에서 부원을 직접 추가하는 요청. 추가 화면에는 이름·학교·학과·전화번호만 있다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberAddRequest {

    @Schema(description = "이름", example = "한지우")
    @NotBlank
    @Size(max = 50)
    private String name;

    @Schema(description = "학교(선택)", example = "동국대학교")
    @Size(max = 100)
    private String school;

    @Schema(description = "학과", example = "전자전기공학부")
    @NotBlank
    @Size(max = 100)
    private String department;

    @Schema(description = "전화번호(선택)", example = "010-6789-0123")
    @Size(max = 20)
    private String phone;
}
