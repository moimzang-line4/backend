package com.moimzang.domain.schedule.service;

import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.meeting.entity.Meeting;
import com.moimzang.domain.meeting.entity.MeetingAgenda;
import com.moimzang.domain.meeting.repository.MeetingAgendaRepository;
import com.moimzang.domain.schedule.dto.request.AgendaLinkCreateRequest;
import com.moimzang.domain.schedule.dto.response.AgendaLinkCreateResponse;
import com.moimzang.domain.schedule.dto.response.AgendaLinkResponse;
import com.moimzang.domain.schedule.dto.response.LinkedAgendaListResponse;
import com.moimzang.domain.schedule.dto.response.LinkedAgendaResponse;
import com.moimzang.domain.schedule.dto.response.OperationSpaceItemResponse;
import com.moimzang.domain.schedule.dto.response.OperationSpaceListResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleAgendaLink;
import com.moimzang.domain.schedule.enums.OperationSpaceSort;
import com.moimzang.domain.schedule.enums.OperationSpaceStatus;
import com.moimzang.domain.schedule.enums.OperationSpaceType;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.repository.OperationSpaceCursor;
import com.moimzang.domain.schedule.repository.OperationSpaceQueryRepository;
import com.moimzang.domain.schedule.repository.ScheduleAgendaLinkRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OperationSpaceService {

    // 일정 종료 여부는 한국 시간 기준으로 판단한다
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    // cursor 정렬 값 뒤에 유형 순서를 붙일 때 쓰는 구분자. 유형 순서는 숫자라 마지막 구분자로 안전하게 자를 수 있다
    private static final char CURSOR_TYPE_SEPARATOR = '|';

    private final OperationSpaceQueryRepository operationSpaceQueryRepository;
    private final ScheduleRepository scheduleRepository;
    private final ScheduleAgendaLinkRepository scheduleAgendaLinkRepository;
    private final MeetingAgendaRepository meetingAgendaRepository;
    private final GroupTermRepository groupTermRepository;

    // 운영 공간 목록을 조회한다. 일정과 회의록을 각각 한 페이지 + 1건씩 가져와 같은 정렬 기준으로 합친 뒤 자른다
    public OperationSpaceListResponse listOperationSpaces(Long termId, OperationSpaceStatus status,
                                                          OperationSpaceType type, String keyword,
                                                          OperationSpaceSort sort, String cursor, Integer size) {
        log.info("[운영 공간 목록 조회] 조회를 시작합니다. termId = {}, status = {}, type = {}, sort = {}, cursor = {}",
                termId, status, type, sort, cursor);

        if (!groupTermRepository.existsById(termId)) {
            log.warn("[운영 공간 목록 조회] 존재하지 않는 기수입니다. termId = {}", termId);
            throw new CustomException(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
        }
        OperationSpaceType resolvedType = type == null ? OperationSpaceType.ALL : type;
        OperationSpaceSort resolvedSort = sort == null ? OperationSpaceSort.LATEST : sort;
        int limit = CursorPageConstants.resolveSize(size);
        OperationSpaceCursor decoded = decodeCursor(cursor, termId);

        List<Entry> entries = new ArrayList<>();
        if (resolvedType != OperationSpaceType.MEETING) {
            List<Schedule> schedules = operationSpaceQueryRepository.findSchedules(termId, status,
                    LocalDateTime.now(SERVICE_ZONE), keyword, resolvedSort, decoded, limit + 1);
            schedules.forEach(s -> entries.add(Entry.of(s, status, resolvedSort)));
        }
        if (resolvedType != OperationSpaceType.SCHEDULE) {
            List<Meeting> meetings = operationSpaceQueryRepository.findMeetings(termId, status, keyword,
                    resolvedSort, decoded, limit + 1);
            meetings.forEach(m -> entries.add(Entry.of(m, status, resolvedSort)));
        }
        entries.sort(comparator(resolvedSort));

        boolean hasNext = entries.size() > limit;
        List<Entry> page = hasNext ? entries.subList(0, limit) : entries;
        String nextCursor = hasNext ? encodeCursor(page.get(page.size() - 1)) : null;

        log.info("[운영 공간 목록 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}",
                termId, page.size(), hasNext);
        return OperationSpaceListResponse.builder()
                .items(page.stream().map(Entry::item).toList())
                .nextCursor(nextCursor)
                .build();
    }

    // 고른 안건들을 일정에 연결한다. 어느 일정에도 속하지 않은 안건만 연결할 수 있다 —
    // 일정 소속 회의록의 안건이나 이미 연결된 안건이 다른 일정으로 또 끌려가지 않게 막는다
    @Transactional
    public AgendaLinkCreateResponse linkAgendas(Long scheduleId, AgendaLinkCreateRequest request) {
        List<Long> agendaIds = new ArrayList<>(new LinkedHashSet<>(request.getAgendaIds()));
        log.info("[안건 연결] 연결을 시작합니다. scheduleId = {}, agendaIds = {}", scheduleId, agendaIds);

        Schedule schedule = findSchedule(scheduleId, "안건 연결");
        List<MeetingAgenda> agendas = meetingAgendaRepository.findAllById(agendaIds);
        Long groupTermId = schedule.getGroupTerm().getId();
        boolean allFound = agendas.size() == agendaIds.size()
                && agendas.stream().allMatch(a -> a.getMeeting().getGroupTerm().getId().equals(groupTermId));
        if (!allFound) {
            log.warn("[안건 연결] 없거나 다른 기수의 안건이 있습니다. scheduleId = {}, agendaIds = {}", scheduleId, agendaIds);
            throw new CustomException(ScheduleErrorCode.SPACE_404_AGENDA_NOT_FOUND);
        }
        boolean ownedBySchedule = agendas.stream().anyMatch(a -> a.getMeeting().getSchedule() != null);
        if (ownedBySchedule || scheduleAgendaLinkRepository.existsByMeetingAgendaIdIn(agendaIds)) {
            log.warn("[안건 연결] 이미 연결됐거나 일정 소속 회의록의 안건입니다. scheduleId = {}, agendaIds = {}",
                    scheduleId, agendaIds);
            throw new CustomException(ScheduleErrorCode.SPACE_409_AGENDA_ALREADY_LINKED);
        }

        List<AgendaLinkResponse> links = agendas.stream()
                .map(agenda -> scheduleAgendaLinkRepository.save(ScheduleAgendaLink.create(schedule, agenda)))
                .map(link -> AgendaLinkResponse.builder()
                        .linkId(link.getId())
                        .agendaId(link.getMeetingAgenda().getId())
                        .build())
                .toList();

        log.info("[안건 연결] 연결을 완료했습니다. scheduleId = {}, count = {}", scheduleId, links.size());
        return AgendaLinkCreateResponse.builder()
                .links(links)
                .build();
    }

    // 일정에 연결된 안건 카드를 조회한다
    public LinkedAgendaListResponse listLinkedAgendas(Long scheduleId) {
        log.info("[연결된 안건 조회] 조회를 시작합니다. scheduleId = {}", scheduleId);

        findSchedule(scheduleId, "연결된 안건 조회");
        List<LinkedAgendaResponse> links = scheduleAgendaLinkRepository.findLinkedAgendas(scheduleId).stream()
                .map(this::toLinkedAgendaResponse)
                .toList();

        log.info("[연결된 안건 조회] 조회를 완료했습니다. scheduleId = {}, count = {}", scheduleId, links.size());
        return LinkedAgendaListResponse.builder()
                .links(links)
                .build();
    }

    // 안건 연결을 해제한다. 원본 회의록·안건은 그대로 둔다
    @Transactional
    public void unlinkAgenda(Long scheduleId, Long linkId) {
        log.info("[안건 연결 해제] 해제를 시작합니다. scheduleId = {}, linkId = {}", scheduleId, linkId);

        ScheduleAgendaLink link = scheduleAgendaLinkRepository.findByIdAndScheduleId(linkId, scheduleId)
                .orElseThrow(() -> {
                    log.warn("[안건 연결 해제] 이 일정에 없는 연결입니다. scheduleId = {}, linkId = {}", scheduleId, linkId);
                    return new CustomException(ScheduleErrorCode.SPACE_404_LINK_NOT_FOUND);
                });
        scheduleAgendaLinkRepository.delete(link);

        log.info("[안건 연결 해제] 해제를 완료했습니다. scheduleId = {}, linkId = {}", scheduleId, linkId);
    }

    private LinkedAgendaResponse toLinkedAgendaResponse(ScheduleAgendaLink link) {
        MeetingAgenda agenda = link.getMeetingAgenda();
        Meeting meeting = agenda.getMeeting();
        return LinkedAgendaResponse.builder()
                .linkId(link.getId())
                .meetingId(meeting.getId())
                .meetingTitle(meeting.getTitle())
                .meetingDate(meeting.getMeetingDate())
                .meetingStatus(meeting.getStatus())
                .agendaId(agenda.getId())
                .agendaOrderNo(agenda.getOrderNo())
                .agendaContent(agenda.getContent())
                .agendaStatus(agenda.getStatus())
                .discussionNote(agenda.getDiscussionNote())
                .decision(agenda.getDecision())
                .hasDecision(agenda.getDecision() != null && !agenda.getDecision().isBlank())
                .build();
    }

    private Schedule findSchedule(Long scheduleId, String action) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 일정입니다. scheduleId = {}", action, scheduleId);
                    return new CustomException(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
                });
    }

    private OperationSpaceCursor decodeCursor(String cursor, Long termId) {
        if (cursor == null) {
            return null;
        }
        try {
            CursorCodec decoded = CursorCodec.decode(cursor);
            String value = decoded.getSortValue();
            int separator = value.lastIndexOf(CURSOR_TYPE_SEPARATOR);
            int typeOrder = Integer.parseInt(value.substring(separator + 1));
            return new OperationSpaceCursor(value.substring(0, separator), typeOrder, decoded.getId());
        } catch (RuntimeException e) {
            log.warn("[운영 공간 목록 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
            throw new CustomException(ScheduleErrorCode.SPACE_400_INVALID_CURSOR);
        }
    }

    private String encodeCursor(Entry last) {
        return CursorCodec.encode(last.sortValue() + CURSOR_TYPE_SEPARATOR + last.typeOrder(), last.id());
    }

    // 두 쿼리가 쓰는 정렬과 똑같이 (정렬 키, 유형 순서, id)로 비교한다. 제목은 DB 콜레이션처럼 대소문자를 무시한다
    private Comparator<Entry> comparator(OperationSpaceSort sort) {
        Comparator<Entry> byKey = sort == OperationSpaceSort.NAME
                ? Comparator.comparing(Entry::sortValue, String.CASE_INSENSITIVE_ORDER)
                : Comparator.comparing(Entry::dateKey);
        Comparator<Entry> full = byKey.thenComparingInt(Entry::typeOrder).thenComparing(Entry::id);
        return OperationSpaceQueryRepository.isDescending(sort) ? full.reversed() : full;
    }

    // 합치기 위해 두 테이블의 행을 같은 모양으로 맞춘 것. dateKey는 일정은 시작 시각, 회의는 회의 날짜 0시다
    private record Entry(String sortValue, LocalDateTime dateKey, int typeOrder, Long id,
                         OperationSpaceItemResponse item) {

        static Entry of(Schedule schedule, OperationSpaceStatus status, OperationSpaceSort sort) {
            LocalDateTime dateKey = schedule.getStartAt();
            String sortValue = sort == OperationSpaceSort.NAME ? schedule.getTitle() : dateKey.toString();
            return new Entry(sortValue, dateKey, OperationSpaceQueryRepository.SCHEDULE_TYPE_ORDER, schedule.getId(),
                    OperationSpaceItemResponse.builder()
                            .type(OperationSpaceType.SCHEDULE)
                            .id(schedule.getId())
                            .title(schedule.getTitle())
                            .date(schedule.getStartAt().toLocalDate())
                            .status(status)
                            .build());
        }

        static Entry of(Meeting meeting, OperationSpaceStatus status, OperationSpaceSort sort) {
            LocalDateTime dateKey = meeting.getMeetingDate().atStartOfDay();
            String sortValue = sort == OperationSpaceSort.NAME ? meeting.getTitle() : dateKey.toString();
            return new Entry(sortValue, dateKey, OperationSpaceQueryRepository.MEETING_TYPE_ORDER, meeting.getId(),
                    OperationSpaceItemResponse.builder()
                            .type(OperationSpaceType.MEETING)
                            .id(meeting.getId())
                            .title(meeting.getTitle())
                            .date(meeting.getMeetingDate())
                            .status(status)
                            .build());
        }
    }
}
