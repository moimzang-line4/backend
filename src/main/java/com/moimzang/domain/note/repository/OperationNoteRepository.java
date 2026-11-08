package com.moimzang.domain.note.repository;

import com.moimzang.domain.note.entity.OperationNote;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationNoteRepository extends JpaRepository<OperationNote, Long> {
}
