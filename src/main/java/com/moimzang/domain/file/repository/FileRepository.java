package com.moimzang.domain.file.repository;

import com.moimzang.domain.file.entity.File;
import com.moimzang.domain.file.enums.FileCategory;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FileRepository extends JpaRepository<File, Long>, FileRepositoryCustom {

    @Query("""
            select f from File f
            where f.groupTerm.id = :groupTermId
              and f.category = :category
              and f.deletedAt is null
            """)
    Optional<File> findActiveFile(
            @Param("groupTermId") Long groupTermId,
            @Param("category") FileCategory category
    );
}
