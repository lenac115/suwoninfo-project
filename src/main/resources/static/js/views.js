/**
 * 화면(View) 렌더링 함수 모음
 * 각 View는 (root, params) => Promise<void> 형태이며 root 엘리먼트의 내용을 채운다.
 */
const esc = (s) => String(s ?? '').replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

const BOARD_NAME = { FREE: '자유게시판', TRADE: '중고거래' };
const TRADE_STATUS = { READY: '판매중', NOW_TRADING: '거래중', DONE: '거래완료' };
const DAYS = ['MON', 'TUE', 'WED', 'THU', 'FRI', 'SAT', 'SUN'];
const DAY_NAME = { MON: '월', TUE: '화', WED: '수', THU: '목', FRI: '금', SAT: '토', SUN: '일' };
const PAGE_SIZE = 10;

const won = (n) => (n ?? 0).toLocaleString('ko-KR') + '원';
const pad = (n) => String(n).padStart(2, '0');

function toast(msg, isError = false) {
    const el = document.getElementById('toast');
    el.textContent = msg;
    el.className = 'toast' + (isError ? ' error' : '');
    el.hidden = false;
    clearTimeout(toast._t);
    toast._t = setTimeout(() => { el.hidden = true; }, 2600);
}

function errorBox(e) {
    return `<div class="empty">⚠️ ${esc(e.message)}</div>`;
}

function requireLogin(root) {
    if (Auth.isLoggedIn()) return true;
    root.innerHTML = `
        <div class="card center">
            <p>로그인이 필요한 페이지입니다.</p>
            <a class="btn primary" href="#/login?next=${encodeURIComponent(location.hash)}">로그인</a>
        </div>`;
    return false;
}

