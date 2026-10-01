/**
 * 백엔드 API 호출 모듈
 * - Access/Refresh 토큰을 localStorage에 보관
 * - 서버(JwtFilter)가 만료 토큰을 자동 재발급하면 New-Access-Token 헤더로 내려주므로 이를 반영
 * - 그래도 401이 나면 /users/reissue로 한 번 재발급을 시도
 */
const Auth = {
    get access() { return localStorage.getItem('accessToken'); },
    get refresh() { return localStorage.getItem('refreshToken'); },
    save(token) {
        localStorage.setItem('accessToken', token.accessToken);
        if (token.refreshToken) localStorage.setItem('refreshToken', token.refreshToken);
    },
    clear() {
        localStorage.removeItem('accessToken');
        localStorage.removeItem('refreshToken');
        localStorage.removeItem('me');
    },
    isLoggedIn() { return !!this.access; },
    get me() {
        try { return JSON.parse(localStorage.getItem('me')); } catch { return null; }
    },
    set me(user) { localStorage.setItem('me', JSON.stringify(user)); },
};

class ApiError extends Error {
    constructor(status, body) {
        const msg = (body && typeof body === 'object')
            ? (body.message || body.solution || body.error || '요청 실패')
            : (body || '요청 실패');
        super(msg);
        this.status = status;
        this.code = body && body.code;
        this.body = body;
    }
}

async function parseBody(res) {
    const text = await res.text();
    if (!text) return null;
    try { return JSON.parse(text); } catch { return text; }
}

async function reissue() {
    if (!Auth.access || !Auth.refresh) return false;
    const res = await fetch('/users/reissue', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ accessToken: Auth.access, refreshToken: Auth.refresh, tokenType: 'Bearer' }),
    });
    if (!res.ok) return false;
    Auth.save(await res.json());
    return true;
}

/**
 * @param {string} path
 * @param {{method?: string, json?: any, form?: FormData, text?: string, raw?: boolean}} opts
 */
async function request(path, opts = {}, retried = false) {
    const headers = {};
    let body;
    if (opts.json !== undefined) {
        headers['Content-Type'] = 'application/json';
        body = JSON.stringify(opts.json);
    } else if (opts.form) {
        body = opts.form;
    } else if (opts.text !== undefined) {
        headers['Content-Type'] = 'text/plain;charset=UTF-8';
        body = opts.text;
    }
    if (Auth.access) headers['Authorization'] = 'Bearer ' + Auth.access;

    const res = await fetch(path, { method: opts.method || 'GET', headers, body });

    const renewed = res.headers.get('New-Access-Token');
    if (renewed) Auth.save({ accessToken: renewed });

    if (res.status === 401 && Auth.access && !retried) {
        if (await reissue()) return request(path, opts, true);
        Auth.clear();
        window.dispatchEvent(new Event('auth-changed'));
    }

    if (opts.raw && res.ok) return res;
    const data = await parseBody(res);
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
}

/** multipart 요청에서 JSON 파트를 만들기 위한 헬퍼 (@RequestPart DTO 바인딩용) */
function jsonPart(obj) {
    return new Blob([JSON.stringify(obj)], { type: 'application/json' });
}

const Api = {
    // ===== User =====
    signup: (form) => request('/users/new', { method: 'POST', json: form }),
    login: (email, password) => request('/users/login', { method: 'POST', json: { email, password } }),
    info: () => request('/users/info'),
    updateUser: (form) => request('/users/update', { method: 'POST', json: form }),
    logout: () => request('/users/logout', { method: 'DELETE' }),
    deleteUser: () => request('/users/delete', { method: 'DELETE' }),

    // ===== Post =====
    postList: (type, page) => request(`/post/list?type=${type}&page=${page}`),
    postSearch: async (type, keyword, page) => {
        const res = await request(
            `/post/search?type=${type}&page=${page}&keyword=${encodeURIComponent(keyword)}`, { raw: true });
        return { list: (await parseBody(res)) || [], totalPages: Number(res.headers.get('total-pages')) || 1 };
    },
    postView: (id) => request(`/post/view/${id}`),
    createPost: (postForm, files) => {
        const fd = new FormData();
        fd.append('postForm', jsonPart(postForm));
        (files || []).forEach(f => fd.append('files', f));
        return request('/post/new', { method: 'POST', form: fd });
    },
    updatePost: (id, updateForm, files) => {
        const fd = new FormData();
        fd.append('updateForm', jsonPart(updateForm));
        (files || []).forEach(f => fd.append('files', f));
        return request(`/post/update/${id}`, { method: 'POST', form: fd });
    },
    deletePost: (id) => request(`/post/delete/${id}`, { method: 'DELETE' }),

    // ===== Photo =====
    /** /photo/{id}는 인증이 필요하므로 <img src>로 직접 못 쓰고 Blob URL로 변환 */
    photoUrl: async (id) => {
        const res = await request(`/photo/${id}`, { raw: true });
        return URL.createObjectURL(await res.blob());
    },
    deletePhoto: (id) => request(`/photo/delete/${id}`, { method: 'DELETE' }),

    // ===== Comment =====
    comments: (postId) => request(`/comment/${postId}?page=1`),
    addComment: (postId, content) =>
        request('/comment/notreply', { method: 'POST', json: { content, postResponse: { postId } } }),
    addReply: (postId, parentId, content) =>
        request('/comment/reply', { method: 'POST', json: { content, postResponse: { postId }, parent: { id: parentId } } }),
    updateComment: (id, content) => request(`/comment/update/${id}`, { method: 'POST', text: content }),
    deleteComment: (id) => request(`/comment/delete/${id}`, { method: 'DELETE' }),

    // ===== Todo(시간표) =====
    todos: async () => (await request('/todo/view')) || [],
    createTodo: (todo) => request('/todo/new', { method: 'POST', json: todo }),
    updateTodo: (todo) => request('/todo/update', { method: 'POST', json: todo }),
    deleteTodo: (id) => request(`/todo/delete/${id}`, { method: 'DELETE' }),
};
