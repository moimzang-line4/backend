package com.moimzang.domain.meeting.service;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MeetingService {

    private final MeetingRepository meetingRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;
    private final GroupTermRepository groupTermRepository;
    private final ScheduleCategoryRepository scheduleCategoryRepository;

    @Transactional
    public MeetingCreateResponse createMeeting(Long termId, MeetingCreateRequest request) {
        log.info("[회의 생성] 생성을 시작합니다. termId = {}, agendaCount = {}",
                termId, request.getAgendas().size());

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> new CustomException(CommonErrorCode.NOT_FOUND));
        ScheduleCategory category = scheduleCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new CustomException(CommonErrorCode.NOT_FOUND));

        Meeting meeting = meetingRepository.save(Meeting.create(
                groupTerm, category, request.getTitle(), request.getMeetingDate(),
                request.getAttendees(), request.getListSummary()));

        // 요청에 적힌 순서를 그대로 안건의 orderNo로 쓴다 — 회의 진행 화면의 안건 탭 순서가 이 순서다
        List<Long> agendaIds = new ArrayList<>();
        List<String> agendas = request.getAgendas();
        for (int i = 0; i < agendas.size(); i++) {
            MeetingAgenda agenda = meetingAgendaRepository.save(
                    MeetingAgenda.create(meeting, agendas.get(i), i));
            agendaIds.add(agenda.getId());
        }

        log.info("[회의 생성] 생성을 완료했습니다. meetingId = {}, agendaCount = {}",
                meeting.getId(), agendaIds.size());
        return MeetingCreateResponse.builder()
                .meetingId(meeting.getId())
                .agendaIds(agendaIds)
                .build();
    }
}
