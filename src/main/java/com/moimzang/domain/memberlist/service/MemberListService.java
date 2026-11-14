package com.moimzang.domain.memberlist.service;

import com.moimzang.domain.attendance.repository.RoundAttendeeRepository;
import com.moimzang.domain.attendance.repository.ScheduleAttendeeRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.memberlist.dto.request.MemberListCreateRequest;
import com.moimzang.domain.memberlist.dto.request.MemberListUpdateRequest;
import com.moimzang.domain.memberlist.dto.response.MemberListCreateResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListDetailResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListFieldsResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListListResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListSummaryResponse;
import com.moimzang.domain.memberlist.dto.response.MemberListUpdateResponse;
import com.moimzang.domain.memberlist.entity.MemberList;
import com.moimzang.domain.memberlist.exception.MemberListErrorCode;
import com.moimzang.domain.memberlist.repository.MemberListRepository;
import com.moimzang.domain.memberlist.repository.MemberRepository;
import com.moimzang.domain.settlement.repository.SettlementTargetRepository;
import com.moimzang.global.exception.CustomException;
import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MemberListService {

    // 마감 여부는 한국 시간 기준으로 판단한다
    private static final ZoneId SERVICE_ZONE = ZoneId.of("Asia/Seoul");

    // 등록 링크 경로. 서버는 프론트 도메인을 모르므로 경로만 내려주고 프론트가 도메인을 붙인다
    private static final String JOIN_PATH_PREFIX = "/join/";

    // 토큰은 링크를 아는 사람만 등록할 수 있게 하는 값이라 추측하기 어려운 난수로 만든다
    private static final int JOIN_TOKEN_BYTES = 12;
    private static final int JOIN_TOKEN_MAX_ATTEMPTS = 5;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MemberListRepository memberListRepository;
    private final MemberRepository memberRepository;
    private final GroupTermRepository groupTermRepository;
    private final ScheduleAttendeeRepository scheduleAttendeeRepository;
    private final RoundAttendeeRepository roundAttendeeRepository;
    private final SettlementTargetRepository settlementTargetRepository;

    // 명단방을 만들고 등록 링크 토큰을 발급한다
    @Transactional
    public MemberListCreateResponse createMemberList(Long termId, MemberListCreateRequest request) {
        log.info("[명단방 생성] 생성을 시작합니다. termId = {}, name = {}", termId, request.getName());

        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[명단방 생성] 존재하지 않는 기수입니다. termId = {}", termId);
                    return new CustomException(MemberListErrorCode.MEMBER_LIST_404_GROUP_TERM_NOT_FOUND);
                });

        MemberList memberList = memberListRepository.save(MemberList.create(groupTerm, request.getName().strip(),
                endOfDay(request.getRegisterDeadline()), defaultOn(request.getCollectSchool()),
                defaultOn(request.getCollectStudentId()), defaultOn(request.getCollectPart()),
                defaultOn(request.getCollectGrade()), defaultOn(request.getCollectPhone()), issueJoinToken()));

        log.info("[명단방 생성] 생성을 완료했습니다. memberListId = {}", memberList.getId());
        return MemberListCreateResponse.builder()
                .memberListId(memberList.getId())
                .joinToken(memberList.getJoinToken())
                .joinUrl(JOIN_PATH_PREFIX + memberList.getJoinToken())
                .build();
    }

    // 기수의 명단방 목록을 등록 인원과 함께 조회한다
    public MemberListListResponse listMemberLists(Long termId) {
        log.info("[명단방 목록 조회] 조회를 시작합니다. termId = {}", termId);

        if (!groupTermRepository.existsById(termId)) {
            log.warn("[명단방 목록 조회] 존재하지 않는 기수입니다. termId = {}", termId);
            throw new CustomException(MemberListErrorCode.MEMBER_LIST_404_GROUP_TERM_NOT_FOUND);
        }
        List<MemberList> memberLists = memberListRepository.findByGroupTermIdOrderByIdAsc(termId);
        Map<Long, Long> counts = memberLists.isEmpty() ? Map.of()
                : memberRepository.countByMemberListIds(memberLists.stream().map(MemberList::getId).toList())
                        .stream()
                        .collect(Collectors.toMap(MemberRepository.MemberCountRow::getMemberListId,
                                MemberRepository.MemberCountRow::getMemberCount));
        LocalDateTime now = LocalDateTime.now(SERVICE_ZONE);

        List<MemberListSummaryResponse> summaries = memberLists.stream()
                .map(list -> MemberListSummaryResponse.builder()
                        .memberListId(list.getId())
                        .name(list.getName())
                        .memberCount(counts.getOrDefault(list.getId(), 0L))
                        .status(list.effectiveStatus(now))
                        .registerDeadline(toDate(list.getRegisterDeadline()))
                        .build())
                .toList();

        log.info("[명단방 목록 조회] 조회를 완료했습니다. termId = {}, count = {}", termId, summaries.size());
        return MemberListListResponse.builder()
                .memberLists(summaries)
                .build();
    }

    // 링크 관리 화면에 쓸 명단방 상세를 조회한다
    public MemberListDetailResponse getMemberList(Long memberListId) {
        log.info("[명단방 상세 조회] 조회를 시작합니다. memberListId = {}", memberListId);

        MemberList memberList = findMemberList(memberListId, "명단방 상세 조회");
        long memberCount = memberRepository.countByMemberListId(memberListId);

        log.info("[명단방 상세 조회] 조회를 완료했습니다. memberListId = {}, memberCount = {}", memberListId, memberCount);
        return MemberListDetailResponse.builder()
                .memberListId(memberList.getId())
                .name(memberList.getName())
                .registerDeadline(toDate(memberList.getRegisterDeadline()))
                .status(memberList.effectiveStatus(LocalDateTime.now(SERVICE_ZONE)))
                .memberCount(memberCount)
                .joinUrl(JOIN_PATH_PREFIX + memberList.getJoinToken())
                .isLinkClosed(memberList.getIsLinkClosed())
                .fields(MemberListFieldsResponse.builder()
                        .school(memberList.getCollectSchool())
                        .studentId(memberList.getCollectStudentId())
                        .part(memberList.getCollectPart())
                        .grade(memberList.getCollectGrade())
                        .phone(memberList.getCollectPhone())
                        .build())
                .build();
    }

    // 명단방 이름·마감일·수집 항목을 수정한다. 같은 명단방과 링크를 그대로 쓴다
    @Transactional
    public MemberListUpdateResponse updateMemberList(Long memberListId, MemberListUpdateRequest request) {
        log.info("[명단방 수정] 수정을 시작합니다. memberListId = {}", memberListId);

        MemberList memberList = findMemberList(memberListId, "명단방 수정");
        memberList.update(request.getName() == null ? null : request.getName().strip(),
                request.getRegisterDeadline() == null ? null : endOfDay(request.getRegisterDeadline()),
                request.getCollectSchool(), request.getCollectStudentId(), request.getCollectPart(),
                request.getCollectGrade(), request.getCollectPhone());

        log.info("[명단방 수정] 수정을 완료했습니다. memberListId = {}", memberListId);
        return MemberListUpdateResponse.builder()
                .memberListId(memberList.getId())
                .build();
    }

    // 명단방을 삭제한다. 부원과, 그 부원으로 등록된 일정 참가자·출석·정산 기록까지 함께 지우고 링크도 쓸 수 없게 된다.
    // FK가 전부 RESTRICT라 출석·정산 → 일정 참가자 → 부원 → 명단방 순서로 지운다
    @Transactional
    public void deleteMemberList(Long memberListId) {
        log.info("[명단방 삭제] 삭제를 시작합니다. memberListId = {}", memberListId);

        findMemberList(memberListId, "명단방 삭제");
        roundAttendeeRepository.deleteAllByMemberListId(memberListId);
        settlementTargetRepository.deleteAllByMemberListId(memberListId);
        scheduleAttendeeRepository.deleteAllByMemberListId(memberListId);
        memberRepository.deleteAllByMemberListId(memberListId);
        memberListRepository.deleteById(memberListId);

        log.info("[명단방 삭제] 삭제를 완료했습니다. memberListId = {}", memberListId);
    }

    private MemberList findMemberList(Long memberListId, String action) {
        return memberListRepository.findById(memberListId)
                .orElseThrow(() -> {
                    log.warn("[{}] 존재하지 않는 명단방입니다. memberListId = {}", action, memberListId);
                    return new CustomException(MemberListErrorCode.MEMBER_LIST_404_NOT_FOUND);
                });
    }

    private String issueJoinToken() {
        for (int attempt = 0; attempt < JOIN_TOKEN_MAX_ATTEMPTS; attempt++) {
            byte[] bytes = new byte[JOIN_TOKEN_BYTES];
            RANDOM.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            if (!memberListRepository.existsByJoinToken(token)) {
                return token;
            }
        }
        // 96비트 난수가 다섯 번 연속 겹치는 건 사실상 불가능해 여기까지 오면 서버 문제로 본다
        throw new IllegalStateException("등록 링크 토큰을 발급하지 못했습니다.");
    }

    private LocalDateTime endOfDay(LocalDate date) {
        return date.atTime(LocalTime.of(23, 59, 59));
    }

    private LocalDate toDate(LocalDateTime dateTime) {
        return dateTime == null ? null : dateTime.toLocalDate();
    }

    private boolean defaultOn(Boolean value) {
        return value == null || value;
    }
}
