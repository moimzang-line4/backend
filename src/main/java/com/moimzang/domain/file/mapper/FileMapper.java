package com.moimzang.domain.file.mapper;

import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileSummaryResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.service.FileContentUrl;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FileMapper {

    @Mapping(target = "fileId", source = "id")
    FileUploadResponse toUploadResponse(File file);

    @Mapping(target = "fileId", source = "id")
    FileRestoreResponse toRestoreResponse(File file);

    @Mapping(target = "fileId", source = "id")
    @Mapping(target = "scheduleId", source = "schedule.id")
    @Mapping(target = "thumbnailUrl", source = "file", qualifiedByName = "thumbnailUrl")
    FileSummaryResponse toSummaryResponse(File file);

    // 썸네일이 없으면 null을 준다 — 프론트는 그때 파일 타입 아이콘을 그린다
    @Named("thumbnailUrl")
    default String toThumbnailUrl(File file) {
        return file.getThumbnailKey() == null ? null : FileContentUrl.ofThumbnail(file.getId());
    }

    List<FileSummaryResponse> toSummaryResponses(List<File> files);
}
