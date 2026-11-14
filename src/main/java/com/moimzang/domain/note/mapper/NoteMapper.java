package com.moimzang.domain.note.mapper;

import com.moimzang.domain.note.dto.response.NoteResponse;
import com.moimzang.domain.note.entity.OperationNote;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface NoteMapper {

    // 메모 1건을 목록 카드 응답으로 변환한다
    @Mapping(target = "noteId", source = "id")
    NoteResponse toResponse(OperationNote note);

    // 메모 목록을 변환한다
    List<NoteResponse> toResponses(List<OperationNote> notes);
}
