package com.moimzang.domain.meeting.dto.request;

import com.moimzang.domain.meeting.enums.AgendaStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 전부 선택값인 부분 수정 요청이다 — '다음 안건'은 status=COMPLETED로, '중단하기'는 status
// 없이(내용만) 호출한다
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class MeetingAgendaUpdateRequest {

    @Schema(description = "안건 내용(선택)", example = "관객 참여 이벤트 기획안")
    @Size(max = 100)
    private String content;

    @Schema(description = "논의 내용(선택)", example = "참여 이벤트 2종 진행하기로 함")
    private String discussionNote;

    @Schema(description = "결정 사항(선택)", example = "경품은 홍보팀이 준비")
    private String decision;

    @Schema(description = "안건 상태(선택)", example = "COMPLETED")
    private AgendaStatus status;
}
