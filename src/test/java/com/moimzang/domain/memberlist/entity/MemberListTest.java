package com.moimzang.domain.memberlist.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.memberlist.enums.MemberListStatus;
import java.time.LocalDateTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class MemberListTest {

    @Test
    void getStatus_마감_일시가_지났으면_저장된_값과_상관없이_COMPLETED() {
        MemberList memberList = memberList(now().minusMinutes(1));

        assertThat(ReflectionTestUtils.getField(memberList, "status")).isEqualTo(MemberListStatus.RECRUITING);
        assertThat(memberList.getStatus()).isEqualTo(MemberListStatus.COMPLETED);
    }

    @Test
    void getStatus_마감_전이면_RECRUITING() {
        assertThat(memberList(now().plusDays(1)).getStatus()).isEqualTo(MemberListStatus.RECRUITING);
    }

    @Test
    void getStatus_링크를_닫았으면_마감_전이어도_COMPLETED() {
        MemberList memberList = memberList(now().plusDays(1));
        ReflectionTestUtils.setField(memberList, "isLinkClosed", true);

        assertThat(memberList.getStatus()).isEqualTo(MemberListStatus.COMPLETED);
    }

    @Test
    void getStatus_마감_일시를_미래로_늘리면_다시_RECRUITING() {
        MemberList memberList = memberList(now().minusDays(1));
        memberList.update(null, now().plusDays(3), null, null, null, null, null);

        assertThat(memberList.getStatus()).isEqualTo(MemberListStatus.RECRUITING);
    }

    private MemberList memberList(LocalDateTime deadline) {
        return MemberList.create(mock(GroupTerm.class), "2026년 2학기 음샘", deadline,
                true, true, true, true, true, "token");
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneId.of("Asia/Seoul"));
    }
}
