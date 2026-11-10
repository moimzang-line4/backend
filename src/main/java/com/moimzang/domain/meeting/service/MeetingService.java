package com.moimzang.domain.meeting.service;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.exception.MeetingErrorCode;
import com.moimzang.domain.meeting.mapper.MeetingMapper;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingCategoryRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
    private final MeetingCategoryRepository meetingCategoryRepository;
    private final MeetingMapper meetingMapper;

    // 회의와 안건을 한 번에 생성한다
    @Transactional
    public MeetingCreateResponse createMeeting(Long termId, MeetingCreateRequest request) {
        log.info("[회의 생성] 생성을 시작합니다. termId = {}, agendaCount = {}",
                termId, request.getAgendas().size());

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[회의 생성] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
                });

        // 회의 카테고리는 일정 카테고리와 별도 테이블(meeting_category)로 운영한다
        // 회의 카테고리 생성은 별도 api
        MeetingCategory category = meetingCategoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> {
                    log.warn("[회의 생성] 존재하지 않는 카테고리입니다. categoryId = {}", request.getCategoryId());
                    return new CustomException(MeetingErrorCode.MEETING_404_CATEGORY_NOT_FOUND);
                });

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

    // 기수의 회의 목록을 상태·카테고리·검색어로 필터링해 cursor 페이지네이션으로 조회한다
    public MeetingListResponse listMeetings(Long termId, MeetingStatus status, Long categoryId, String keyword,
                                             String cursor, Integer size) {
        log.info("[회의 목록 조회] 조회를 시작합니다. termId = {}, status = {}, categoryId = {}, keyword = {}, cursor = {}",
                termId, status, categoryId, keyword, cursor);

        groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[회의 목록 조회] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
                });

        int limit = CursorPageConstants.resolveSize(size);

        // cursor 디코딩 — sortValue는 meetingDate를 ISO-8601 문자열로 직렬화한 것이다
        LocalDate cursorMeetingDate = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorMeetingDate = LocalDate.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[회의 목록 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
                throw new CustomException(MeetingErrorCode.MEETING_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<Meeting> fetched = meetingRepository.search(
                termId, status, categoryId, keyword, cursorMeetingDate, cursorId, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<Meeting> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<Long> meetingIds = page.stream().map(Meeting::getId).toList();
        Map<Long, Long> agendaCounts = meetingAgendaRepository.countByMeetingIds(meetingIds);

        List<MeetingSummaryResponse> summaries = page.stream()
                .map(m -> meetingMapper.toSummaryResponse(m, agendaCounts.getOrDefault(m.getId(), 0L)))
                .toList();

        String nextCursor = hasNext ? buildCursor(page.get(page.size() - 1)) : null;

        log.info("[회의 목록 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}",
                termId, summaries.size(), hasNext);

        return MeetingListResponse.builder()
                .meetings(summaries)
                .nextCursor(nextCursor)
                .build();
    }

    // 마지막 회의를 기준으로 다음 페이지 cursor를 만든다
    private String buildCursor(Meeting last) {
        return CursorCodec.encode(last.getMeetingDate().toString(), last.getId());
    }
}
