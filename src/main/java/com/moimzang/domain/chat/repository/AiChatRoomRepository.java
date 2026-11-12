package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiChatRoomRepository extends JpaRepository<AiChatRoom, Long>, AiChatRoomRepositoryCustom {
}
