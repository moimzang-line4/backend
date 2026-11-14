package com.moimzang.domain.memberlist.repository;

import static com.moimzang.domain.memberlist.entity.QMember.member;
import static com.moimzang.domain.memberlist.entity.QMemberList.memberList;

import com.moimzang.domain.memberlist.entity.Member;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.util.StringUtils;

@RequiredArgsConstructor
public class MemberRepositoryImpl implements MemberRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    // 기수에 명단방이 여러 개일 수 있어 어느 방의 부원인지 응답에 써야 한다 — memberList를 fetch join한다
    @Override
    public List<Member> searchForAssistant(Long groupTermId, String keyword, String part, int limit) {
        return queryFactory.selectFrom(member)
                .join(member.memberList, memberList).fetchJoin()
                .where(memberList.groupTerm.id.eq(groupTermId))
                .where(nameContains(keyword), partContains(part))
                .orderBy(member.name.asc(), member.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countForAssistant(Long groupTermId, String keyword, String part) {
        Long count = queryFactory.select(member.count())
                .from(member)
                .join(member.memberList, memberList)
                .where(memberList.groupTerm.id.eq(groupTermId))
                .where(nameContains(keyword), partContains(part))
                .fetchOne();
        return count == null ? 0 : count;
    }

    @Override
    public List<Member> searchInMemberList(Long memberListId, String keyword, LocalDateTime cursorCreatedAt,
                                           Long cursorId, int limit) {
        return queryFactory.selectFrom(member)
                .where(member.memberList.id.eq(memberListId))
                .where(rosterKeywordContains(keyword), cursorCondition(cursorCreatedAt, cursorId))
                .orderBy(member.createdAt.asc(), member.id.asc())
                .limit(limit)
                .fetch();
    }

    @Override
    public long countInMemberList(Long memberListId, String keyword) {
        Long count = queryFactory.select(member.count())
                .from(member)
                .where(member.memberList.id.eq(memberListId))
                .where(rosterKeywordContains(keyword))
                .fetchOne();
        return count == null ? 0 : count;
    }

    // 명단 조회 검색창은 이름·학과·전화번호를 함께 찾는다
    private BooleanExpression rosterKeywordContains(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        return member.name.containsIgnoreCase(keyword)
                .or(member.department.containsIgnoreCase(keyword))
                .or(member.phone.contains(keyword));
    }

    // (createdAt, id) 복합 비교. 오름차순이라 gt를 쓰고, 같은 초에 등록한 부원은 id로 순서를 가른다
    private BooleanExpression cursorCondition(LocalDateTime cursorCreatedAt, Long cursorId) {
        if (cursorId == null) {
            return null;
        }
        return member.createdAt.gt(cursorCreatedAt)
                .or(member.createdAt.eq(cursorCreatedAt).and(member.id.gt(cursorId)));
    }

    private BooleanExpression nameContains(String keyword) {
        return StringUtils.hasText(keyword) ? member.name.containsIgnoreCase(keyword) : null;
    }

    // 사용자는 '기획파트'처럼 뒤에 말을 붙여 부르고 저장된 값은 '기획'이라 일치 비교로는 걸리지 않는다
    private BooleanExpression partContains(String part) {
        return StringUtils.hasText(part) ? member.part.containsIgnoreCase(part) : null;
    }
}
