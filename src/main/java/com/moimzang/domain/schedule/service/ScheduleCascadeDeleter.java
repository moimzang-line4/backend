package com.moimzang.domain.schedule.service;

import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.budget.repository.LedgerEntryRepository;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.domain.note.repository.OperationNoteRepository;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

// 일정 하나에 딸린 데이터를 FK 순서대로 지운다. 일정 행 자체는 호출하는 쪽이 마지막에 지운다.
// 모든 FK가 RESTRICT라 자식부터 지워야 하고, 순서가 하나라도 어긋나면 그 자리에서 FK 에러가 난다
@Component
@RequiredArgsConstructor
public class ScheduleCascadeDeleter {

    private final ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;
    private final MeetingRepository meetingRepository;
    private final OperationNoteRepository operationNoteRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final RoundAttendeeRepository roundAttendeeRepository;
    private final SettlementTargetRepository settlementTargetRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final FileRepository fileRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void deleteChildren(Long scheduleId) {
        // 회의록 쪽: 연결 → 일정 소속 회의록의 안건 → 회의록.
        // 다른 일정에서 연결만 해 둔 안건은 연결만 끊기고 원본 회의록은 남는다
        scheduleAgendaLinkRepository.deleteAllForScheduleDeletion(scheduleId);
        meetingAgendaRepository.deleteAllByMeetingScheduleId(scheduleId);
        meetingRepository.deleteAllByScheduleId(scheduleId);

        operationNoteRepository.deleteAllByScheduleId(scheduleId);

        // 출석·정산 쪽: 장부 참조 끊기 → 출석·정산 대상 → 차수 → 참가자.
        // 장부 기록은 돈이 실제로 오간 기록이라 지우지 않고 차수 참조만 끊는다
        ledgerEntryRepository.clearScheduleReference(scheduleId);
        roundAttendeeRepository.deleteAllByScheduleId(scheduleId);
        settlementTargetRepository.deleteAllByScheduleId(scheduleId);
        scheduleRoundRepository.deleteAllByScheduleId(scheduleId);
        scheduleAttendeeRepository.deleteAllByScheduleId(scheduleId);

        fileRepository.deleteAllByScheduleId(scheduleId);
    }
}
