package com.moimzang.domain.file.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.moimzang.domain.file.dto.response.FileDownloadUrlResponse;
import com.moimzang.domain.file.dto.response.FileListResponse;
import com.moimzang.domain.file.dto.response.FileUploadResponse;
import com.moimzang.domain.file.dto.response.FileZipEntry;
import com.moimzang.domain.file.dto.response.FileZipTarget;
import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileCategoryFilter;
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
import com.moimzang.global.exception.CustomException;
import com.moimzang.global.storage.FileStorage;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
class FileServiceTest {

    @Mock
    private FileRepository fileRepository;

    @Mock
    private GroupTermRepository groupTermRepository;

    @Mock
    private ScheduleRepository scheduleRepository;

    @Mock
    private FileStorage fileStorage;

    @Spy
    private FileMapper fileMapper = Mappers.getMapper(FileMapper.class);

    private FileService fileService;

    @BeforeEach
    void setUp() {
        fileService = new FileService(fileRepository, groupTermRepository, scheduleRepository, fileMapper, fileStorage);
    }

    @Test
    void upload_정상_요청이면_파일을_저장한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        MultipartFile multipartFile = new MockMultipartFile("file", "공연장 계약서.pdf", "application/pdf", "content".getBytes());

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.save(any(File.class))).willAnswer(invocation -> {
            File saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 10L);
            return saved;
        });

        FileUploadResponse response = fileService.upload(1L, multipartFile, "공연장 계약서", FileCategory.ACTIVITY_DOC, null);

        assertThat(response.getFileId()).isEqualTo(10L);
        assertThat(response.getName()).isEqualTo("공연장 계약서");
        verify(fileStorage).store(any(), any(), eq((long) multipartFile.getSize()), eq("application/pdf"));
    }

    @Test
    void upload_허용하지_않는_확장자면_FILE_400_EXTENSION_NOT_ALLOWED를_던진다() {
        MultipartFile multipartFile = new MockMultipartFile("file", "virus.exe", "application/octet-stream", "content".getBytes());

        assertThatThrownBy(() -> fileService.upload(1L, multipartFile, "바이러스", FileCategory.ACTIVITY_DOC, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_400_EXTENSION_NOT_ALLOWED);
    }

    @Test
    void upload_허용_용량을_넘으면_FILE_413_SIZE_EXCEEDED를_던진다() {
        byte[] content = new byte[(int) (FileUploadPolicy.MAX_SIZE_BYTES + 1)];
        MultipartFile multipartFile = new MockMultipartFile("file", "big.pdf", "application/pdf", content);

        assertThatThrownBy(() -> fileService.upload(1L, multipartFile, "큰 파일", FileCategory.ACTIVITY_DOC, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_413_SIZE_EXCEEDED);
    }

    @Test
    void upload_scheduleId가_다른_기수_소속이면_FILE_400_SCHEDULE_TERM_MISMATCH를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        GroupTerm otherTerm = mock(GroupTerm.class);
        Schedule schedule = mock(Schedule.class);
        MultipartFile multipartFile = new MockMultipartFile("file", "a.pdf", "application/pdf", "content".getBytes());

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(scheduleRepository.findById(999L)).willReturn(Optional.of(schedule));
        given(schedule.getGroupTerm()).willReturn(otherTerm);
        given(otherTerm.getId()).willReturn(2L);

        assertThatThrownBy(() -> fileService.upload(1L, multipartFile, "자료", FileCategory.ACTIVITY_DOC, 999L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_400_SCHEDULE_TERM_MISMATCH);
    }

    @Test
    void upload_기수가_없으면_FILE_404_GROUP_TERM_NOT_FOUND를_던진다() {
        MultipartFile multipartFile = new MockMultipartFile("file", "a.pdf", "application/pdf", "content".getBytes());
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.upload(1L, multipartFile, "자료", FileCategory.ACTIVITY_DOC, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void upload_장부파일이면_기존_활성_장부파일을_소프트삭제하고_scheduleId를_무시한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        File existing = File.upload(groupTerm, null, "이전 장부", "xlsx", 100L, FileCategory.LEDGER_FILE, "files/old.xlsx");
        MultipartFile multipartFile =
                new MockMultipartFile("file", "장부.xlsx", "application/vnd.ms-excel", "content".getBytes());

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.findActiveFile(1L, FileCategory.LEDGER_FILE)).willReturn(Optional.of(existing));
        given(fileRepository.save(any(File.class))).willAnswer(invocation -> invocation.getArgument(0));

        fileService.upload(1L, multipartFile, "장부", FileCategory.LEDGER_FILE, 999L);

        assertThat(existing.getDeletedAt()).isNotNull();
        verify(scheduleRepository, never()).findById(any());
    }

    @Test
    void getZipTargets_대상이_없으면_FILE_404_NO_DOWNLOADABLE_FILES를_던진다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.searchAll(eq(1L), isNull(), isNull())).willReturn(List.of());

        assertThatThrownBy(() -> fileService.getZipTargets(1L, FileCategoryFilter.ALL, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_NO_DOWNLOADABLE_FILES);
    }

    @Test
    void getZipTargets_같은_이름의_파일이_여러_개면_엔트리명에_번호를_붙인다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        given(groupTerm.getName()).willReturn("26기");
        File file1 = File.upload(groupTerm, null, "계약서", "pdf", 100L, FileCategory.ACTIVITY_DOC, "k1");
        File file2 = File.upload(groupTerm, null, "계약서", "pdf", 100L, FileCategory.ACTIVITY_DOC, "k2");

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.searchAll(eq(1L), isNull(), isNull())).willReturn(List.of(file1, file2));

        FileZipTarget target = fileService.getZipTargets(1L, FileCategoryFilter.ALL, null);

        assertThat(target.getEntries()).extracting(FileZipEntry::getEntryName)
                .containsExactly("계약서.pdf", "계약서 (1).pdf");
    }

    @Test
    void listFiles_다음_페이지가_있으면_nextCursor를_만든다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        File file1 = File.upload(groupTerm, null, "파일1", "pdf", 100L, FileCategory.ACTIVITY_DOC, "k1");
        File file2 = File.upload(groupTerm, null, "파일2", "pdf", 100L, FileCategory.ACTIVITY_DOC, "k2");
        ReflectionTestUtils.setField(file1, "id", 1L);
        ReflectionTestUtils.setField(file1, "createdAt", LocalDateTime.now());
        ReflectionTestUtils.setField(file2, "id", 2L);
        ReflectionTestUtils.setField(file2, "createdAt", LocalDateTime.now());

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.search(eq(1L), isNull(), isNull(), isNull(), eq(FileSortOption.LATEST),
                isNull(), isNull(), isNull(), eq(2)))
                .willReturn(List.of(file1, file2));

        FileListResponse response = fileService.listFiles(1L, FileCategoryFilter.ALL, null, FileSortOption.LATEST, null, null, 1);

        assertThat(response.getFiles()).hasSize(1);
        assertThat(response.getNextCursor()).isNotNull();
    }

    @Test
    void listFiles_cursor가_없으면_totalCount를_반환한다() {
        GroupTerm groupTerm = mock(GroupTerm.class);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.search(eq(1L), isNull(), isNull(), isNull(), eq(FileSortOption.LATEST),
                isNull(), isNull(), isNull(), eq(21)))
                .willReturn(List.of());
        given(fileRepository.count(eq(1L), isNull(), isNull(), isNull())).willReturn(5L);

        FileListResponse response = fileService.listFiles(1L, FileCategoryFilter.ALL, null, FileSortOption.LATEST, null, null, null);

        assertThat(response.getTotalCount()).isEqualTo(5L);
    }

    @Test
    void listFiles_cursor가_있으면_totalCount를_조회하지_않는다() {
        GroupTerm groupTerm = mock(GroupTerm.class);
        String cursor = CursorCodec.encode(String.valueOf(System.currentTimeMillis()), 1L);

        given(groupTermRepository.findById(1L)).willReturn(Optional.of(groupTerm));
        given(fileRepository.search(any(), any(), any(), any(), any(), any(), any(), any(), anyInt()))
                .willReturn(List.of());

        FileListResponse response = fileService.listFiles(1L, FileCategoryFilter.ALL, null, FileSortOption.LATEST, null, cursor, null);

        assertThat(response.getTotalCount()).isNull();
        verify(fileRepository, never()).count(any(), any(), any(), any());
    }

    @Test
    void listFiles_기수가_없으면_FILE_404_GROUP_TERM_NOT_FOUND를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() ->
                fileService.listFiles(1L, FileCategoryFilter.ALL, null, FileSortOption.LATEST, null, null, null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_GROUP_TERM_NOT_FOUND);
    }

    @Test
    void getFileContent_파일이_없거나_삭제됐으면_FILE_404_NOT_FOUND를_던진다() {
        given(fileRepository.findActiveById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getFileContent(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_NOT_FOUND);
    }

    @Test
    void getDownloadUrl_파일이_없거나_삭제됐으면_FILE_404_NOT_FOUND를_던진다() {
        given(fileRepository.findActiveById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.getDownloadUrl(1L, FileDownloadMode.PREVIEW))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_NOT_FOUND);
    }

    @Test
    void getDownloadUrl_응답은_content_엔드포인트_url만_담는다() {
        File file = mock(File.class);
        given(file.getId()).willReturn(1L);
        given(fileRepository.findActiveById(1L)).willReturn(Optional.of(file));

        FileDownloadUrlResponse response = fileService.getDownloadUrl(1L, FileDownloadMode.PREVIEW);

        assertThat(response.getUrl()).isEqualTo("/api/files/1/content?mode=PREVIEW");
    }

    @Test
    void deleteFile_파일이_없으면_FILE_404_NOT_FOUND를_던진다() {
        given(fileRepository.findById(1L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> fileService.deleteFile(1L))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_404_NOT_FOUND);
    }

    @Test
    void deleteFile_살아있는_파일이면_deleted_at을_찍는다() {
        File file = newFile();
        given(fileRepository.findById(1L)).willReturn(Optional.of(file));

        fileService.deleteFile(1L);

        assertThat(file.getDeletedAt()).isNotNull();
    }

    @Test
    void deleteFile_이미_삭제된_파일이면_성공하고_삭제_시각을_유지한다() {
        LocalDateTime deletedAt = LocalDateTime.of(2026, 11, 13, 10, 0);
        File file = newFile();
        ReflectionTestUtils.setField(file, "deletedAt", deletedAt);
        given(fileRepository.findById(1L)).willReturn(Optional.of(file));

        fileService.deleteFile(1L);

        assertThat(file.getDeletedAt()).isEqualTo(deletedAt);
    }

    @Test
    void listFiles_cursor가_잘못됐으면_FILE_400_INVALID_CURSOR를_던진다() {
        given(groupTermRepository.findById(1L)).willReturn(Optional.of(mock(GroupTerm.class)));

        assertThatThrownBy(() ->
                fileService.listFiles(1L, FileCategoryFilter.ALL, null, FileSortOption.LATEST, null, "not-a-cursor", null))
                .isInstanceOf(CustomException.class)
                .extracting(e -> ((CustomException) e).getErrorCode())
                .isEqualTo(FileErrorCode.FILE_400_INVALID_CURSOR);
    }

    private File newFile() {
        return File.upload(mock(GroupTerm.class), null, "공연장 계약서", "pdf", 1024L,
                FileCategory.ACTIVITY_DOC, "files/2026/11/contract.pdf");
    }
}
