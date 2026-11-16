package com.moimzang.domain.file.service;

import com.moimzang.domain.file.dto.response.FileContentTarget;
import com.moimzang.domain.file.dto.response.FileDownloadUrlResponse;
import com.moimzang.domain.file.dto.response.FileListResponse;
import com.moimzang.domain.file.dto.response.FilePolicyResponse;
import com.moimzang.domain.file.dto.response.FileRestoreResponse;
import com.moimzang.domain.file.dto.response.FileSummaryResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.dto.response.FileZipEntry;
import com.moimzang.domain.file.dto.response.FileZipTarget;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileCategoryFilter;
import com.moimzang.domain.file.enums.FileContentVariant;
import com.moimzang.domain.file.enums.FileDownloadMode;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.file.exception.FileErrorCode;
import com.moimzang.domain.file.mapper.FileMapper;
import com.moimzang.domain.file.repository.FileRepository;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.domain.group.repository.GroupTermRepository;
import com.moimzang.domain.schedule.entity.Schedule;
import com.moimzang.domain.schedule.repository.ScheduleRepository;
import com.moimzang.global.cursor.CursorCodec;
import com.moimzang.global.cursor.CursorPageConstants;
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import com.moimzang.global.text.SingleLineText;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FileService {

    private final FileRepository fileRepository;
    private final GroupTermRepository groupTermRepository;
    private final ScheduleRepository scheduleRepository;
    private final FileMapper fileMapper;
    private final FileStorage fileStorage;
    private final FileThumbnailGenerator thumbnailGenerator;

    public FilePolicyResponse getPolicy() {
        log.info("[업로드 정책 조회] 조회를 시작합니다.");
        FilePolicyResponse response = FilePolicyResponse.builder()
                .allowedExtensions(FileUploadPolicy.ALLOWED_EXTENSIONS.stream().sorted().toList())
                .maxSizeBytes(FileUploadPolicy.MAX_SIZE_BYTES)
                .build();
        log.info("[업로드 정책 조회] 조회를 완료했습니다. maxSizeBytes = {}", FileUploadPolicy.MAX_SIZE_BYTES);
        return response;
    }

    @Transactional
    public FileUploadResponse upload(Long termId, MultipartFile file, String name, FileCategory category, Long scheduleId) {
        log.info("[파일 업로드] 업로드를 시작합니다. termId = {}, category = {}, scheduleId = {}", termId, category, scheduleId);

        // 필수값 검증 — 파일이 비었거나 이름이 없으면 더 진행하지 않는다
        if (file.isEmpty() || name == null || name.isBlank()) {
            log.warn("[파일 업로드] 필수 값이 비어 있습니다. termId = {}, fileEmpty = {}", termId, file.isEmpty());
            throw new CustomException(FileErrorCode.FILE_400_REQUIRED_FIELD_MISSING);
        }

        // 이름 길이 검증 — 컬럼 길이를 넘기면 저장 단계에서 깨지므로 여기서 끊는다
        if (name.length() > FileUploadPolicy.MAX_NAME_LENGTH) {
            log.warn("[파일 업로드] 자료명이 너무 깁니다. termId = {}, nameLength = {}", termId, name.length());
            throw new CustomException(FileErrorCode.FILE_400_NAME_TOO_LONG);
        }

        // 자료명은 한 줄 입력란이다 — 길이 검증을 통과한 뒤에 편다(정규화는 길이를 늘리지 않는다)
        String normalizedName = SingleLineText.normalize(name);

        // 확장자 검증 — 허용 목록(FileUploadPolicy)에 없으면 거부
        String extension = extractExtension(file.getOriginalFilename());
        if (!FileUploadPolicy.isAllowed(extension)) {
            log.warn("[파일 업로드] 허용하지 않는 확장자입니다. termId = {}, extension = {}", termId, extension);
            throw new CustomException(FileErrorCode.FILE_400_EXTENSION_NOT_ALLOWED);
        }
        // 용량 검증 — Tomcat의 multipart 상한과 별개로 이 상수 하나로 기준을 통일한다
        if (file.getSize() > FileUploadPolicy.MAX_SIZE_BYTES) {
            log.warn("[파일 업로드] 허용 용량을 초과했습니다. termId = {}, size = {}", termId, file.getSize());
            throw new CustomException(FileErrorCode.FILE_413_SIZE_EXCEEDED);
        }

        // 업로드 대상 기수 조회
        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[파일 업로드] 기수를 찾을 수 없습니다. termId = {}", termId);
                    return new CustomException(FileErrorCode.FILE_404_GROUP_TERM_NOT_FOUND);
                });

        // 일정 조회 — 장부 파일은 일정과 무관하므로 scheduleId가 와도 무시한다
        Schedule schedule = null;
        if (category != FileCategory.LEDGER_FILE && scheduleId != null) {
            schedule = scheduleRepository.findById(scheduleId)
                    .orElseThrow(() -> {
                        log.warn("[파일 업로드] 일정을 찾을 수 없습니다. scheduleId = {}", scheduleId);
                        return new CustomException(FileErrorCode.FILE_404_SCHEDULE_NOT_FOUND);
                    });
            if (!schedule.getGroupTerm().getId().equals(termId)) {
                log.warn("[파일 업로드] 일정이 해당 기수에 속하지 않습니다. termId = {}, scheduleId = {}", termId, scheduleId);
                throw new CustomException(FileErrorCode.FILE_400_SCHEDULE_TERM_MISMATCH);
            }
        }

        // 스토리지에 실제 파일 저장 — 여기까지 검증을 끝내고 나서 써야 실패해도 고아 파일이 안 남는다
        String storageKey = buildStorageKey(extension);
        try (InputStream content = file.getInputStream()) {
            fileStorage.store(storageKey, content, file.getSize(), file.getContentType());
        } catch (IOException e) {
            log.error("[파일 업로드] 저장 중 입출력 오류가 발생했습니다. termId = {}, storageKey = {}", termId, storageKey, e);
            throw new UncheckedIOException("파일 저장 중 입출력 오류가 발생했다", e);
        }

        // 썸네일 생성 — 실패해도 업로드는 그대로 성공시킨다(그리드 표시를 위한 최적화일 뿐이다)
        String thumbnailKey = storeThumbnail(file, extension, storageKey);

        // 장부 파일은 기수당 1개만 유지한다 — 기존 파일은 소프트 삭제만 하고 실제 스토리지 파일은 남겨둔다
        if (category == FileCategory.LEDGER_FILE) {
            fileRepository.findActiveFile(termId, FileCategory.LEDGER_FILE)
                    .ifPresent(existing -> {
                        log.info("[파일 업로드] 기존 장부 파일을 교체합니다. termId = {}, previousFileId = {}", termId, existing.getId());
                        existing.softDelete();
                    });
        }

        // 새 레코드 저장 및 응답 변환
        File saved = fileRepository.save(
                File.upload(groupTerm, schedule, normalizedName, extension, file.getSize(), category,
                        storageKey, thumbnailKey));
        log.info("[파일 업로드] 업로드를 완료했습니다. termId = {}, fileId = {}", termId, saved.getId());
        return fileMapper.toUploadResponse(saved);
    }

    public FileListResponse listFiles(Long termId, FileCategoryFilter category, String keyword, FileSortOption sort,
                                       Long scheduleId, String cursor, Integer size) {
        log.info("[문서함 목록 조회] 조회를 시작합니다. termId = {}, category = {}, keyword = {}, scheduleId = {}, cursor = {}",
                termId, category, keyword, scheduleId, cursor);

        // 조회 대상 기수 존재 검증 — upload()/getZipTargets()와 동일하게 없는 term은 404로 끊는다
        groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[문서함 목록 조회] 기수를 찾을 수 없습니다. termId = {}", termId);
                    return new CustomException(FileErrorCode.FILE_404_GROUP_TERM_NOT_FOUND);
                });

        int limit = CursorPageConstants.resolveSize(size);

        // cursor 디코딩 — sort가 NAME이면 name 기준, 아니면 createdAt(epoch millis) 기준으로 인코딩돼 있다
        LocalDateTime cursorCreatedAt = null;
        String cursorName = null;
        Long cursorId = null;
        if (cursor != null) {
            try {
                CursorCodec decoded = CursorCodec.decode(cursor);
                cursorId = decoded.getId();
                if (sort == FileSortOption.NAME) {
                    cursorName = decoded.getSortValue();
                } else {
                    cursorCreatedAt = Instant.ofEpochMilli(Long.parseLong(decoded.getSortValue()))
                            .atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
            } catch (RuntimeException e) {
                log.warn("[문서함 목록 조회] cursor 형식이 올바르지 않습니다. termId = {}, cursor = {}", termId, cursor);
                throw new CustomException(FileErrorCode.FILE_400_INVALID_CURSOR);
            }
        }

        // size + 1건을 조회해서 그중 마지막 1건이 있으면 "다음 페이지가 있다"는 뜻이다
        List<File> fetched = fileRepository.search(
                termId, category.toFileCategoryOrNull(), keyword, scheduleId, sort,
                cursorCreatedAt, cursorName, cursorId, limit + 1);

        boolean hasNext = fetched.size() > limit;
        List<File> page = hasNext ? fetched.subList(0, limit) : fetched;

        List<FileSummaryResponse> summaries = fileMapper.toSummaryResponses(page);
        String nextCursor = hasNext ? buildCursor(sort, page.get(page.size() - 1)) : null;

        // 첫 페이지(cursor 없음)에서만 count 쿼리를 날린다 — 스크롤마다 다시 세지 않는다
        Long totalCount = cursor == null
                ? fileRepository.count(termId, category.toFileCategoryOrNull(), keyword, scheduleId)
                : null;

        log.info("[문서함 목록 조회] 조회를 완료했습니다. termId = {}, count = {}, hasNext = {}", termId, summaries.size(), hasNext);
        return FileListResponse.builder()
                .files(summaries)
                .nextCursor(nextCursor)
                .totalCount(totalCount)
                .build();
    }

    private String buildCursor(FileSortOption sort, File last) {
        String sortValue = sort == FileSortOption.NAME
                ? last.getName()
                : String.valueOf(last.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli());
        return CursorCodec.encode(sortValue, last.getId());
    }

    public FileDownloadUrlResponse getDownloadUrl(Long fileId, FileDownloadMode mode) {
        log.info("[다운로드 URL 발급] 발급을 시작합니다. fileId = {}, mode = {}", fileId, mode);

        // 대상 파일 조회 — 소프트 삭제된 파일은 URL을 알고 있어도 열 수 없게 막는다
        File file = fileRepository.findActiveById(fileId)
                .orElseThrow(() -> {
                    log.warn("[다운로드 URL 발급] 파일을 찾을 수 없습니다. fileId = {}", fileId);
                    return new CustomException(FileErrorCode.FILE_404_NOT_FOUND);
                });

        // URL 조립 — 우리 서버의 콘텐츠 서빙 엔드포인트를 가리킨다. mode를 쿼리로 그대로 넘겨서
        // 실제 다운로드 시점에 Content-Disposition(attachment/inline)을 거기서 결정하게 한다
        FileDownloadUrlResponse response = FileDownloadUrlResponse.builder()
                .url(FileContentUrl.of(file.getId(), mode))
                .build();

        log.info("[다운로드 URL 발급] 발급을 완료했습니다. fileId = {}", fileId);
        return response;
    }

    public FileContentTarget getFileContent(Long fileId, FileContentVariant variant) {
        log.info("[파일 콘텐츠 서빙] 대상 조회를 시작합니다. fileId = {}, variant = {}", fileId, variant);

        // 소프트 삭제된 파일은 URL을 알고 있어도 열 수 없게 막는다
        File file = fileRepository.findActiveById(fileId)
                .orElseThrow(() -> {
                    log.warn("[파일 콘텐츠 서빙] 파일을 찾을 수 없습니다. fileId = {}", fileId);
                    return new CustomException(FileErrorCode.FILE_404_NOT_FOUND);
                });

        if (variant == FileContentVariant.THUMBNAIL) {
            // 원본으로 조용히 되돌리지 않는다 — pdf에 썸네일을 요청했는데 20MB가 날아가면 더 나쁘다
            if (file.getThumbnailKey() == null) {
                log.warn("[파일 콘텐츠 서빙] 썸네일이 없는 자료입니다. fileId = {}", fileId);
                throw new CustomException(FileErrorCode.FILE_404_THUMBNAIL_NOT_FOUND);
            }

            log.info("[파일 콘텐츠 서빙] 썸네일 대상 조회를 완료했습니다. fileId = {}", fileId);
            return FileContentTarget.builder()
                    .name(file.getName())
                    .extension(extractExtension(file.getThumbnailKey()))
                    .storageKey(file.getThumbnailKey())
                    .build();
        }

        log.info("[파일 콘텐츠 서빙] 대상 조회를 완료했습니다. fileId = {}", fileId);
        return FileContentTarget.builder()
                .name(file.getName())
                .extension(file.getExtension())
                .storageKey(file.getStorageKey())
                .build();
    }

    // 바이트 전송은 파일 크기에 따라 오래 걸리는데 DB를 쓰지 않으므로, 클래스 레벨 트랜잭션에서 빼낸다
    // — 조회는 getFileContent가 이미 끝내 놨다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void writeFileContentTo(FileContentTarget target, OutputStream out) throws IOException {
        log.info("[파일 콘텐츠 서빙] 전송을 시작합니다. storageKey = {}", target.getStorageKey());

        try (InputStream in = fileStorage.open(target.getStorageKey())) {
            in.transferTo(out);
        } catch (IOException e) {
            log.error("[파일 콘텐츠 서빙] 전송 중 오류가 발생했습니다. storageKey = {}", target.getStorageKey(), e);
            throw e;
        }

        log.info("[파일 콘텐츠 서빙] 전송을 완료했습니다. storageKey = {}", target.getStorageKey());
    }

    @Transactional
    public void deleteFile(Long fileId) {
        log.info("[파일 삭제] 삭제를 시작합니다. fileId = {}", fileId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.warn("[파일 삭제] 파일을 찾을 수 없습니다. fileId = {}", fileId);
                    return new CustomException(FileErrorCode.FILE_404_NOT_FOUND);
                });

        // 이미 삭제된 파일에 다시 들어온 요청은 성공으로 흘린다 — 중복 클릭이나 재시도가 실패로
        // 보이면 안 되고, deleted_at을 덮어쓰면 처음 삭제한 시점이 어긋난다
        if (file.getDeletedAt() != null) {
            log.info("[파일 삭제] 이미 삭제된 파일입니다. fileId = {}", fileId);
            return;
        }
        file.softDelete();

        log.info("[파일 삭제] 삭제를 완료했습니다. fileId = {}", fileId);
    }

    @Transactional
    public FileRestoreResponse restoreFile(Long fileId) {
        log.info("[파일 복구] 복구를 시작합니다. fileId = {}", fileId);

        File file = fileRepository.findById(fileId)
                .orElseThrow(() -> {
                    log.warn("[파일 복구] 파일을 찾을 수 없습니다. fileId = {}", fileId);
                    return new CustomException(FileErrorCode.FILE_404_NOT_FOUND);
                });
        // 장부는 기수당 1개만 유지한다 — 교체로 밀려난 장부를 되살리면 활성 장부가 2개가 되고,
        // 그 뒤의 장부 업로드가 기존 장부를 찾는 단계에서 터진다
        if (file.getCategory() == FileCategory.LEDGER_FILE) {
            fileRepository.findActiveFile(file.getGroupTerm().getId(), FileCategory.LEDGER_FILE)
                    .filter(active -> !active.getId().equals(fileId))
                    .ifPresent(active -> {
                        log.warn("[파일 복구] 이미 활성 장부가 있어 복구할 수 없습니다. fileId = {}, activeFileId = {}",
                                fileId, active.getId());
                        throw new CustomException(FileErrorCode.FILE_409_LEDGER_ALREADY_EXISTS);
                    });
        }

        // 이미 복구된 상태여도 에러 없이 통과한다 — 기간 제한이 없어 막을 이유가 없다
        file.restore();

        log.info("[파일 복구] 복구를 완료했습니다. fileId = {}", fileId);
        return fileMapper.toRestoreResponse(file);
    }

    public FileZipTarget getZipTargets(Long termId, FileCategoryFilter category, String keyword) {
        log.info("[문서함 전체 다운로드] 대상 조회를 시작합니다. termId = {}, category = {}, keyword = {}", termId, category, keyword);

        // 기수 조회 — ZIP 파일명(「{기수}자료{N}개.zip」)에 쓸 이름이 필요하다
        GroupTerm groupTerm = groupTermRepository.findById(termId)
                .orElseThrow(() -> {
                    log.warn("[문서함 전체 다운로드] 기수를 찾을 수 없습니다. termId = {}", termId);
                    return new CustomException(FileErrorCode.FILE_404_GROUP_TERM_NOT_FOUND);
                });

        // 조건에 맞는 파일 전체 조회 — ZIP은 "전부 내려받기"라 페이지네이션 대상이 아니다.
        // 정렬 기준이 스펙에 없어서 ZIP 안 파일 순서를 예측 가능하게 이름순으로 고정한다
        List<File> files = fileRepository.searchAll(termId, category.toFileCategoryOrNull(), keyword);
        if (files.isEmpty()) {
            log.warn("[문서함 전체 다운로드] 다운로드할 파일이 없습니다. termId = {}", termId);
            throw new CustomException(FileErrorCode.FILE_404_NO_DOWNLOADABLE_FILES);
        }

        // ZIP에 쓸 엔트리 목록 조립(이름 중복 처리 포함)
        List<FileZipEntry> entries = buildUniqueEntries(files);
        log.info("[문서함 전체 다운로드] 대상 조회를 완료했습니다. termId = {}, fileCount = {}", termId, entries.size());
        return FileZipTarget.builder()
                .groupTermName(groupTerm.getName())
                .entries(entries)
                .build();
    }

    // 압축·전송 구간도 DB를 쓰지 않는다 — 대상 목록은 getZipTargets가 이미 조회해 DTO로 넘겨준다
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void writeZipTo(FileZipTarget target, OutputStream out) throws IOException {
        int fileCount = target.getEntries().size();
        log.info("[문서함 전체 다운로드] 압축을 시작합니다. fileCount = {}", fileCount);

        try (ZipOutputStream zos = new ZipOutputStream(out)) {
            for (FileZipEntry entry : target.getEntries()) {
                zos.putNextEntry(new ZipEntry(entry.getEntryName()));
                try (InputStream in = fileStorage.open(entry.getStorageKey())) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (IOException e) {
            log.error("[문서함 전체 다운로드] 압축 중 오류가 발생했습니다. fileCount = {}", fileCount, e);
            throw e;
        }

        log.info("[문서함 전체 다운로드] 압축을 완료했습니다. fileCount = {}", fileCount);
    }

    // 같은 이름+확장자 파일이 여럿이면 ZipEntry가 중복 이름을 거부하므로 번호를 붙여 피한다
    private List<FileZipEntry> buildUniqueEntries(List<File> files) {
        Map<String, Integer> usedCount = new HashMap<>();
        List<FileZipEntry> entries = new ArrayList<>();
        for (File file : files) {
            String base = file.getName() + "." + file.getExtension();
            int count = usedCount.merge(base, 1, Integer::sum);
            String entryName = count == 1 ? base : "%s (%d).%s".formatted(file.getName(), count - 1, file.getExtension());
            entries.add(FileZipEntry.builder().storageKey(file.getStorageKey()).entryName(entryName).build());
        }
        return entries;
    }

    private String extractExtension(String originalFilename) {
        if (originalFilename == null) {
            return "";
        }
        int dot = originalFilename.lastIndexOf('.');
        if (dot < 0 || dot == originalFilename.length() - 1) {
            return "";
        }
        return originalFilename.substring(dot + 1).toLowerCase();
    }

    private String buildStorageKey(String extension) {
        LocalDate today = LocalDate.now();
        return "files/%d/%02d/%s.%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(), extension);
    }

    // 썸네일을 만들어 저장하고 그 키를 돌려준다. 대상이 아니거나 실패하면 null을 돌려주고
    // 업로드는 계속 진행한다 — 썸네일이 없으면 프론트가 파일 타입 아이콘을 그리면 된다
    private String storeThumbnail(MultipartFile file, String extension, String storageKey) {
        if (!thumbnailGenerator.supports(extension)) {
            return null;
        }

        // 원본 저장이 이미 스트림을 한 번 소비했으므로 새로 연다. 20MB를 통째로 메모리에
        // 들고 있지 않으려고 바이트 배열 대신 스트림을 다시 받는다
        try (InputStream content = file.getInputStream()) {
            Optional<FileThumbnail> generated = thumbnailGenerator.generate(content, extension);
            if (generated.isEmpty()) {
                return null;
            }

            FileThumbnail thumbnail = generated.get();
            String thumbnailKey = buildThumbnailKey(storageKey, thumbnail.extension());
            fileStorage.store(thumbnailKey, new ByteArrayInputStream(thumbnail.content()),
                    thumbnail.content().length, thumbnail.contentType());
            return thumbnailKey;
        } catch (IOException | RuntimeException e) {
            log.warn("[파일 업로드] 썸네일을 만들지 못해 원본만 저장합니다. storageKey = {}", storageKey, e);
            return null;
        }
    }

    // 원본 키 옆에 붙여 둔다 — 같은 디렉터리에 있어야 어느 파일의 썸네일인지 추적하기 쉽다
    private String buildThumbnailKey(String storageKey, String thumbnailExtension) {
        int dot = storageKey.lastIndexOf('.');
        String withoutExtension = dot < 0 ? storageKey : storageKey.substring(0, dot);
        return "%s_thumb.%s".formatted(withoutExtension, thumbnailExtension);
    }
}
