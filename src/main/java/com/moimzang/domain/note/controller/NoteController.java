package com.moimzang.domain.note.controller;

import com.moimzang.domain.note.service.NoteService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class NoteController implements NoteApi {

    private final NoteService noteService;
}
