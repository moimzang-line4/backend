package com.moimzang.domain.memberlist.controller;

import com.moimzang.domain.memberlist.dto.request.MemberListCreateRequest;
import com.moimzang.domain.memberlist.dto.request.MemberListUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberListCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListDetailResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListListResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListUpdateResponse;
import com.moimzang.domain.memberlist.service.MemberListService;
import com.moimzang.global.response.BaseResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberListController implements MemberListApi {

    private final MemberListService memberListService;

    // 명단방을 만들고 등록 링크 토큰을 발급한다
    @Override
    @PostMapping("/api/terms/{termId}/member-lists")
    public ResponseEntity<BaseResponse<MemberListCreateResponse>> createMemberList(
            @PathVariable Long termId,
            @Valid @RequestBody MemberListCreateRequest request
    ) {
        MemberListCreateResponse response = memberListService.createMemberList(termId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 기수의 명단방 목록을 조회한다
    @Override
    @GetMapping("/api/terms/{termId}/member-lists")
    public ResponseEntity<BaseResponse<MemberListListResponse>> listMemberLists(@PathVariable Long termId) {
        MemberListListResponse response = memberListService.listMemberLists(termId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 명단방 상세(링크 관리)를 조회한다
    @Override
    @GetMapping("/api/member-lists/{memberListId}")
    public ResponseEntity<BaseResponse<MemberListDetailResponse>> getMemberList(@PathVariable Long memberListId) {
        MemberListDetailResponse response = memberListService.getMemberList(memberListId);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 명단방 설정을 수정한다
    @Override
    @PatchMapping("/api/member-lists/{memberListId}")
    public ResponseEntity<BaseResponse<MemberListUpdateResponse>> updateMemberList(
            @PathVariable Long memberListId,
            @Valid @RequestBody MemberListUpdateRequest request
    ) {
        MemberListUpdateResponse response = memberListService.updateMemberList(memberListId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 명단방을 연결된 기록과 함께 삭제한다
    @Override
    @DeleteMapping("/api/member-lists/{memberListId}")
    public ResponseEntity<BaseResponse<Void>> deleteMemberList(@PathVariable Long memberListId) {
        memberListService.deleteMemberList(memberListId);
        return ResponseEntity.ok(BaseResponse.success());
    }
}
