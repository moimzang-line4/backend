package com.moimzang.domain.meeting.service;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingAgendaUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCategoryCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingCreateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingSummaryUpdateRequest;
import com.moimzang.domain.meeting.dto.request.MeetingUpdateRequest;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingAgendaUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCategoryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingCreateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingDetailResponse;
import com.moimzang.domain.meeting.dto.response.MeetingEndResponse;
import com.moimzang.domain.meeting.dto.response.MeetingListResponse;
import com.moimzang.domain.meeting.dto.response.MeetingProgressResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryGenerateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryResponse;
import com.moimzang.domain.meeting.dto.response.MeetingSummaryUpdateResponse;
import com.moimzang.domain.meeting.dto.response.MeetingUpdateResponse;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.entity.MeetingCategory;
import com.moimzang.domain.meeting.enums.AgendaStatus;
import com.moimzang.domain.meeting.enums.MeetingStatus;
import com.moimzang.domain.meeting.exception.MeetingErrorCode;
import com.moimzang.domain.meeting.mapper.MeetingMapper;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.meeting.repository.MeetingCategoryRepository;
import com.moimzang.domain.meeting.repository.MeetingRepository;
import com.moimzang.global.ai.AiClient;
import com.moimzang.global.ai.AiClientException;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
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
    private final AiClient aiClient;
    private final MeetingSummaryPromptBuilder summaryPromptBuilder;

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

    // 회의 상세를 조회한다 — 안건 전체 목록과 완료/전체 안건 수 기반 진행률을 함께 내려준다
    public MeetingDetailResponse getMeetingDetail(Long meetingId) {
        log.info("[회의 상세 조회] 조회를 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[회의 상세 조회] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        List<MeetingAgenda> agendas = meetingAgendaRepository.findOrderedAgendas(meetingId);
        MeetingProgressResponse progress = calculateProgress(agendas);

        log.info("[회의 상세 조회] 조회를 완료했습니다. meetingId = {}, agendaCount = {}", meetingId, agendas.size());
        return meetingMapper.toDetailResponse(meeting, agendas, progress);
    }

    // 완료/전체 안건 수와 그 비율(%)을 계산한다. 안건이 0개면 0%로 처리한다(0으로 나누기 방지)
    private MeetingProgressResponse calculateProgress(List<MeetingAgenda> agendas) {
        int totalCount = agendas.size();
        int doneCount = (int) agendas.stream().filter(a -> a.getStatus() == AgendaStatus.COMPLETED).count();
        int percentage = totalCount == 0 ? 0 : Math.round(doneCount * 100f / totalCount);

        return MeetingProgressResponse.builder()
                .doneCount(doneCount)
                .totalCount(totalCount)
                .percentage(percentage)
                .build();
    }

    // 회의 정보를 부분 수정한다 — 종료된 회의는 수정을 거부한다
    @Transactional
    public MeetingUpdateResponse updateMeeting(Long meetingId, MeetingUpdateRequest request) {
        log.info("[회의 정보 수정] 수정을 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[회의 정보 수정] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        if (meeting.isEnded()) {
            log.warn("[회의 정보 수정] 이미 종료된 회의입니다. meetingId = {}", meetingId);
            throw new CustomException(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
        }

        MeetingCategory category = null;
        if (request.getCategoryId() != null) {
            category = meetingCategoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> {
                        log.warn("[회의 정보 수정] 존재하지 않는 카테고리입니다. categoryId = {}", request.getCategoryId());
                        return new CustomException(MeetingErrorCode.MEETING_404_CATEGORY_NOT_FOUND);
                    });
        }

        meeting.updateInfo(category, request.getTitle(), request.getMeetingDate(),
                request.getAttendees(), request.getListSummary());

        log.info("[회의 정보 수정] 수정을 완료했습니다. meetingId = {}", meetingId);
        return MeetingUpdateResponse.builder()
                .meetingId(meeting.getId())
                .build();
    }

    // 회의를 삭제한다 — 상태와 무관하게 허용한다(decisions #15). 안건을 먼저 지우고 회의를 지운다
    @Transactional
    public void deleteMeeting(Long meetingId) {
        log.info("[회의 삭제] 삭제를 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[회의 삭제] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        meetingAgendaRepository.deleteAllByMeetingId(meetingId);
        meetingRepository.delete(meeting);

        log.info("[회의 삭제] 삭제를 완료했습니다. meetingId = {}", meetingId);
    }

    // 기수의 회의 카테고리 목록을 조회한다
    public MeetingCategoryListResponse listCategories(Long termId) {
        log.info("[회의 카테고리 목록 조회] 조회를 시작합니다. termId = {}", termId);

        groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[회의 카테고리 목록 조회] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
                });

        List<MeetingCategory> categories = meetingCategoryRepository.findByGroupTermIdOrderByIdAsc(termId);

        log.info("[회의 카테고리 목록 조회] 조회를 완료했습니다. termId = {}, count = {}", termId, categories.size());
        return MeetingCategoryListResponse.builder()
                .categories(meetingMapper.toCategoryResponses(categories))
                .build();
    }

    // 기수에 새 회의 카테고리를 생성한다
    @Transactional
    public MeetingCategoryResponse createCategory(Long termId, MeetingCategoryCreateRequest request) {
        log.info("[회의 카테고리 생성] 생성을 시작합니다. termId = {}, name = {}", termId, request.getName());

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[회의 카테고리 생성] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(MeetingErrorCode.MEETING_404_GROUP_TERM_NOT_FOUND);
                });

        MeetingCategory saved = meetingCategoryRepository.save(MeetingCategory.create(groupTerm, request.getName()));

        log.info("[회의 카테고리 생성] 생성을 완료했습니다. categoryId = {}", saved.getId());
        return meetingMapper.toCategoryResponse(saved);
    }

    // 회의에 안건을 추가한다 — 기존 안건 목록 끝(마지막 orderNo+1)에 붙인다. 종료된 회의는 거부한다
    @Transactional
    public MeetingAgendaCreateResponse addAgenda(Long meetingId, MeetingAgendaCreateRequest request) {
        log.info("[안건 추가] 추가를 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[안건 추가] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        if (meeting.isEnded()) {
            log.warn("[안건 추가] 이미 종료된 회의입니다. meetingId = {}", meetingId);
            throw new CustomException(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
        }

        int nextOrderNo = meetingAgendaRepository.findLastAgendas(meetingId, PageRequest.of(0, 1))
                .stream().findFirst()
                .map(last -> last.getOrderNo() + 1)
                .orElse(0);

        MeetingAgenda saved = meetingAgendaRepository.save(MeetingAgenda.create(meeting, request.getContent(), nextOrderNo));

        log.info("[안건 추가] 추가를 완료했습니다. agendaId = {}, orderNo = {}", saved.getId(), nextOrderNo);
        return MeetingAgendaCreateResponse.builder()
                .agendaId(saved.getId())
                .orderNo(nextOrderNo)
                .build();
    }

    // 안건 논의 내용을 저장한다 — '다음 안건'(status=COMPLETED)과 '중단하기'(status 없이 내용만)가
    // 같은 API를 서로 다른 값으로 호출한다. 종료된 회의는 거부한다
    @Transactional
    public MeetingAgendaUpdateResponse updateAgendaContent(Long agendaId, MeetingAgendaUpdateRequest request) {
        log.info("[안건 내용 저장] 저장을 시작합니다. agendaId = {}, status = {}", agendaId, request.getStatus());

        MeetingAgenda agenda = meetingAgendaRepository.findById(agendaId)
                .orElseThrow(() -> {
                    log.warn("[안건 내용 저장] 존재하지 않는 안건입니다. agendaId = {}", agendaId);
                    return new CustomException(MeetingErrorCode.AGENDA_404_NOT_FOUND);
                });

        if (agenda.getMeeting().isEnded()) {
            log.warn("[안건 내용 저장] 이미 종료된 회의의 안건입니다. agendaId = {}", agendaId);
            throw new CustomException(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
        }

        // 완료된 안건을 다른 상태로 되돌리는 요청만 막는다 — status 없이 내용만 고치는 건 허용
        if (agenda.getStatus() == AgendaStatus.COMPLETED
                && request.getStatus() != null
                && request.getStatus() != AgendaStatus.COMPLETED) {
            log.warn("[안건 내용 저장] 이미 완료된 안건의 상태를 되돌리려 했습니다. agendaId = {}, requestedStatus = {}",
                    agendaId, request.getStatus());

            throw new CustomException(MeetingErrorCode.AGENDA_409_ALREADY_COMPLETED);
        }

        agenda.updateContent(request.getContent(), request.getDiscussionNote(), request.getDecision(), request.getStatus());

        List<MeetingAgenda> agendas = meetingAgendaRepository.findOrderedAgendas(agenda.getMeeting().getId());
        MeetingProgressResponse progress = calculateProgress(agendas);

        log.info("[안건 내용 저장] 저장을 완료했습니다. agendaId = {}, status = {}", agendaId, agenda.getStatus());
        return MeetingAgendaUpdateResponse.builder()
                .agendaId(agenda.getId())
                .status(agenda.getStatus())
                .progress(progress)
                .build();
    }

    // 안건을 삭제한다 — 안건이 1개만 남으면 거부한다. 종료된 회의도 거부한다
    @Transactional
    public void deleteAgenda(Long agendaId) {
        log.info("[안건 삭제] 삭제를 시작합니다. agendaId = {}", agendaId);

        MeetingAgenda agenda = meetingAgendaRepository.findById(agendaId)
                .orElseThrow(() -> {
                    log.warn("[안건 삭제] 존재하지 않는 안건입니다. agendaId = {}", agendaId);
                    return new CustomException(MeetingErrorCode.AGENDA_404_NOT_FOUND);
                });

        if (agenda.getMeeting().isEnded()) {
            log.warn("[안건 삭제] 이미 종료된 회의의 안건입니다. agendaId = {}", agendaId);
            throw new CustomException(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
        }

        if (meetingAgendaRepository.countByMeetingId(agenda.getMeeting().getId()) <= 1) {
            log.warn("[안건 삭제] 안건이 1개만 남아 삭제할 수 없습니다. agendaId = {}", agendaId);
            throw new CustomException(MeetingErrorCode.AGENDA_409_AT_LEAST_ONE_REQUIRED);
        }

        meetingAgendaRepository.delete(agenda);

        log.info("[안건 삭제] 삭제를 완료했습니다. agendaId = {}", agendaId);
    }

    // 회의를 종료한다 — 안건이 전부 완료되지 않았어도 허용한다
    @Transactional
    public MeetingEndResponse endMeeting(Long meetingId) {
        log.info("[회의 종료] 종료를 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[회의 종료] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        if (meeting.isEnded()) {
            log.warn("[회의 종료] 이미 종료된 회의입니다. meetingId = {}", meetingId);
            throw new CustomException(MeetingErrorCode.MEETING_409_ALREADY_ENDED);
        }

        meeting.end();

        long doneAgendaCount = meetingAgendaRepository.countByMeetingIdAndStatus(meetingId, AgendaStatus.COMPLETED);
        long totalAgendaCount = meetingAgendaRepository.countByMeetingId(meetingId);

        log.info("[회의 종료] 종료를 완료했습니다. meetingId = {}, doneAgendaCount = {}, totalAgendaCount = {}",
                meetingId, doneAgendaCount, totalAgendaCount);
        return MeetingEndResponse.builder()
                .meetingId(meeting.getId())
                .status(meeting.getStatus())
                .endedAt(meeting.getEndedAt())
                .doneAgendaCount(doneAgendaCount)
                .totalAgendaCount(totalAgendaCount)
                .build();
    }

    // AI로 회의 전체를 요약한다 — 원본 안건(content·discussionNote·decision)은 항상 보존.
    // 회의 종료 후 화면에서만 쓰는 동작이라 종료 여부를 따로 검사하지 않는다.
    // "다시 요약하기"도 이 메서드를 그대로 재호출한다
    @Transactional
    public MeetingSummaryGenerateResponse generateSummary(Long meetingId) {
        log.info("[AI 요약 생성] 생성을 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[AI 요약 생성] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        List<MeetingAgenda> agendas = meetingAgendaRepository.findOrderedAgendas(meetingId);
        String prompt = summaryPromptBuilder.build(meeting, agendas);

        String summaryContent;
        try {
            summaryContent = aiClient.generateContent(prompt);
        } catch (AiClientException e) {
            log.warn("[AI 요약 생성] AI 호출에 실패했습니다. meetingId = {}", meetingId, e);
            throw new CustomException(MeetingErrorCode.MEETING_502_SUMMARY_FAILED);
        }

        meeting.applySummary(summaryContent);

        log.info("[AI 요약 생성] 생성을 완료했습니다. meetingId = {}, agendaCount = {}", meetingId, agendas.size());
        return MeetingSummaryGenerateResponse.builder()
                .summarizedAgendaCount(agendas.size())
                .summaryContent(meeting.getSummaryContent())
                .summaryGeneratedAt(meeting.getSummaryGeneratedAt())
                .build();
    }

    // 사용자가 직접 고친 요약을 저장한다 — AI 재호출 없이 받은 텍스트로 그대로 덮어쓴다
    @Transactional
    public MeetingSummaryUpdateResponse updateSummary(Long meetingId, MeetingSummaryUpdateRequest request) {
        log.info("[회의록 저장] 저장을 시작합니다. meetingId = {}", meetingId);

        Meeting meeting = meetingRepository.findById(meetingId)
                .orElseThrow(() -> {
                    log.warn("[회의록 저장] 존재하지 않는 회의입니다. meetingId = {}", meetingId);
                    return new CustomException(MeetingErrorCode.MEETING_404_NOT_FOUND);
                });

        meeting.updateSummaryContent(request.getSummaryContent());

        log.info("[회의록 저장] 저장을 완료했습니다. meetingId = {}", meetingId);
        return MeetingSummaryUpdateResponse.builder()
                .meetingId(meeting.getId())
                .build();
    }
}