const Views = {

    // ===================== 홈 =====================
    async home(root) {
        root.innerHTML = `
            <section class="hero">
                <h1>수원대학교 커뮤니티</h1>
                <p>자유롭게 이야기하고, 필요한 물건을 사고팔고, 시간표를 관리하세요.</p>
            </section>
            <div class="grid-2">
                <div class="card"><div class="card-head"><h2>자유게시판</h2><a href="#/board/FREE">더보기 →</a></div><ul class="mini-list" id="home-FREE"><li class="muted">불러오는 중…</li></ul></div>
                <div class="card"><div class="card-head"><h2>중고거래</h2><a href="#/board/TRADE">더보기 →</a></div><ul class="mini-list" id="home-TRADE"><li class="muted">불러오는 중…</li></ul></div>
            </div>`;
        for (const type of ['FREE', 'TRADE']) {
            const ul = root.querySelector('#home-' + type);
            try {
                const list = (await Api.postList(type, 1)) || [];
                ul.innerHTML = list.length
                    ? list.slice(0, 5).map(p => `
                        <li><a href="#/post/${p.postId}">${esc(p.title)}</a>
                        ${type === 'TRADE' ? `<span class="muted">${won(p.price)}</span>` : ''}</li>`).join('')
                    : '<li class="muted">게시글이 없습니다.</li>';
            } catch (e) {
                ul.innerHTML = `<li class="muted">${esc(e.message)}</li>`;
            }
        }
    },

    // ===================== 게시판 목록 =====================
    async board(root, { type, query }) {
        if (!BOARD_NAME[type]) return Views.notFound(root);
        const page = Math.max(1, Number(query.get('page')) || 1);
        const keyword = query.get('q') || '';

        root.innerHTML = `
            <div class="page-head">
                <h1>${BOARD_NAME[type]}</h1>
                <a class="btn primary" href="#/write/${type}">글쓰기</a>
            </div>
            <form class="search" id="search">
                <input name="q" placeholder="제목/내용 검색" value="${esc(keyword)}">
                <button class="btn">검색</button>
                ${keyword ? `<a class="btn ghost" href="#/board/${type}">초기화</a>` : ''}
            </form>
            <div id="list" class="card list"><div class="muted pad">불러오는 중…</div></div>
            <div id="pager" class="pager"></div>`;

        root.querySelector('#search').addEventListener('submit', (ev) => {
            ev.preventDefault();
            const q = new FormData(ev.target).get('q').trim();
            location.hash = `#/board/${type}` + (q ? `?q=${encodeURIComponent(q)}` : '');
        });

        const listEl = root.querySelector('#list');
        let list, totalPages = null;
        try {
            if (keyword) {
                ({ list, totalPages } = await Api.postSearch(type, keyword, page));
            } else {
                list = (await Api.postList(type, page)) || [];
            }
        } catch (e) {
            listEl.innerHTML = errorBox(e);
            return;
        }

        listEl.innerHTML = list.length ? list.map(p => `
            <a class="row" href="#/post/${p.postId}">
                <span class="row-id">${p.postId}</span>
                <span class="row-title">${esc(p.title)}
                    ${p.photos && p.photos.length ? '<span class="tag">📷</span>' : ''}
                </span>
                ${type === 'TRADE' ? `
                    <span class="row-price">${won(p.price)}</span>
                    <span class="badge s-${p.tradeStatus}">${TRADE_STATUS[p.tradeStatus] || '-'}</span>` : ''}
            </a>`).join('')
            : '<div class="empty">게시글이 없습니다.</div>';

        // /post/list는 전체 페이지 수를 주지 않으므로 결과 개수로 다음 페이지 유무를 판단
        const hasNext = totalPages != null ? page < totalPages : list.length >= PAGE_SIZE;
        const link = (p) => `#/board/${type}?page=${p}` + (keyword ? `&q=${encodeURIComponent(keyword)}` : '');
        root.querySelector('#pager').innerHTML = `
            ${page > 1 ? `<a class="btn" href="${link(page - 1)}">← 이전</a>` : '<span class="btn disabled">← 이전</span>'}
            <span class="pager-cur">${page}${totalPages != null ? ' / ' + totalPages : ''}</span>
            ${hasNext ? `<a class="btn" href="${link(page + 1)}">다음 →</a>` : '<span class="btn disabled">다음 →</span>'}`;
    },

    // ===================== 게시글 상세 =====================
    async post(root, { id }) {
        root.innerHTML = '<div class="muted pad">불러오는 중…</div>';
        let post;
        try {
            post = await Api.postView(id);
        } catch (e) {
            root.innerHTML = errorBox(e);
            return;
        }

        const isTrade = post.postType === 'TRADE';
        const me = Auth.me;
        const canChat = Auth.isLoggedIn() && post.writerId && me && me.id !== post.writerId;
        root.innerHTML = `
            <a class="back" href="#/board/${post.postType}">← ${BOARD_NAME[post.postType] || '목록'}</a>
            <article class="card post">
                <div class="post-head">
                    <h1>${esc(post.title)}</h1>
                    ${isTrade ? `<div class="post-meta">
                        <span class="price">${won(post.price)}</span>
                        <span class="badge s-${post.tradeStatus}">${TRADE_STATUS[post.tradeStatus] || '-'}</span>
                    </div>` : ''}
                </div>
                <div class="post-body">${esc(post.content)}</div>
                <div class="photos" id="photos"></div>
                ${Auth.isLoggedIn() ? `
                <div class="actions">
                    ${canChat ? `<button class="btn primary" id="chat-writer">💬 ${isTrade ? '판매자와 채팅' : '작성자와 채팅'}</button>` : ''}
                    <a class="btn" href="#/edit/${post.postId}">수정</a>
                    <button class="btn danger" id="del-post">삭제</button>
                </div>` : ''}
            </article>
            <section class="card" id="comments"></section>`;

        // 사진 (인증 필요 → Blob URL)
        const photosEl = root.querySelector('#photos');
        for (const ph of post.photos || []) {
            const fig = document.createElement('figure');
            fig.innerHTML = `<div class="ph-loading">${esc(ph.origFileName)}</div>`;
            photosEl.appendChild(fig);
            if (!Auth.isLoggedIn()) {
                fig.innerHTML = `<div class="ph-loading">🔒 로그인 후 볼 수 있습니다</div>`;
                continue;
            }
            Api.photoUrl(ph.photoId)
                .then(url => { fig.innerHTML = `<img src="${url}" alt="${esc(ph.origFileName)}">`; })
                .catch(() => { fig.innerHTML = `<div class="ph-loading">이미지 로드 실패</div>`; });
        }

        const chatBtn = root.querySelector('#chat-writer');
        if (chatBtn) chatBtn.addEventListener('click', () => Views.openChat(post.writerId));

        const del = root.querySelector('#del-post');
        if (del) del.addEventListener('click', async () => {
            if (!confirm('게시글을 삭제할까요?')) return;
            try {
                await Api.deletePost(post.postId);
                toast('삭제되었습니다.');
                location.hash = `#/board/${post.postType}`;
            } catch (e) { toast(e.message, true); }
        });

        Views._comments(root.querySelector('#comments'), post.postId);
    },

    async _comments(box, postId) {
        if (!Auth.isLoggedIn()) {
            box.innerHTML = `<h2>댓글</h2><p class="muted">댓글은 <a href="#/login?next=${encodeURIComponent(location.hash)}">로그인</a> 후 볼 수 있습니다.</p>`;
            return;
        }
        box.innerHTML = '<h2>댓글</h2><div class="muted">불러오는 중…</div>';

        let comments;
        try {
            comments = (await Api.comments(postId)) || [];
        } catch (e) {
            box.innerHTML = '<h2>댓글</h2>' + errorBox(e);
            return;
        }
        const me = Auth.me;
        const count = (list) => list.reduce((n, c) => n + 1 + count(c.children || []), 0);

        const renderOne = (c, depth) => {
            const mine = me && c.user && c.user.email === me.email;
            return `
            <div class="comment" style="--depth:${Math.min(depth, 4)}" data-id="${c.id}">
                <div class="comment-head">
                    <strong>${esc(c.user ? c.user.nickname : '알 수 없음')}</strong>
                    <span class="comment-tools">
                        <button class="link" data-act="reply">답글</button>
                        ${!mine && c.user ? `<button class="link" data-act="chat" data-user="${c.user.id}">채팅</button>` : ''}
                        ${mine ? `<button class="link" data-act="edit">수정</button>
                                  <button class="link danger" data-act="delete">삭제</button>` : ''}
                    </span>
                </div>
                <div class="comment-body">${esc(c.content)}</div>
                <div class="comment-slot"></div>
            </div>
            ${(c.children || []).map(ch => renderOne(ch, depth + 1)).join('')}`;
        };

        box.innerHTML = `
            <h2>댓글 <span class="muted">${count(comments)}</span></h2>
            <form class="comment-form" id="new-comment">
                <textarea name="content" rows="2" placeholder="댓글을 입력하세요" required></textarea>
                <button class="btn primary">등록</button>
            </form>
            <div class="comment-list">${comments.length ? comments.map(c => renderOne(c, 0)).join('') : '<div class="muted">첫 댓글을 남겨보세요.</div>'}</div>`;

        const reload = () => Views._comments(box, postId);

        box.querySelector('#new-comment').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const content = new FormData(ev.target).get('content').trim();
            if (!content) return;
            try { await Api.addComment(postId, content); reload(); }
            catch (e) { toast(e.message, true); }
        });

        box.querySelector('.comment-list').addEventListener('click', async (ev) => {
            const btn = ev.target.closest('button[data-act]');
            if (!btn) return;
            const el = btn.closest('.comment');
            const id = Number(el.dataset.id);
            const slot = el.querySelector('.comment-slot');
            const act = btn.dataset.act;

            if (act === 'chat') {
                Views.openChat(Number(btn.dataset.user));
                return;
            }
            if (act === 'delete') {
                if (!confirm('댓글을 삭제할까요?')) return;
                try { await Api.deleteComment(id); reload(); } catch (e) { toast(e.message, true); }
                return;
            }
            const initial = act === 'edit' ? el.querySelector('.comment-body').textContent : '';
            slot.innerHTML = `
                <form class="comment-form inline">
                    <textarea rows="2" required placeholder="${act === 'reply' ? '답글을 입력하세요' : ''}">${esc(initial)}</textarea>
                    <button class="btn primary">${act === 'reply' ? '답글' : '수정'}</button>
                    <button type="button" class="btn ghost" data-cancel>취소</button>
                </form>`;
            const form = slot.querySelector('form');
            form.querySelector('[data-cancel]').onclick = () => { slot.innerHTML = ''; };
            form.querySelector('textarea').focus();
            form.onsubmit = async (e2) => {
                e2.preventDefault();
                const content = form.querySelector('textarea').value.trim();
                if (!content) return;
                try {
                    if (act === 'reply') await Api.addReply(postId, id, content);
                    else await Api.updateComment(id, content);
                    reload();
                } catch (e) { toast(e.message, true); }
            };
        });
    },

    // ===================== 글쓰기 / 수정 =====================
    async editor(root, { type, id }) {
        if (!requireLogin(root)) return;
        let post = { title: '', content: '', price: 0, postType: type || 'FREE', tradeStatus: 'READY', photos: [] };
        if (id) {
            try { post = await Api.postView(id); }
            catch (e) { root.innerHTML = errorBox(e); return; }
        }

        root.innerHTML = `
            <div class="page-head"><h1>${id ? '게시글 수정' : '새 글 작성'}</h1></div>
            <form class="card form" id="editor">
                <label>게시판
                    <select name="postType">
                        ${Object.entries(BOARD_NAME).map(([k, v]) =>
                            `<option value="${k}" ${post.postType === k ? 'selected' : ''}>${v}</option>`).join('')}
                    </select>
                </label>
                <label>제목<input name="postTitle" required maxlength="200" value="${esc(post.title)}"></label>
                <div class="trade-only grid-2">
                    <label>가격(원)<input name="price" type="number" min="0" step="100" value="${post.price ?? 0}"></label>
                    <label>거래 상태
                        <select name="tradeStatus">
                            ${Object.entries(TRADE_STATUS).map(([k, v]) =>
                                `<option value="${k}" ${post.tradeStatus === k ? 'selected' : ''}>${v}</option>`).join('')}
                        </select>
                    </label>
                </div>
                <label>내용<textarea name="content" rows="12" required>${esc(post.content)}</textarea></label>
                ${id && post.photos && post.photos.length ? `
                <div class="field">
                    <span class="field-label">첨부된 사진</span>
                    <ul class="file-list">${post.photos.map(ph => `
                        <li data-photo="${ph.photoId}">${esc(ph.origFileName)}
                            <button type="button" class="link danger" data-del-photo="${ph.photoId}">삭제</button></li>`).join('')}
                    </ul>
                </div>` : ''}
                <label>사진 추가 <span class="muted">(최대 10MB)</span>
                    <input name="files" type="file" accept="image/png,image/jpeg" multiple>
                </label>
                <div class="actions">
                    <a class="btn ghost" href="${id ? '#/post/' + id : '#/board/' + post.postType}">취소</a>
                    <button class="btn primary">${id ? '수정 완료' : '등록'}</button>
                </div>
            </form>`;

        const form = root.querySelector('#editor');
        const syncTrade = () => {
            form.querySelector('.trade-only').style.display = form.postType.value === 'TRADE' ? '' : 'none';
        };
        form.postType.addEventListener('change', syncTrade);
        syncTrade();

        form.addEventListener('click', async (ev) => {
            const btn = ev.target.closest('[data-del-photo]');
            if (!btn || !confirm('사진을 삭제할까요?')) return;
            try {
                await Api.deletePhoto(btn.dataset.delPhoto);
                btn.closest('li').remove();
                toast('사진이 삭제되었습니다.');
            } catch (e) { toast(e.message, true); }
        });

        form.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const isTrade = form.postType.value === 'TRADE';
            const data = {
                title: form.postTitle.value.trim(),
                content: form.content.value,
                postType: form.postType.value,
                price: isTrade ? Number(form.price.value) || 0 : 0,
                tradeStatus: isTrade ? form.tradeStatus.value : null,
            };
            const files = [...form.files.files];
            const submitBtn = form.querySelector('button.primary');
            submitBtn.disabled = true;
            try {
                if (id) {
                    await Api.updatePost(id, { postId: Number(id), ...data }, files);
                    toast('수정되었습니다.');
                    location.hash = '#/post/' + id;
                } else {
                    await Api.createPost(data, files);
                    toast('등록되었습니다.');
                    location.hash = '#/board/' + data.postType;
                }
            } catch (e) {
                toast(e.message, true);
                submitBtn.disabled = false;
            }
        });
    },

    // ===================== 로그인 / 회원가입 =====================
    async login(root, { query }) {
        root.innerHTML = `
            <form class="card form narrow" id="login">
                <h1>로그인</h1>
                <label>이메일<input name="email" type="email" required autocomplete="username"></label>
                <label>비밀번호<input name="password" type="password" required autocomplete="current-password"></label>
                <button class="btn primary block">로그인</button>
                <p class="muted center">계정이 없나요? <a href="#/signup">회원가입</a>
                    <span aria-hidden="true"> · </span>
                    <a href="#" id="toggle-reset">비밀번호 재설정(테스트)</a></p>
            </form>
            <form class="card form narrow" id="reset-form" hidden>
                <h2>테스트용 비밀번호 재설정</h2>
                <p class="muted">입력한 이메일 계정의 비밀번호를 새 값으로 덮어씁니다.
                    (BCrypt 해시라 기존 비밀번호를 복구하는 것은 불가능합니다.)</p>
                <label>이메일<input name="email" type="email" required autocomplete="username"></label>
                <label>새 비밀번호<input name="password" type="password" required minlength="4" autocomplete="new-password"></label>
                <button class="btn block">재설정</button>
            </form>`;

        // 테스트용 재설정 폼 토글 (로그인 창에 입력한 이메일을 그대로 채워준다)
        const resetForm = root.querySelector('#reset-form');
        root.querySelector('#toggle-reset').addEventListener('click', (ev) => {
            ev.preventDefault();
            resetForm.hidden = !resetForm.hidden;
            if (!resetForm.hidden) resetForm.email.value = root.querySelector('#login').email.value;
        });
        resetForm.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const f = ev.target;
            try {
                await Api.resetTestPassword(f.email.value.trim(), f.password.value);
            } catch (e) {
                toast(e.code === 'NOT_EXIST_EMAIL' ? '존재하지 않는 이메일입니다.'
                    : e.code === 'INVALID_TEST_PASSWORD' ? '새 비밀번호는 4자 이상이어야 합니다.'
                    : e.message, true);
                return;
            }
            // 재설정에 쓴 값을 로그인 폼에 그대로 채워 바로 로그인할 수 있게 한다
            const loginForm = root.querySelector('#login');
            loginForm.email.value = f.email.value.trim();
            loginForm.password.value = f.password.value;
            resetForm.hidden = true;
            toast('비밀번호가 재설정되었습니다. 로그인 버튼을 눌러주세요.');
        });

        root.querySelector('#login').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const f = ev.target;
            try {
                Auth.save(await Api.login(f.email.value.trim(), f.password.value));
            } catch (e) {
                Auth.clear();
                toast(e.code === 'NOT_CORRECT_PASSWORD' ? '비밀번호가 올바르지 않습니다.'
                    : e.code === 'NOT_EXIST_EMAIL' ? '존재하지 않는 이메일입니다.' : e.message, true);
                return;
            }
            // 로그인 자체와 내 정보 조회 실패를 구분해서 안내
            try {
                Auth.me = await Api.info();
            } catch (e) {
                Auth.clear();
                toast('로그인은 됐지만 사용자 정보를 불러오지 못했습니다: ' + e.message, true);
                return;
            }
            window.dispatchEvent(new Event('auth-changed'));
            toast(`${Auth.me.nickname}님 환영합니다!`);
            location.hash = query.get('next') || '#/';
        });
    },

    async signup(root) {
        root.innerHTML = `
            <form class="card form narrow" id="signup">
                <h1>회원가입</h1>
                <label>이메일<input name="email" type="email" required autocomplete="username"></label>
                <label>비밀번호<input name="password" type="password" required minlength="4" autocomplete="new-password"></label>
                <label>비밀번호 확인<input name="password2" type="password" required autocomplete="new-password"></label>
                <label>이름<input name="userName" required></label>
                <label>닉네임<input name="nickname" required></label>
                <label>학번<input name="studentNumber" type="number" required></label>
                <button class="btn primary block">가입하기</button>
                <p class="muted center">이미 계정이 있나요? <a href="#/login">로그인</a></p>
            </form>`;
        root.querySelector('#signup').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const f = ev.target;
            if (f.password.value !== f.password2.value) return toast('비밀번호가 일치하지 않습니다.', true);
            try {
                await Api.signup({
                    email: f.email.value.trim(),
                    password: f.password.value,
                    name: f.userName.value.trim(),
                    nickname: f.nickname.value.trim(),
                    studentNumber: Number(f.studentNumber.value),
                });
                toast('가입이 완료되었습니다. 로그인해주세요.');
                location.hash = '#/login';
            } catch (e) {
                toast(e.code === 'VALIDATED_EMAIL_ERROR' ? '이미 사용 중인 이메일입니다.'
                    : e.code === 'VALIDATED_NICK_ERROR' ? '이미 사용 중인 닉네임입니다.' : e.message, true);
            }
        });
    },

    // ===================== 마이페이지 =====================
    async mypage(root) {
        if (!requireLogin(root)) return;
        let me;
        try { me = Auth.me = await Api.info(); }
        catch (e) { root.innerHTML = errorBox(e); return; }

        root.innerHTML = `
            <div class="page-head"><h1>마이페이지</h1></div>
            <div class="grid-2">
                <div class="card">
                    <h2>내 정보</h2>
                    <dl class="info">
                        <dt>이메일</dt><dd>${esc(me.email)}</dd>
                        <dt>이름</dt><dd>${esc(me.name)}</dd>
                        <dt>닉네임</dt><dd>${esc(me.nickname)}</dd>
                        <dt>학번</dt><dd>${esc(me.studentNumber)}</dd>
                        <dt>권한</dt><dd>${esc(me.auth)}</dd>
                    </dl>
                </div>
                <form class="card form" id="update">
                    <h2>정보 수정</h2>
                    <label>이름<input name="userName" required value="${esc(me.name)}"></label>
                    <label>닉네임<input name="nickname" required value="${esc(me.nickname)}"></label>
                    <label>새 비밀번호<input name="password" type="password" required autocomplete="new-password"></label>
                    <button class="btn primary">저장</button>
                </form>
            </div>
            <div class="card danger-zone">
                <h2>계정</h2>
                <div class="actions">
                    <button class="btn" id="logout">로그아웃</button>
                    <button class="btn danger" id="withdraw">회원 탈퇴</button>
                </div>
            </div>`;

        root.querySelector('#update').addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const f = ev.target;
            try {
                Auth.me = await Api.updateUser({
                    email: me.email, name: f.userName.value.trim(),
                    nickname: f.nickname.value.trim(), password: f.password.value,
                });
                window.dispatchEvent(new Event('auth-changed'));
                toast('저장되었습니다.');
                Views.mypage(root);
            } catch (e) { toast(e.message, true); }
        });
        root.querySelector('#logout').addEventListener('click', App.logout);
        root.querySelector('#withdraw').addEventListener('click', async () => {
            if (!confirm('정말 탈퇴하시겠어요? 되돌릴 수 없습니다.')) return;
            try {
                await Api.deleteUser();
                Auth.clear();
                window.dispatchEvent(new Event('auth-changed'));
                toast('탈퇴 처리되었습니다.');
                location.hash = '#/';
            } catch (e) { toast(e.message, true); }
        });
    },

    // ===================== 시간표 =====================
    async todo(root) {
        if (!requireLogin(root)) return;
        root.innerHTML = '<div class="muted pad">불러오는 중…</div>';
        let todos;
        try { todos = await Api.todos(); }
        catch (e) { root.innerHTML = errorBox(e); return; }

        const START = 9, END = 21, ROW = 48; // 9시~21시, 1시간당 48px
        const startOf = (t) => t.startHour * 60 + t.startMinute;
        const endOf = (t) => t.endHour * 60 + t.endMinute;
        const hourLines = Array.from({ length: END - START }, (_, i) =>
            `<div class="tt-hour" style="top:${i * ROW}px"><span>${START + i}</span></div>`).join('');

        root.innerHTML = `
            <div class="page-head"><h1>시간표</h1></div>
            <div class="todo-layout">
                <div class="card tt-wrap">
                    <div class="tt" style="--rows:${END - START};--row:${ROW}px">
                        <div class="tt-corner"></div>
                        ${DAYS.slice(0, 5).map(d => `<div class="tt-day">${DAY_NAME[d]}</div>`).join('')}
                        <div class="tt-gutter">${hourLines}</div>
                        ${DAYS.slice(0, 5).map(d => `
                            <div class="tt-col">
                                ${todos.filter(t => t.dayList === d).map(t => {
                                    const top = (startOf(t) - START * 60) / 60 * ROW;
                                    const h = Math.max(20, (endOf(t) - startOf(t)) / 60 * ROW);
                                    return `<button class="tt-item c${t.id % 6}" data-id="${t.id}" style="top:${top}px;height:${h}px">
                                        <strong>${esc(t.className || '(과목명 없음)')}</strong>
                                        <span>${esc(t.classroom || '')}</span>
                                    </button>`;
                                }).join('')}
                            </div>`).join('')}
                    </div>
                    ${todos.some(t => t.dayList === 'SAT' || t.dayList === 'SUN')
                        ? `<p class="muted">주말 수업: ${todos.filter(t => t.dayList === 'SAT' || t.dayList === 'SUN')
                            .map(t => `${DAY_NAME[t.dayList]} ${esc(t.className)}`).join(', ')}</p>` : ''}
                </div>

                <form class="card form" id="todo-form">
                    <h2 id="todo-form-title">수업 추가</h2>
                    <input type="hidden" name="todoId">
                    <label>과목명<input name="subject" required></label>
                    <label>강의실<input name="classroom"></label>
                    <label>교수<input name="professor"></label>
                    <label>요일
                        <select name="dayList">${DAYS.map(d => `<option value="${d}">${DAY_NAME[d]}</option>`).join('')}</select>
                    </label>
                    <div class="grid-2">
                        <label>시작<input name="start" type="time" value="09:00" step="300" required></label>
                        <label>종료<input name="end" type="time" value="10:15" step="300" required></label>
                    </div>
                    <p class="form-error" id="todo-error" role="alert" hidden></p>
                    <div class="actions">
                        <button type="button" class="btn danger" id="todo-del" hidden>삭제</button>
                        <button type="button" class="btn ghost" id="todo-reset" hidden>취소</button>
                        <button class="btn primary">저장</button>
                    </div>
                </form>
            </div>`;

        const form = root.querySelector('#todo-form');
        const delBtn = root.querySelector('#todo-del');
        const resetBtn = root.querySelector('#todo-reset');
        const errorEl = root.querySelector('#todo-error');

        // 저장 실패는 토스트만으로는 놓치기 쉬우므로 폼 안에 남겨두고, 충돌한 수업 블록을 강조
        const showError = (msg, conflictId) => {
            errorEl.textContent = msg || '';
            errorEl.hidden = !msg;
            root.querySelectorAll('.tt-item.conflict').forEach(el => el.classList.remove('conflict'));
            if (conflictId != null) {
                const el = root.querySelector(`.tt-item[data-id="${conflictId}"]`);
                if (el) el.classList.add('conflict');
            }
        };
        const fmt = (t) => `${DAY_NAME[t.dayList]} ${pad(t.startHour)}:${pad(t.startMinute)}~${pad(t.endHour)}:${pad(t.endMinute)}`;

        const fill = (t) => {
            showError(null);
            form.todoId.value = t ? t.id : '';
            form.subject.value = t ? t.className || '' : '';
            form.classroom.value = t ? t.classroom || '' : '';
            form.professor.value = t ? t.professor || '' : '';
            form.dayList.value = t ? t.dayList : 'MON';
            form.start.value = t ? `${pad(t.startHour)}:${pad(t.startMinute)}` : '09:00';
            form.end.value = t ? `${pad(t.endHour)}:${pad(t.endMinute)}` : '10:15';
            root.querySelector('#todo-form-title').textContent = t ? '수업 수정' : '수업 추가';
            delBtn.hidden = resetBtn.hidden = !t;
        };

        root.querySelector('.tt').addEventListener('click', (ev) => {
            const item = ev.target.closest('.tt-item');
            if (item) fill(todos.find(t => t.id === Number(item.dataset.id)));
        });
        resetBtn.addEventListener('click', () => fill(null));
        delBtn.addEventListener('click', async () => {
            if (!confirm('이 수업을 삭제할까요?')) return;
            try { await Api.deleteTodo(form.todoId.value); toast('삭제되었습니다.'); Views.todo(root); }
            catch (e) { toast(e.message, true); }
        });

        form.addEventListener('submit', async (ev) => {
            ev.preventDefault();
            const [sh, sm] = form.start.value.split(':').map(Number);
            const [eh, em] = form.end.value.split(':').map(Number);
            const start = sh * 60 + sm, end = eh * 60 + em;
            if (end <= start) return showError('종료 시간이 시작 시간보다 늦어야 합니다.');
            const body = {
                id: form.todoId.value ? Number(form.todoId.value) : null,
                className: form.subject.value.trim(),
                classroom: form.classroom.value.trim(),
                professor: form.professor.value.trim(),
                dayList: form.dayList.value,
                startHour: sh, startMinute: sm, endHour: eh, endMinute: em,
            };

            // 서버(TodoService.validateTime)와 같은 규칙으로 미리 검사 (수정 시 자기 자신 제외)
            const conflict = todos.find(t => t.id !== body.id && t.dayList === body.dayList
                && start < t.endHour * 60 + t.endMinute && end > t.startHour * 60 + t.startMinute);
            if (conflict) {
                return showError(`'${conflict.className || '수업'}'(${fmt(conflict)})과 시간이 겹쳐 저장할 수 없습니다.`, conflict.id);
            }

            try {
                if (body.id) await Api.updateTodo(body); else await Api.createTodo(body);
                toast('저장되었습니다.');
                Views.todo(root);
            } catch (e) {
                showError(e.code === 'DUPLICATED_TODO'
                    ? '같은 시간에 이미 수업이 있어 저장되지 않았습니다.'
                    : '저장 실패: ' + e.message);
            }
        });
    },

    // ===================== 채팅 목록 =====================
    async chatList(root) {
        if (!requireLogin(root)) return;
        root.innerHTML = '<div class="muted pad">불러오는 중…</div>';
        let rooms;
        try {
            rooms = await Api.chatRooms();
        } catch (e) {
            root.innerHTML = errorBox(e);
            return;
        }
        root.innerHTML = `
            <div class="page-head"><h1>채팅</h1></div>
            <div class="card list">
                ${rooms.length ? rooms.map(r => `
                    <a class="row" href="#/chat/${r.roomId}">
                        <span class="chat-avatar">${esc((r.opponentNickname || '?').slice(0, 1))}</span>
                        <span class="row-title">${esc(r.opponentNickname || '알 수 없음')}</span>
                        <span class="muted">→</span>
                    </a>`).join('')
                : '<div class="empty">아직 채팅방이 없습니다.<br>게시글이나 댓글에서 <b>채팅하기</b>를 눌러 대화를 시작하세요.</div>'}
            </div>`;
    },

    // ===================== 채팅방 =====================
    async chatRoom(root, { roomId }) {
        if (!requireLogin(root)) return;
        roomId = Number(roomId);
        root.innerHTML = '<div class="muted pad">불러오는 중…</div>';

        let me = Auth.me, room, history;
        try {
            if (!me) me = Auth.me = await Api.info();
            [room, history] = await Promise.all([
                Api.chatRooms().then(list => list.find(r => r.roomId === roomId)),
                Api.chatMessages(roomId),
            ]);
        } catch (e) {
            root.innerHTML = errorBox(e);
            return;
        }
        if (!room) {
            root.innerHTML = `<div class="card center"><p>채팅방을 찾을 수 없습니다.</p><a class="btn" href="#/chat">채팅 목록</a></div>`;
            return;
        }

        root.innerHTML = `
            <a class="back" href="#/chat">← 채팅 목록</a>
            <section class="card chat">
                <div class="chat-head">
                    <span class="chat-avatar">${esc((room.opponentNickname || '?').slice(0, 1))}</span>
                    <strong>${esc(room.opponentNickname || '알 수 없음')}</strong>
                    <span class="chat-status muted" id="chat-status">연결 중…</span>
                </div>
                <div class="chat-log" id="chat-log">
                    <div class="center" id="chat-more-wrap"><button class="btn small ghost" id="chat-more">이전 메시지 더보기</button></div>
                </div>
                <form class="chat-form" id="chat-form">
                    <input name="content" maxlength="1000" autocomplete="off" placeholder="메시지를 입력하세요" disabled>
                    <button class="btn primary" disabled>전송</button>
                </form>
            </section>`;

        const log = root.querySelector('#chat-log');
        const moreWrap = root.querySelector('#chat-more-wrap');
        const form = root.querySelector('#chat-form');
        const status = root.querySelector('#chat-status');
        const seen = new Set();
        let oldestId = null;

        const time = (iso) => {
            const d = new Date(iso);
            return `${pad(d.getHours())}:${pad(d.getMinutes())}`;
        };
        const bubble = (m) => {
            const mine = m.senderId === me.id;
            const el = document.createElement('div');
            el.className = 'chat-msg' + (mine ? ' mine' : '');
            el.innerHTML = `<div class="chat-bubble">${esc(m.content)}</div><span class="chat-time">${time(m.sentAt)}</span>`;
            return el;
        };
        const nearBottom = () => log.scrollHeight - log.scrollTop - log.clientHeight < 80;
        const toBottom = () => { log.scrollTop = log.scrollHeight; };

        // 내역은 최신순으로 오므로 뒤집어서 위에서부터 쌓는다
        const prepend = (list) => {
            const prevHeight = log.scrollHeight;
            const frag = document.createDocumentFragment();
            for (const m of [...list].reverse()) {
                if (seen.has(m.messageId)) continue;
                seen.add(m.messageId);
                frag.appendChild(bubble(m));
            }
            moreWrap.after(frag);
            if (list.length) oldestId = list[list.length - 1].messageId;
            moreWrap.hidden = list.length < 30;
            log.scrollTop += log.scrollHeight - prevHeight; // 보던 위치 유지
        };
        const append = (m) => {
            if (seen.has(m.messageId)) return; // 내역 로딩과 실시간 수신이 겹친 경우
            seen.add(m.messageId);
            const stick = nearBottom() || m.senderId === me.id;
            log.appendChild(bubble(m));
            if (stick) toBottom();
        };

        prepend(history);
        if (!history.length) log.insertAdjacentHTML('beforeend', '<div class="empty" id="chat-empty">첫 메시지를 보내보세요.</div>');
        toBottom();

        root.querySelector('#chat-more').addEventListener('click', async (ev) => {
            ev.target.disabled = true;
            try { prepend(await Api.chatMessages(roomId, oldestId)); }
            catch (e) { toast(e.message, true); }
            finally { ev.target.disabled = false; }
        });

        try {
            await Chat.enterRoom(roomId, (m) => {
                root.querySelector('#chat-empty')?.remove();
                append(m);
            }, (err) => toast(err.message, true));
        } catch (e) {
            status.textContent = '연결 실패';
            toast(e.message, true);
            return;
        }
        // 연결을 기다리는 사이 다른 화면으로 이동했으면 구독만 해두지 않도록 정리
        if (!document.body.contains(form)) { Chat.leaveRoom(); return; }

        status.textContent = '';
        form.content.disabled = false;
        form.querySelector('button').disabled = false;
        form.content.focus();

        form.addEventListener('submit', (ev) => {
            ev.preventDefault();
            const content = form.content.value.trim();
            if (!content) return;
            try {
                Chat.send(roomId, content);
                form.content.value = '';
            } catch (e) { toast(e.message, true); }
        });
    },

    /** 상대방과의 1:1 채팅방을 열고(없으면 생성) 이동 */
    async openChat(targetUserId) {
        try {
            const room = await Api.openChatRoom(targetUserId);
            location.hash = `#/chat/${room.roomId}`;
        } catch (e) { toast(e.message, true); }
    },

    async notFound(root) {
        root.innerHTML = `<div class="card center"><h1>404</h1><p>페이지를 찾을 수 없습니다.</p><a class="btn" href="#/">홈으로</a></div>`;
    },
};
