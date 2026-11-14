package com.moimzang.domain.group.mapper;

import com.moimzang.domain.group.dto.response.GroupDetailResponse;
import com.moimzang.domain.group.dto.response.GroupTermResponse;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface GroupMapper {

    // 기수 1건을 변환한다
    @Mapping(target = "termId", source = "id")
    GroupTermResponse toTermResponse(GroupTerm term);

    // 기수 목록을 변환한다
    List<GroupTermResponse> toTermResponses(List<GroupTerm> terms);

    // 그룹 정보와 기수 목록을 묶어 변환한다. 기수 목록은 Service가 별도로 조회해 넘긴다
    @Mapping(target = "groupId", source = "group.id")
    @Mapping(target = "name", source = "group.name")
    GroupDetailResponse toDetailResponse(ClubGroup group, List<GroupTerm> terms);
}
