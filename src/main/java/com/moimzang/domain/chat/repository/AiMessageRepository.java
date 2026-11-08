package com.moimzang.domain.chat.repository;

import com.moimzang.domain.chat.entity.AiMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {
}
