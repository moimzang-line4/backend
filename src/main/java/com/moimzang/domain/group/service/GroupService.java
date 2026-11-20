package com.moimzang.domain.group.service;

import com.moimzang.domain.group.dto.request.GroupCreateRequest;
import com.moimzang.domain.group.dto.request.GroupUpdateRequest;
import com.moimzang.domain.group.dto.response.GroupCreateResponse;
import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupSummaryListResponse;
import com.moimzang.domain.group.dto.response.GroupSummaryResponse;
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.exception.GroupErrorCode;
import com.moimzang.domain.group.mapper.GroupMapper;
import com.moimzang.domain.group.repository.ClubGroupRepository;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.global.exception.CommonErrorCode;
import com.moimzang.global.exception.CustomException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class GroupService {

    // 중복 제거 후 기준(사용자 결정) — 홈 화면 전환 카드 한 번에 보여줄 수 있는 현실적인 상한
    private static final int MAX_GROUP_IDS = 50;

    private final ClubGroupRepository clubGroupRepository;
    private final GroupTermRepository groupTermRepository;
    private final GroupMapper groupMapper;

    // 그룹을 만들면서 첫 기수도 함께 만든다 — 다른 기능이 전부 기수 단위로 동작해서 기수 없는 그룹은 쓸 수 없다
    @Transactional
    public GroupCreateResponse createGroup(GroupCreateRequest request) {
        log.info("[그룹 생성] 생성을 시작합니다. name = {}, termName = {}", request.getName(), request.getTermName());

        ClubGroup group = clubGroupRepository.save(ClubGroup.create(request.getName().strip()));
        GroupTerm term = groupTermRepository.save(GroupTerm.create(group, request.getTermName().strip()));

        log.info("[그룹 생성] 생성을 완료했습니다. groupId = {}, termId = {}", group.getId(), term.getId());
        return GroupCreateResponse.builder()
                .groupId(group.getId())
                .termId(term.getId())
                .build();
    }

    // 그룹 정보와 기수 목록을 조회한다
    public GroupDetailResponse getGroup(Long groupId) {
        log.info("[그룹 조회] 조회를 시작합니다. groupId = {}", groupId);

        ClubGroup group = findGroup(groupId, "그룹 조회");
        List<GroupTerm> terms = groupTermRepository.findAllByGroupIdOrderByIdAsc(groupId);

        log.info("[그룹 조회] 조회를 완료했습니다. groupId = {}, termCount = {}", groupId, terms.size());
        return groupMapper.toDetailResponse(group, terms);
    }

    // 로컬에 저장된 groupId 목록으로 여러 그룹을 한 번에 조회한다(API-087, 홈 화면 그룹 전환용,
    // 명세 외 추가). 존재하지 않는 id는 조용히 제외하고, 응답 순서는 요청한 ids 순서를 그대로
    // 유지한다. club_group·group_term 둘 다 쿼리 1회씩만 날리고, 그룹별 최신 기수 선별은
    // Service에서 id 최댓값 비교로 한다(N+1·복잡한 쿼리 둘 다 피한다)
    public GroupSummaryListResponse getGroupsByIds(String ids) {
        log.info("[그룹 일괄 조회] 조회를 시작합니다. ids = {}", ids);

        List<Long> groupIds = parseGroupIds(ids);

        Map<Long, ClubGroup> groupById = clubGroupRepository.findAllById(groupIds).stream()
                .collect(Collectors.toMap(ClubGroup::getId, Function.identity()));
        List<Long> foundGroupIds = groupIds.stream().filter(groupById::containsKey).toList();

        Map<Long, GroupTerm> currentTermByGroupId = groupTermRepository.findAllByGroupIdIn(foundGroupIds).stream()
                .collect(Collectors.toMap(
                        term -> term.getGroup().getId(),
                        Function.identity(),
                        (current, candidate) -> candidate.getId() > current.getId() ? candidate : current));

        List<GroupSummaryResponse> groups = foundGroupIds.stream()
                .map(groupId -> {
                    GroupTerm currentTerm = currentTermByGroupId.get(groupId);
                    return groupMapper.toSummaryResponse(groupById.get(groupId),
                            currentTerm != null ? currentTerm.getId() : null,
                            currentTerm != null ? currentTerm.getName() : null);
                })
                .toList();

        log.info("[그룹 일괄 조회] 조회를 완료했습니다. requestedCount = {}, foundCount = {}", groupIds.size(), groups.size());
        return GroupSummaryListResponse.builder().groups(groups).build();
    }

    // "1,2,3" 형식을 파싱한다. 빈 문자열·공백만 있는 토큰·숫자가 아닌 토큰이 하나라도 있으면
    // COMMON_400이고, 중복은 처음 등장한 순서를 유지한 채 한 번만 남긴다(LinkedHashSet). 중복
    // 제거 후 개수로 상한을 검사한다(사용자 결정)
    private List<Long> parseGroupIds(String ids) {
        if (ids == null || ids.isBlank()) {
            log.warn("[그룹 일괄 조회] ids가 비어 있습니다.");
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        LinkedHashSet<Long> parsed = new LinkedHashSet<>();
        for (String token : ids.split(",", -1)) {
            String trimmed = token.strip();
            if (trimmed.isEmpty()) {
                log.warn("[그룹 일괄 조회] ids에 빈 값이 섞여 있습니다. ids = {}", ids);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException e) {
                log.warn("[그룹 일괄 조회] ids에 숫자가 아닌 값이 섞여 있습니다. ids = {}", ids);
                throw new CustomException(CommonErrorCode.BAD_REQUEST);
            }
        }

        if (parsed.size() > MAX_GROUP_IDS) {
            log.warn("[그룹 일괄 조회] ids 개수가 상한을 초과했습니다. count = {}", parsed.size());
            throw new CustomException(CommonErrorCode.BAD_REQUEST);
        }

        return new ArrayList<>(parsed);
    }

    // 그룹명을 고치거나 새 기수를 추가한다 — 기존 기수 이름은 바꾸지 않는다.
    // 기수별로 쌓인 일정·명단·장부가 그 기수에 그대로 남아야 해서, 다음 기수는 항상 새 행으로 만든다
    @Transactional
    public GroupUpdateResponse updateGroup(Long groupId, GroupUpdateRequest request) {
        log.info("[그룹 수정] 수정을 시작합니다. groupId = {}, name = {}, termName = {}",
                groupId, request.getName(), request.getTermName());

        ClubGroup group = findGroup(groupId, "그룹 수정");

        if (request.getName() != null) {
            group.updateName(request.getName().strip());
        }

        GroupTerm currentTerm;
        if (request.getTermName() != null) {
            currentTerm = groupTermRepository.save(GroupTerm.create(group, request.getTermName().strip()));
            log.info("[그룹 수정] 새 기수를 만들었습니다. groupId = {}, termId = {}", groupId, currentTerm.getId());
        } else {
            currentTerm = groupTermRepository.findFirstByGroupIdOrderByIdDesc(groupId).orElse(null);
        }

        log.info("[그룹 수정] 수정을 완료했습니다. groupId = {}", groupId);
        return GroupUpdateResponse.builder()
                .groupId(group.getId())
                .name(group.getName())
                .termId(currentTerm != null ? currentTerm.getId() : null)
                .termName(currentTerm != null ? currentTerm.getName() : null)
                .build();
    }

    private ClubGroup findGroup(Long groupId, String action) {
        return clubGroupRepository.findById(groupId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 그룹입니다. groupId = {}", action, groupId);
                    return new CustomException(GroupErrorCode.GROUP_404_NOT_FOUND);
                });
    }
}
