package com.moimzang.domain.chat.service;

import com.moimzang.domain.chat.repository.AiChatRoomRepository;
import com.moimzang.domain.chat.repository.AiMessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ChatService {

    private final AiChatRoomRepository aiChatRoomRepository;
    private final AiMessageRepository aiMessageRepository;
}
