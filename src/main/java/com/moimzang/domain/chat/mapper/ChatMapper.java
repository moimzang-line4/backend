package com.moimzang.domain.chat.mapper;

import com.moimzang.domain.chat.dto.response.ChatRoomSummaryResponse;
import com.moimzang.domain.chat.entity.AiChatRoom;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.ERROR)
public interface ChatMapper {

    // 대화방 1건을 목록 카드로 변환한다
    @Mapping(target = "chatRoomId", source = "id")
    ChatRoomSummaryResponse toChatRoomSummaryResponse(AiChatRoom chatRoom);

    // 대화방 목록을 변환한다
    List<ChatRoomSummaryResponse> toChatRoomSummaryResponses(List<AiChatRoom> chatRooms);
}
