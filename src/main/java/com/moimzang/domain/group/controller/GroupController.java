package com.moimzang.domain.group.controller;

import com.moimzang.domain.group.dto.request.GroupCreateRequest;
import com.moimzang.domain.group.dto.request.GroupUpdateRequest;
import com.moimzang.domain.group.dto.response.GroupCreateResponse;
import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupUpdateResponse;
import com.moimzang.domain.group.service.GroupService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class GroupController implements GroupApi {

    private final GroupService groupService;

    // 그룹과 첫 기수를 함께 생성한다
    @Override
    @PostMapping("/api/groups")
    public ResponseEntity<BaseResponse<GroupCreateResponse>> createGroup(
            @Valid @RequestBody GroupCreateRequest request
    ) {
        GroupCreateResponse response = groupService.createGroup(request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그룹 정보와 기수 목록을 조회한다
    @Override
    @GetMapping("/api/groups/{groupId}")
    public ResponseEntity<BaseResponse<GroupDetailResponse>> getGroup(@PathVariable Long groupId) {
        GroupDetailResponse response = groupService.getGroup(groupId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 그룹명을 수정하거나 새 기수를 추가한다
    @Override
    @PatchMapping("/api/groups/{groupId}")
    public ResponseEntity<BaseResponse<GroupUpdateResponse>> updateGroup(
            @PathVariable Long groupId,
            @Valid @RequestBody GroupUpdateRequest request
    ) {
        GroupUpdateResponse response = groupService.updateGroup(groupId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }
}
