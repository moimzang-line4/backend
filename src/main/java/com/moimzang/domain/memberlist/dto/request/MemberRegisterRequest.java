package com.moimzang.domain.memberlist.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 부원이 공개 링크로 직접 등록하는 요청. 이름·학과 외 항목이 필수인지는 명단방 토글에 따라 서비스에서 판단한다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberRegisterRequest {

    @Schema(description = "이름", example = "홍길동")
    @NotBlank
    @Size(max = 50)
    private String name;

    @Schema(description = "학교(명단방에서 학교를 받을 때만 필수)", example = "동국대학교")
    @Size(max = 100)
    private String school;

    @Schema(description = "학과", example = "광고홍보학과")
    @NotBlank
    @Size(max = 100)
    private String department;

    @Schema(description = "전화번호(명단방에서 전화번호를 받을 때만 필수)", example = "010-0000-0000")
    @Size(max = 20)
    private String phone;

    @Schema(description = "학번(명단방에서 학번을 받을 때만 필수)", example = "2026123456")
    @Size(max = 30)
    private String studentId;

    @Schema(description = "파트(명단방에서 파트를 받을 때만 필수)", example = "보컬")
    @Size(max = 50)
    private String part;

    @Schema(description = "학년(명단방에서 학년을 받을 때만 필수)", example = "1학년")
    @Size(max = 20)
    private String grade;

    @Schema(description = "개인정보 수집·이용 동의", example = "true")
    @NotNull
    private Boolean privacyConsent;
}
