package com.moimzang.domain.file.mapper;

import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileSummaryResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.entity.File;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface FileMapper {

    @Mapping(target = "fileId", source = "id")
    FileUploadResponse toUploadResponse(File file);

    @Mapping(target = "fileId", source = "id")
    FileRestoreResponse toRestoreResponse(File file);

    @Mapping(target = "fileId", source = "id")
    @Mapping(target = "scheduleId", source = "schedule.id")
    FileSummaryResponse toSummaryResponse(File file);

    List<FileSummaryResponse> toSummaryResponses(List<File> files);
}
