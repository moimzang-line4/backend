package com.moimzang.domain.file.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import com.moimzang.domain.file.enums.FileSortOption;
import com.moimzang.domain.group.entity.ClubGroup;
import com.moimzang.domain.group.entity.GroupTerm;
import com.moimzang.global.config.JpaAuditingConfig;
import com.moimzang.global.config.QuerydslConfig;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// findActiveById의 소프트 삭제 조건은 mock 테스트로 구분할 수 없어 실제 DB로 검증한다
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import({QuerydslConfig.class, JpaAuditingConfig.class})
class FileRepositoryTest {

    @Container
    @ServiceConnection
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0").withReuse(true);

    @Autowired
    private TestEntityManager em;

    @Autowired
    private FileRepository fileRepository;

    private GroupTerm groupTerm;

    @BeforeEach
    void setUp() {
        ClubGroup clubGroup = BeanUtils.instantiateClass(ClubGroup.class);
        ReflectionTestUtils.setField(clubGroup, "name", "테스트 동아리");
        em.persistAndFlush(clubGroup);

        groupTerm = BeanUtils.instantiateClass(GroupTerm.class);
        ReflectionTestUtils.setField(groupTerm, "group", clubGroup);
        ReflectionTestUtils.setField(groupTerm, "name", "26기");
        em.persistAndFlush(groupTerm);
    }

    @Test
    void findActiveById_삭제되지_않은_파일을_반환한다() {
        File file = persistFile("공연장 계약서");

        Optional<File> result = fileRepository.findActiveById(file.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("공연장 계약서");
    }

    @Test
    void findActiveById_소프트_삭제된_파일은_반환하지_않는다() {
        File file = persistFile("삭제된 계약서");
        file.softDelete();
        em.flush();
        em.clear();

        Optional<File> result = fileRepository.findActiveById(file.getId());

        assertThat(result).isEmpty();
    }

    @Test
    void findActiveById_존재하지_않는_ID면_빈_값을_반환한다() {
        Optional<File> result = fileRepository.findActiveById(999L);

        assertThat(result).isEmpty();
    }

    @Test
    void search_검색어의_연속_공백도_접어서_저장값과_맞춘다() {
        // 자료명도 한 칸으로 접어 저장하므로(decisions #84) 검색어만 안 접으면 0건이 된다
        File target = persistFile("공연장 계약서");

        List<File> result = fileRepository.search(groupTerm.getId(), null, "공연장  계약서", null,
                FileSortOption.LATEST, null, null, null, 10);

        assertThat(result).extracting(File::getId).containsExactly(target.getId());
    }

    @Test
    void searchAll_ZIP_경로도_목록과_같은_검색어_규칙을_쓴다() {
        // search와 searchAll이 같은 조건 빌더를 타므로 목록에 뜨는 자료는 ZIP에도 담겨야 한다
        File target = persistFile("공연장 계약서");

        List<File> listed = fileRepository.search(groupTerm.getId(), null, "공연장  계약서", null,
                FileSortOption.LATEST, null, null, null, 10);
        List<File> zipped = fileRepository.searchAll(groupTerm.getId(), null, "공연장  계약서");

        assertThat(zipped).extracting(File::getId).containsExactly(target.getId());
        assertThat(zipped).extracting(File::getId)
                .containsExactlyElementsOf(listed.stream().map(File::getId).toList());
    }

    @Test
    void count_목록과_같은_검색어_규칙으로_센다() {
        persistFile("공연장 계약서");
        persistFile("무관한 자료");

        assertThat(fileRepository.count(groupTerm.getId(), null, "공연장  계약서", null)).isEqualTo(1);
    }

    private File persistFile(String name) {
        File file = File.upload(groupTerm, null, name, "pdf", 1024L, FileCategory.ACTIVITY_DOC,
                "files/2026/11/%s.pdf".formatted(name), null);
        return em.persistAndFlush(file);
    }
}
