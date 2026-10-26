/**
 * 실시간 채팅(STOMP over WebSocket) 연결 관리
 * - 앱 전체에서 연결 하나만 유지하고, 채팅방 화면에 들어갈 때 구독/나갈 때 해제
 * - 브라우저 WebSocket은 핸드셰이크에 헤더를 못 붙이므로 JWT는 STOMP CONNECT 헤더로 보낸다
 * - CONNECT가 거부되면(토큰 만료 등) /users/reissue로 재발급 후 재연결
 */
const Chat = {
    client: null,
    roomSub: null,
    errorHandler: null,
    _ready: null,

    /** 연결될 때까지 기다린다. 이미 연결돼 있으면 바로 반환 */
    connect() {
        if (this.client && this.client.connected) return Promise.resolve();
        if (this._ready) return this._ready;

        this._ready = new Promise((resolve, reject) => {
            let reissued = false;
            const scheme = location.protocol === 'https:' ? 'wss://' : 'ws://';
            const client = new StompJs.Client({
                brokerURL: scheme + location.host + '/ws-stomp',
                reconnectDelay: 0, // 재연결은 직접 관리 (만료 토큰으로 무한 재시도 방지)
                beforeConnect: () => { client.connectHeaders = { Authorization: 'Bearer ' + Auth.access }; },
                onConnect: () => {
                    client.subscribe('/user/queue/errors', (frame) => {
                        const err = JSON.parse(frame.body);
                        if (this.errorHandler) this.errorHandler(err);
                        else toast(err.message, true);
                    });
                    this._ready = null;
                    resolve();
                },
                onStompError: async () => {
                    // 서버 인터셉터에서 거부되면 ERROR 프레임이 오고 연결이 끊긴다
                    await client.deactivate();
                    if (!reissued && await reissue()) {
                        reissued = true;
                        client.activate();
                        return;
                    }
                    this._reset();
                    reject(new Error('채팅 서버 인증에 실패했습니다. 다시 로그인해 주세요.'));
                },
                onWebSocketClose: () => {
                    if (this._ready) return; // 연결 시도 중이면 위에서 처리
                    // 연결 도중 끊김 (서버 재시작 등) → 다음 화면 진입 시 다시 연결
                    this.roomSub = null;
                    if (this.client === client) this.client = null;
                },
            });
            this.client = client;
            client.activate();
        });
        return this._ready;
    },

    /** 채팅방 구독. 이전 구독은 해제 */
    async enterRoom(roomId, onMessage, onError) {
        await this.connect();
        this.leaveRoom();
        this.errorHandler = onError;
        this.roomSub = this.client.subscribe(`/sub/chat/room/${roomId}`,
            (frame) => onMessage(JSON.parse(frame.body)));
    },

    leaveRoom() {
        if (this.roomSub) {
            try { this.roomSub.unsubscribe(); } catch { /* 이미 끊긴 연결 */ }
        }
        this.roomSub = null;
        this.errorHandler = null;
    },

    send(roomId, content) {
        if (!this.client || !this.client.connected) throw new Error('채팅 서버에 연결되어 있지 않습니다.');
        this.client.publish({
            destination: '/pub/chat/message',
            body: JSON.stringify({ roomId, content }),
        });
    },

    /** 로그아웃 시 연결 종료 */
    disconnect() {
        this.leaveRoom();
        if (this.client) this.client.deactivate();
        this._reset();
    },

    _reset() {
        this.client = null;
        this._ready = null;
        this.roomSub = null;
    },
};
