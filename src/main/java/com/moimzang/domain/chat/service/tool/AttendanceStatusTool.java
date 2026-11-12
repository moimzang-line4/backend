package com.moimzang.domain.chat.service.tool;

import com.moimzang.domain.attendance.entity.RoundAttendee;
import com.moimzang.domain.attendance.entity.ScheduleAttendee;
import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.global.ai.AiTool;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

// 일정의 출석 현황 조회 도구.
// 인원 수는 여기서 세서 내려준다 — AI가 명단을 직접 세게 두면 틀린다
@Component
@RequiredArgsConstructor
public class AttendanceStatusTool implements ChatTool {

    private final RoundAttendeeRepository roundAttendeeRepository;

    @Override
    public AiTool definition() {
        return new AiTool(
                "get_attendance_status",
                "일정의 출석 현황을 조회해 차수별 출석 인원·미출석 인원과 함께 "
                        + "참가자별 차수 출석 여부 목록을 반환한다. "
                        + "누가 왔는지·누가 빠졌는지·몇 명 왔는지 묻는 질문에 쓴다. "
                        + "rounds[].targetCount는 그 차수의 출석 대상 인원이고, 실제 참석한 인원은 "
                        + "checkedInCount다 — '몇 명 참석했어'에는 checkedInCount를 쓴다. "
                        + "scheduleId가 필요하므로 일정 이름만 아는 경우 search_schedules를 먼저 호출한다.",
                Map.of(
                        "type", "object",
                        "properties", Map.of(
                                "scheduleId", Map.of(
                                        "type", "integer",
                                        "description", "search_schedules로 얻은 일정 ID"),
                                "roundNo", Map.of(
                                        "type", "integer",
                                        "description", "차수 번호. 사용자가 '2차'라고 하면 2. 생략하면 전체 차수")),
                        "required", List.of("scheduleId")));
    }

    @Override
    @Transactional(readOnly = true)
    public Object execute(Long termId, JsonNode args) {
        Long scheduleId = ChatToolArgs.requiredLong(args, "scheduleId");
        Integer roundNo = ChatToolArgs.optionalInt(args, "roundNo");

        List<RoundAttendee> records =
                roundAttendeeRepository.findByScheduleForAssistant(termId, scheduleId, roundNo);

        List<RoundRow> rounds = aggregateByRound(records);
        List<MemberRow> members = aggregateByAttendee(records);

        return new Result(
                scheduleId,
                roundNo,
                rounds,
                members.size(),
                members);
    }

    // 차수별 인원 집계. 사람별 상세는 members에만 두고 여기엔 수만 담는다 — 같은 행을 두 번 실으면
    // 토큰만 늘고 두 값이 어긋나 보일 수 있다
    private List<RoundRow> aggregateByRound(List<RoundAttendee> records) {
        Map<Long, RoundAccumulator> byRound = new LinkedHashMap<>();
        for (RoundAttendee record : records) {
            ScheduleRound round = record.getScheduleRound();
            byRound.computeIfAbsent(round.getId(), id -> new RoundAccumulator(round)).add(record);
        }
        return byRound.values().stream().map(RoundAccumulator::toRow).toList();
    }

    // 사람별 집계. "누가 2차에만 빠졌어?" 같은 질문은 차수별 수만으로는 답할 수 없어
    // 참가자마다 차수별 출석 여부를 그대로 넘긴다
    private List<MemberRow> aggregateByAttendee(List<RoundAttendee> records) {
        Map<Long, MemberAccumulator> byAttendee = new LinkedHashMap<>();
        for (RoundAttendee record : records) {
            ScheduleAttendee attendee = record.getScheduleAttendee();
            byAttendee.computeIfAbsent(attendee.getId(), id -> new MemberAccumulator(attendee)).add(record);
        }
        return byAttendee.values().stream().map(MemberAccumulator::toRow).toList();
    }

    private static final class RoundAccumulator {

        private final ScheduleRound round;
        private int targetCount;
        private int checkedInCount;

        private RoundAccumulator(ScheduleRound round) {
            this.round = round;
        }

        private void add(RoundAttendee record) {
            targetCount++;
            if (Boolean.TRUE.equals(record.getIsCheckedIn())) {
                checkedInCount++;
            }
        }

        private RoundRow toRow() {
            return new RoundRow(round.getRoundNo(), round.getLabel(), targetCount, checkedInCount,
                    targetCount - checkedInCount);
        }
    }

    private static final class MemberAccumulator {

        private final ScheduleAttendee attendee;
        private final List<AttendanceRow> rounds = new ArrayList<>();
        private int checkedInCount;

        private MemberAccumulator(ScheduleAttendee attendee) {
            this.attendee = attendee;
        }

        private void add(RoundAttendee record) {
            boolean checkedIn = Boolean.TRUE.equals(record.getIsCheckedIn());
            if (checkedIn) {
                checkedInCount++;
            }
            rounds.add(new AttendanceRow(
                    record.getScheduleRound().getRoundNo(),
                    checkedIn,
                    record.getCheckedInAt() == null ? null : record.getCheckedInAt().toString()));
        }

        private MemberRow toRow() {
            return new MemberRow(
                    attendee.getId(),
                    attendee.getName(),
                    attendee.getAffiliation(),
                    attendee.getPhoneLast4(),
                    checkedInCount,
                    rounds.size(),
                    rounds);
        }
    }

    private record Result(Long scheduleId, Integer roundNo, List<RoundRow> rounds,
                          int attendeeCount, List<MemberRow> members) {}

    // targetCount는 그 차수의 출석 대상 인원이고 checkedInCount가 실제 참석 인원이다 —
    // 이름이 비슷하면 "6명 참석, 그중 4명 출석" 같은 모순된 문장이 나온다
    private record RoundRow(Integer roundNo, String label, int targetCount, int checkedInCount,
                            int notCheckedInCount) {}

    private record MemberRow(Long attendeeId, String name, String affiliation, String phoneLast4,
                             int checkedInCount, int roundCount, List<AttendanceRow> rounds) {}

    private record AttendanceRow(Integer roundNo, boolean checkedIn, String checkedInAt) {}
}
