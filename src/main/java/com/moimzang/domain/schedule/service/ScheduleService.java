package com.moimzang.domain.schedule.service;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.dto.request.ScheduleCategoryCreateRequest;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryListResponse;
import com.moimzang.domain.schedule.dto.response.ScheduleCategoryResponse;
import com.moimzang.domain.schedule.entity.ScheduleCategory;
import com.moimzang.domain.schedule.exception.ScheduleErrorCode;
import com.moimzang.domain.schedule.mapper.ScheduleMapper;
import com.moimzang.domain.schedule.repository.ScheduleCategoryRepository;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.exception.CustomException;
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
    private final ScheduleMapper scheduleMapper;

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

    private GroupTerm findGroupTerm(Long termId, String action) {
        return groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 기수입니다. termId = {}", action, termId);
                    return new CustomException(ScheduleErrorCode.SCHEDULE_404_GROUP_TERM_NOT_FOUND);
                });
    }
}
