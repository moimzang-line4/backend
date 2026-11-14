package com.moimzang.domain.memberlist.service;

import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.memberlist.dto.request.MemberAddRequest;
import com.moimzang.domain.memberlist.dto.request.MemberRegisterRequest;
import com.moimzang.domain.memberlist.dto.request.MemberUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberJoinFormResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListFieldsResponse;
import com.moimzang.domain.memberlist.dto.response.MemberPageResponse;
import com.moimzang.domain.memberlist.dto.response.MemberUpdateResponse;
import com.moimzang.domain.memberlist.entity.Member;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
import com.moimzang.domain.memberlist.exception.MemberListErrorCode;
import com.moimzang.domain.memberlist.mapper.MemberListMapper;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberService {

    // 마감 여부는 한국 시간 기준으로 판단한다
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    private final MemberListRepository memberListRepository;
    private final MemberRepository memberRepository;
    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final MemberListMapper memberListMapper;

    // 부원이 공개 링크로 들어왔을 때 보여줄 등록 폼 정보를 조회한다
    public MemberJoinFormResponse getJoinForm(String joinToken) {
        log.info("[부원 등록 폼 조회] 조회를 시작합니다.");

        MemberList memberList = findOpenMemberList(joinToken, "부원 등록 폼 조회");

        log.info("[부원 등록 폼 조회] 조회를 완료했습니다. memberListId = {}", memberList.getId());
        return MemberJoinFormResponse.builder()
                .groupName(memberList.getGroupTerm().getGroup().getName())
                .memberListName(memberList.getName())
                .registerDeadline(memberList.getRegisterDeadline())
                .fields(MemberListFieldsResponse.builder()
                        .school(memberList.collectsSchool())
                        .studentId(memberList.collectsStudentId())
                        .part(memberList.collectsPart())
                        .grade(memberList.collectsGrade())
                        .phone(memberList.collectsPhone())
                        .build())
                .build();
    }

    // 부원이 공개 링크로 직접 등록한다. 명단방에서 켠 항목은 필수이고 끈 항목은 보내도 저장하지 않는다
    @Transactional
    public MemberCreateResponse registerMember(String joinToken, MemberRegisterRequest request) {
        log.info("[부원 등록] 등록을 시작합니다.");

        MemberList memberList = findOpenMemberList(joinToken, "부원 등록");
        if (!Boolean.TRUE.equals(request.getPrivacyConsent())) {
            log.warn("[부원 등록] 개인정보 수집에 동의하지 않았습니다. memberListId = {}", memberList.getId());
            throw new CustomException(MemberListErrorCode.MEMBER_400_PRIVACY_CONSENT_REQUIRED);
        }

        String school = collected(memberList.collectsSchool(), request.getSchool());
        String phone = collected(memberList.collectsPhone(), request.getPhone());
        String studentId = collected(memberList.collectsStudentId(), request.getStudentId());
        String part = collected(memberList.collectsPart(), request.getPart());
        String grade = collected(memberList.collectsGrade(), request.getGrade());
        boolean missing = (memberList.collectsSchool() && school == null)
                || (memberList.collectsPhone() && phone == null)
                || (memberList.collectsStudentId() && studentId == null)
                || (memberList.collectsPart() && part == null)
                || (memberList.collectsGrade() && grade == null);
        if (missing) {
            log.warn("[부원 등록] 명단방에서 받는 항목이 비어 있습니다. memberListId = {}", memberList.getId());
            throw new CustomException(MemberListErrorCode.MEMBER_400_REQUIRED_FIELD_MISSING);
        }

        Member member = memberRepository.save(Member.create(memberList, request.getName().strip(), school,
                request.getDepartment().strip(), phone, studentId, part, grade, true));

        log.info("[부원 등록] 등록을 완료했습니다. memberListId = {}, memberId = {}", memberList.getId(), member.getId());
        return MemberCreateResponse.builder()
                .memberId(member.getId())
                .build();
    }

    // 명단 조회 탭. 등록한 순서대로 보여주고 이름·학과·전화번호로 검색한다
    public MemberPageResponse listMembers(Long memberListId, String keyword, String cursor, Integer size) {
        log.info("[부원 목록 조회] 조회를 시작합니다. memberListId = {}, keyword = {}, cursor = {}",
                memberListId, keyword, cursor);

        if (!memberListRepository.existsById(memberListId)) {
            log.warn("[부원 목록 조회] 존재하지 않는 명단방입니다. memberListId = {}", memberListId);
            throw new CustomException(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
        }

        int limit = CursorPageConstants.resolveSize(size);

        // cursor의 정렬 값은 등록 시각을 ISO-8601 문자열로 직렬화한 것이다
        LocalDateTime cursorCreatedAt = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorCreatedAt = LocalDateTime.parse(decoded.getSortValue());
                cursorId = decoded.getId();
            } catch (RuntimeException e) {
                log.warn("[부원 목록 조회] cursor 형식이 올바르지 않습니다. memberListId = {}, cursor = {}",
                        memberListId, cursor);
                throw new CustomException(MemberListErrorCode.MEMBER_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 마지막 1건이 있으면 다음 페이지가 있다는 뜻이다
        List<Member> fetched = memberRepository.searchInMemberList(
                memberListId, keyword, cursorCreatedAt, cursorId, limit + 1);
        boolean hasNext = fetched.size() > limit;
        List<Member> page = hasNext ? fetched.subList(0, limit) : fetched;

        String nextCursor = null;
        if (hasNext) {
            Member last = page.get(page.size() - 1);
            nextCursor = CursorCodec.encode(last.getCreatedAt().toString(), last.getId());
        }
        long totalCount = memberRepository.countInMemberList(memberListId, keyword);

        log.info("[부원 목록 조회] 조회를 완료했습니다. memberListId = {}, count = {}, totalCount = {}, hasNext = {}",
                memberListId, page.size(), totalCount, hasNext);
        return MemberPageResponse.builder()
                .totalCount(totalCount)
                .members(memberListMapper.toMemberResponses(page))
                .nextCursor(nextCursor)
                .build();
    }

    // 운영진이 명단 조회 화면에서 부원을 직접 추가한다. 마감된 명단방에도 추가할 수 있다.
    // 폼 동의를 거치지 않았으므로 개인정보 동의 여부는 false로 남긴다
    @Transactional
    public MemberCreateResponse addMember(Long memberListId, MemberAddRequest request) {
        log.info("[부원 추가] 추가를 시작합니다. memberListId = {}", memberListId);

        MemberList memberList = memberListRepository.findById(memberListId)
                .orElseThrow(() -> {
                    log.warn("[부원 추가] 존재하지 않는 명단방입니다. memberListId = {}", memberListId);
                    return new CustomException(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
                });

        Member member = memberRepository.save(Member.create(memberList, request.getName().strip(),
                blankToNull(request.getSchool()), request.getDepartment().strip(), blankToNull(request.getPhone()),
                null, null, null, false));

        log.info("[부원 추가] 추가를 완료했습니다. memberListId = {}, memberId = {}", memberListId, member.getId());
        return MemberCreateResponse.builder()
                .memberId(member.getId())
                .build();
    }

    // 부원 정보를 수정한다. 보낸 필드만 바꾸고 일정 참가자 정보는 스냅샷이라 함께 바뀌지 않는다
    @Transactional
    public MemberUpdateResponse updateMember(Long memberId, MemberUpdateRequest request) {
        log.info("[부원 수정] 수정을 시작합니다. memberId = {}", memberId);

        Member member = findMember(memberId, "부원 수정");
        member.update(strip(request.getName()), strip(request.getSchool()), strip(request.getDepartment()),
                strip(request.getPhone()));

        log.info("[부원 수정] 수정을 완료했습니다. memberId = {}", memberId);
        return MemberUpdateResponse.builder()
                .memberId(member.getId())
                .build();
    }

    // 부원을 명단에서 삭제한다. 이 부원으로 등록된 일정 참가자는 남기고 부원 연결만 끊는다
    @Transactional
    public void deleteMember(Long memberId) {
        log.info("[부원 삭제] 삭제를 시작합니다. memberId = {}", memberId);

        findMember(memberId, "부원 삭제");
        scheduleAttendeeRepository.clearMemberReference(memberId);
        memberRepository.deleteById(memberId);

        log.info("[부원 삭제] 삭제를 완료했습니다. memberId = {}", memberId);
    }

    // 링크가 없으면 404, 링크를 닫았거나 마감일이 지났으면 410
    private MemberList findOpenMemberList(String joinToken, String action) {
        MemberList memberList = memberListRepository.findByJoinToken(joinToken)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 등록 링크입니다.", action);
                    return new CustomException(MemberListErrorCode.MEMBER_404_JOIN_LINK_NOT_FOUND);
                });
        if (memberList.effectiveStatus(LocalDateTime.now(SERVICE_ZONE)) == MemberListStatus.COMPLETED) {
            log.warn("[{}] 등록이 마감된 명단방입니다. memberListId = {}", action, memberList.getId());
            throw new CustomException(MemberListErrorCode.MEMBER_410_JOIN_CLOSED);
        }
        return memberList;
    }

    private Member findMember(Long memberId, String action) {
        return memberRepository.findById(memberId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 부원입니다. memberId = {}", action, memberId);
                    return new CustomException(MemberListErrorCode.MEMBER_404_NOT_FOUND);
                });
    }

    // 명단방에서 받지 않는 항목은 보내도 버린다
    private String collected(boolean collects, String value) {
        return collects ? blankToNull(value) : null;
    }

    private String blankToNull(String value) {
        return StringUtils.hasText(value) ? value.strip() : null;
    }

    private String strip(String value) {
        return value == null ? null : value.strip();
    }
}
