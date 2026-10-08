package com.gakkum.backend.application.chat.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.gakkum.backend.application.chat.dto.ChatMessageListResponse.Message;
import com.gakkum.backend.application.chat.dto.ChatRoomListResponse.Room;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 채팅방 입장 화면이 요청 하나로 그려지도록 방 정보와 대화 내역을 함께 담는다. */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ChatRoomEntryResponse {

    /** 목록의 방 하나와 같은 필드를 감싸지 않고 그대로 내린다 */
    @JsonUnwrapped
    private final Room room;
    /** 이 작업에 사장님 후기가 등록됐는지 */
    private final boolean reviewed;
    /** 조회한 사용자의 User.id. 메시지의 senderUserId와 비교해 내 메시지를 구분한다 */
    private final String viewerUserId;
    private final List<Message> messages;

    public static ChatRoomEntryResponse of(Room room, boolean reviewed, ChatMessageListResponse messages) {
        return new ChatRoomEntryResponse(room, reviewed, messages.getViewerUserId(), messages.getMessages());
    }
}
