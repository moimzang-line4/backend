package com.moimzang.domain.settlement.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SettlementTargetSaveRequest {

    @Schema(description = "정산 명단에 포함할 참가자 ID(schedule_attendee.id) 목록. 기존 명단을 통째로 교체한다",
            example = "[1, 2, 3]")
    @NotNull
    private List<Long> attendeeIds;
}
