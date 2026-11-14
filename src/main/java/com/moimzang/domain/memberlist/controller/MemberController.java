package com.moimzang.domain.memberlist.controller;

import com.moimzang.domain.memberlist.dto.request.MemberAddRequest;
import com.moimzang.domain.memberlist.dto.request.MemberRegisterRequest;
import com.moimzang.domain.memberlist.dto.request.MemberUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberJoinFormResponse;
import com.moimzang.domain.memberlist.dto.response.MemberPageResponse;
import com.moimzang.domain.memberlist.dto.response.MemberUpdateResponse;
import com.moimzang.domain.memberlist.service.MemberService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class MemberController implements MemberApi {

    private final MemberService memberService;

    // 공개 링크의 등록 폼 정보를 조회한다
    @Override
    @GetMapping("/api/public/member-lists/{joinToken}")
    public ResponseEntity<BaseResponse<MemberJoinFormResponse>> getJoinForm(@PathVariable String joinToken) {
        MemberJoinFormResponse response = memberService.getJoinForm(joinToken);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 부원이 공개 링크로 직접 등록한다
    @Override
    @PostMapping("/api/public/member-lists/{joinToken}/members")
    public ResponseEntity<BaseResponse<MemberCreateResponse>> registerMember(
            @PathVariable String joinToken,
            @Valid @RequestBody MemberRegisterRequest request
    ) {
        MemberCreateResponse response = memberService.registerMember(joinToken, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 명단방의 부원 목록을 조회한다
    @Override
    @GetMapping("/api/member-lists/{memberListId}/members")
    public ResponseEntity<BaseResponse<MemberPageResponse>> listMembers(
            @PathVariable Long memberListId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size
    ) {
        MemberPageResponse response = memberService.listMembers(memberListId, keyword, cursor, size);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 운영진이 부원을 직접 추가한다
    @Override
    @PostMapping("/api/member-lists/{memberListId}/members")
    public ResponseEntity<BaseResponse<MemberCreateResponse>> addMember(
            @PathVariable Long memberListId,
            @Valid @RequestBody MemberAddRequest request
    ) {
        MemberCreateResponse response = memberService.addMember(memberListId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 부원 정보를 수정한다
    @Override
    @PatchMapping("/api/members/{memberId}")
    public ResponseEntity<BaseResponse<MemberUpdateResponse>> updateMember(
            @PathVariable Long memberId,
            @Valid @RequestBody MemberUpdateRequest request
    ) {
        MemberUpdateResponse response = memberService.updateMember(memberId, request);
        return ResponseEntity.ok(BaseResponse.success(response));
    }

    // 부원을 삭제한다
    @Override
    @DeleteMapping("/api/members/{memberId}")
    public ResponseEntity<BaseResponse<Void>> deleteMember(@PathVariable Long memberId) {
        memberService.deleteMember(memberId);
        return ResponseEntity.ok(BaseResponse.success());
    }
}
