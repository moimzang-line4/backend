package com.moimzang.domain.schedule.service;

import com.moimzang.domain.attendance.entity.ScheduleRound;
import com.moimzang.domain.attendance.repository.ScheduleRoundRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleCreateRequest;
import com.moimzang.domain.schedule.dto.request.ScheduleUpdateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCreateResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleDetailResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleUpdateResponse;
import com.moimzang.domain.schedule.dto.response.UpcomingScheduleListResponse;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.mapper.ScheduleMapper;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final ScheduleCategoryRepository scheduleCategoryRepository;
    private final GroupTermRepository groupTermRepository;
    private final ScheduleRoundRepository scheduleRoundRepository;
    private final ScheduleMapper scheduleMapper;
    private final ScheduleCascadeDeleter scheduleCascadeDeleter;

    // 동아리 일정은 전부 한국 시간 기준이라 서버 시간대와 상관없이 D-day를 한국 날짜로 센다
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    // 홈 카드에 보여줄 다가오는 일정 수의 기본값·상한
    private static final int UPCOMING_DEFAULT_LIMIT = 5;
    private static final int UPCOMING_MAX_LIMIT = 20;

    // 일정을 등록한다. 운영 공간을 함께 만들면 출석·정산이 차수 단위로 동작해서 1차 차수도 같이 만든다
    @Transactional
    public ScheduleCreateResponse createSchedule(Long termId, ScheduleCreateRequest request) {
        log.info("[일정 등록] 등록을 시작합니다. termId = {}, createOperationSpace = {}",
                termId, request.getCreateOperationSpace());

        GroupTerm groupTerm = findGroupTerm(termId, "일정 등록");
        validatePeriod(request.getStartAt(), request.getEndAt(), "일정 등록");
        ScheduleCategory category = findCategory(request.getCategoryId(), termId, "일정 등록");

        Schedule schedule = scheduleRepository.save(Schedule.create(
                groupTerm, category, request.getTitle().strip(), request.getColor().name(), request.getIsAllDay(),
                request.getStartAt(), request.getEndAt(), blankToNull(request.getLocation()),
                blankToNull(request.getMemo()), request.getCreateOperationSpace()));

        if (Boolean.TRUE.equals(request.getCreateOperationSpace())) {
            scheduleRoundRepository.save(ScheduleRound.create(schedule, 1, "1차"));
        }

        log.info("[일정 등록] 등록을 완료했습니다. scheduleId = {}, hasOperationSpace = {}",
                schedule.getId(), schedule.getHasOperationSpace());
        return ScheduleCreateResponse.builder()
                .scheduleId(schedule.getId())
                .hasOperationSpace(schedule.getHasOperationSpace())
                .build();
    }

    // 캘린더에 띄울 일정을 조회한다. from~to 기간과 하루라도 겹치는 일정을 시작 시각순으로 내려준다
    public ScheduleListResponse listSchedules(Long termId, LocalDate from, LocalDate to, String cursor, Integer size) {
        log.info("[일정 목록 조회] 조회를 시작합니다. termId = {}, from = {}, to = {}, cursor = {}", termId, from, to, cursor);

        findGroupTerm(termId, "일정 목록 조회");
        if (from.isAfter(to)) {
            log.warn("[일정 목록 조회] 조회 시작일이 종료일보다 늦습니다. from = {}, to = {}", from, to);
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }
        int limit = CursorPageConstants.resolveSize(size);

        // cursor의 정렬 값은 시작 시각을 ISO-8601 문자열로 직렬화한 것이다
        LocalDateTime cursorStartAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorStartAt = LocalDateTime.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[일정 목록 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
                throw new CustomException(ScheduleErrorCode.SCHEDULE_400_INVALID_CURSOR);
            }
        }

        // to는 그날 전체를 포함해야 해서 하루의 마지막 시각까지 본다
        List<Schedule> fetched = scheduleRepository.searchCalendar(termId, from.atStartOfDay(), to.atTime(LocalTime.MAX),
                cursorStartAt, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<Schedule> page = hasNext ? fetched.subList(0, limit) : fetched;

        String nextCursor = null;
        if (hasNext) {
            Schedule last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getStartAt().toString(), last.getId());
        }

        log.info("[일정 목록 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}", termId, page.size(), hasNext);
        return ScheduleListResponse.builder()
                .schedules(scheduleMapper.toSummaryResponses(page))
                .nextCursor(nextCursor)
                .build();
    }

    // 홈 '다가오는 운영 일정' 카드에 띄울 일정을 조회한다
    public UpcomingScheduleListResponse listUpcomingSchedules(Long termId, Integer limit) {
        log.info("[다가오는 일정 조회] 조회를 시작합니다. termId = {}, limit = {}", termId, limit);

        findGroupTerm(termId, "다가오는 일정 조회");
        int resolvedLimit = limit == null || limit <= 0 ? UPCOMING_DEFAULT_LIMIT : Math.min(limit, UPCOMING_MAX_LIMIT);
        List<Schedule> schedules = scheduleRepository.findUpcomingOperationSchedules(
                termId, LocalDateTime.now(SERVICE_ZONE), resolvedLimit);

        log.info("[다가오는 일정 조회] 조회를 완료했습니다. termId = {}, count = {}", termId, schedules.size());
        return UpcomingScheduleListResponse.builder()
                .schedules(scheduleMapper.toUpcomingResponses(schedules))
                .build();
    }

    // 일정 상세를 조회한다
    public ScheduleDetailResponse getSchedule(Long scheduleId) {
        log.info("[일정 상세 조회] 조회를 시작합니다. scheduleId = {}", scheduleId);

        Schedule schedule = findSchedule(scheduleId, "일정 상세 조회");
        // 날짜 단위로만 세서 시작 시각이 오늘 밤이어도 당일이면 0이다
        long dDay = ChronoUnit.DAYS.between(LocalDate.now(SERVICE_ZONE), schedule.getStartAt().toLocalDate());

        log.info("[일정 상세 조회] 조회를 완료했습니다. scheduleId = {}", scheduleId);
        return scheduleMapper.toDetailResponse(schedule, dDay);
    }

    // 일정을 부분 수정한다. 시작·종료 중 하나만 바꿔도 바뀐 결과 기준으로 순서를 다시 확인한다
    @Transactional
    public ScheduleUpdateResponse updateSchedule(Long scheduleId, ScheduleUpdateRequest request) {
        log.info("[일정 수정] 수정을 시작합니다. scheduleId = {}", scheduleId);

        Schedule schedule = findSchedule(scheduleId, "일정 수정");
        LocalDateTime startAt = request.getStartAt() != null ? request.getStartAt() : schedule.getStartAt();
        LocalDateTime endAt = request.getEndAt() != null ? request.getEndAt() : schedule.getEndAt();
        validatePeriod(startAt, endAt, "일정 수정");

        // 경로에 기수가 없으므로 수정 대상 일정이 속한 기수를 기준으로 카테고리를 찾는다
        ScheduleCategory category = findCategory(request.getCategoryId(), schedule.getGroupTerm().getId(), "일정 수정");

        schedule.update(category, stripOrNull(request.getTitle()),
                request.getColor() == null ? null : request.getColor().name(), request.getIsAllDay(),
                request.getStartAt(), request.getEndAt(), stripOrNull(request.getLocation()),
                stripOrNull(request.getMemo()));

        log.info("[일정 수정] 수정을 완료했습니다. scheduleId = {}", scheduleId);
        return ScheduleUpdateResponse.builder()
                .scheduleId(schedule.getId())
                .build();
    }

    // 기수에 일정 카테고리를 추가한다 — 같은 이름이 두 개면 선택 목록에서 구분할 수 없어 막는다
    @Transactional
    public ScheduleCategoryResponse createCategory(Long termId, ScheduleCategoryCreateRequest request) {
        log.info("[일정 카테고리 추가] 추가를 시작합니다. termId = {}, name = {}", termId, request.getName());

        GroupTerm groupTerm = findGroupTerm(termId, "일정 카테고리 추가");
        String name = request.getName().strip();
        if (scheduleCategoryRepository.existsByGroupTermIdAndName(termId, name)) {
            log.warn("[일정 카테고리 추가] 이미 있는 카테고리입니다. termId = {}, name = {}", termId, name);
            throw new CustomException(ScheduleErrorCode.SCHEDULE_409_CATEGORY_DUPLICATED);
        }

        ScheduleCategory category = scheduleCategoryRepository.save(ScheduleCategory.create(groupTerm, name));

        log.info("[일정 카테고리 추가] 추가를 완료했습니다. categoryId = {}", category.getId());
        return scheduleMapper.toCategoryResponse(category);
    }

    // 기수의 일정 카테고리 목록을 조회한다. 회의 카테고리와는 별도로 관리한다
    public ScheduleCategoryListResponse listCategories(Long termId) {
        log.info("[일정 카테고리 목록 조회] 조회를 시작합니다. termId = {}", termId);

        findGroupTerm(termId, "일정 카테고리 목록 조회");
        List<ScheduleCategory> categories = scheduleCategoryRepository.findByGroupTermIdOrderByIdAsc(termId);

        log.info("[일정 카테고리 목록 조회] 조회를 완료했습니다. termId = {}, count = {}", termId, categories.size());
        return ScheduleCategoryListResponse.builder()
                .categories(scheduleMapper.toCategoryResponses(categories))
                .build();
    }

    // 일정을 삭제한다. 운영 메모·차수·출석·정산·자료·안건 연결과 이 일정에서 만든 회의록까지 한 번에 지운다
    @Transactional
    public void deleteSchedule(Long scheduleId) {
        log.info("[일정 삭제] 삭제를 시작합니다. scheduleId = {}", scheduleId);

        Schedule schedule = findSchedule(scheduleId, "일정 삭제");
        scheduleCascadeDeleter.deleteChildren(scheduleId);
        // 하위 삭제가 영속성 컨텍스트를 비우므로 엔티티 대신 ID로 지운다
        scheduleRepository.deleteById(schedule.getId());

        log.info("[일정 삭제] 삭제를 완료했습니다. scheduleId = {}", scheduleId);
    }

    private Schedule findSchedule(Long scheduleId, String action) {
        return scheduleRepository.findById(scheduleId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 일정입니다. scheduleId = {}", action, scheduleId);
                    return new CustomException(ScheduleErrorCode.SCHEDULE_404_NOT_FOUND);
                });
    }

    // 카테고리는 선택 항목이라 ID가 없으면 null을 돌려준다
    private ScheduleCategory findCategory(Long categoryId, Long termId, String action) {
        if (categoryId == null) {
            return null;
        }
        return scheduleCategoryRepository.findByIdAndGroupTermId(categoryId, termId)
                .orElseThrow(() -> {
                    log.warn("[{}] 이 기수에 없는 카테고리입니다. termId = {}, categoryId = {}", action, termId, categoryId);
                    return new CustomException(ScheduleErrorCode.SCHEDULE_404_CATEGORY_NOT_FOUND);
                });
    }

    // 종일 일정은 시작·종료가 같은 시각으로 올 수 있어서 종료가 시작보다 앞설 때만 막는다
    private void validatePeriod(LocalDateTime startAt, LocalDateTime endAt, String action) {
        if (endAt.isBefore(startAt)) {
            log.warn("[{}] 종료 시간이 시작 시간보다 빠릅니다. startAt = {}, endAt = {}", action, startAt, endAt);
            throw new CustomException(ScheduleErrorCode.SCHEDULE_400_END_BEFORE_START);
        }
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }

    private String stripOrNull(String value) {
        return value == null ? null : value.strip();
    }

    private GroupTerm findGroupTerm(Long termId, String action) {
        return groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 기수입니다. termId = {}", action, termId);
                    return new CustomException(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
                });
    }
}
