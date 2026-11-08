package com.moimzang.domain.note.service;

import com.moimzang.domain.note.repository.OperationNoteRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class NoteService {

    private final OperationNoteRepository operationNoteRepository;
}
