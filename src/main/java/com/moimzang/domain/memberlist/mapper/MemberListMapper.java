package com.moimzang.domain.memberlist.mapper;

import com.moimzang.domain.memberlist.dto.response.MemberResponse;
import com.moimzang.domain.memberlist.entity.Member;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface MemberListMapper {

    // 부원 1건을 명단 조회 표의 한 줄로 변환한다
    @Mapping(target = "memberId", source = "id")
    MemberResponse toMemberResponse(Member member);

    // 부원 목록을 변환한다
    List<MemberResponse> toMemberResponses(List<Member> members);
}
