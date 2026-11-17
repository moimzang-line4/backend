package com.moimzang.domain.attendance.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MemberListImportResponse {

    @Schema(description = "이번 호출로 새로 추가된 인원 수", example = "4")
    private final int addedCount;

    @Schema(description = "이미 이 차수에 있어 건너뛴 인원 수", example = "1")
    private final int skippedCount;

    @Schema(description = "새로 추가된 참가자 목록(건너뛴 인원은 포함하지 않는다)")
    private final List<OnSiteAttendeeAddResponse> attendees;

    private final RoundAttendanceProgressResponse progress;
}
