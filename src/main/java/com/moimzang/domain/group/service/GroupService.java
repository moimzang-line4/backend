package com.moimzang.domain.group.service;

import com.moimzang.domain.group.dto.request.GroupCreateRequest;
import com.moimzang.domain.group.dto.request.GroupUpdateRequest;
import com.moimzang.domain.group.dto.response.GroupCreateResponse;
import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.exception.GroupErrorCode;
import com.moimzang.domain.group.mapper.GroupMapper;
import com.moimzang.domain.group.repository.ClubGroupRepository;
import com.moimzang.domain.group.repository.GroupTermRepository;
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
public class GroupService {

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
