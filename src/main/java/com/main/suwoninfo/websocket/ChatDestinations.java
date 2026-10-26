package com.main.suwoninfo.websocket;

public final class ChatDestinations {

    private ChatDestinations() {
    }

    public static String room(Long roomId) {
        return "/sub/chat/room/" + roomId;
    }
}
