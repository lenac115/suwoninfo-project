/**
 * 해시 기반 라우터 (#/board/FREE, #/post/1 ...)
 * 서버 쪽 라우팅 설정 없이 index.html 하나로 동작한다.
 */
const routes = [
    [/^\/$/, Views.home],
    [/^\/board\/(?<type>FREE|TRADE)$/, Views.board],
    [/^\/post\/(?<id>\d+)$/, Views.post],
    [/^\/write\/(?<type>FREE|TRADE)$/, Views.editor],
    [/^\/edit\/(?<id>\d+)$/, Views.editor],
    [/^\/login$/, Views.login],
    [/^\/signup$/, Views.signup],
    [/^\/mypage$/, Views.mypage],
    [/^\/todo$/, Views.todo],
];

const App = {
    async route() {
        const hash = location.hash.slice(1) || '/';
        const [path, qs] = hash.split('?');
        const query = new URLSearchParams(qs || '');
        const root = document.getElementById('app');

        document.querySelectorAll('[data-nav]').forEach(a => {
            a.classList.toggle('active', path.includes('/' + a.dataset.nav));
        });

        for (const [re, view] of routes) {
            const m = path.match(re);
            if (m) {
                window.scrollTo(0, 0);
                await view(root, { ...(m.groups || {}), query });
                return;
            }
        }
        Views.notFound(root);
    },

    renderNav() {
        const el = document.getElementById('nav-user');
        if (Auth.isLoggedIn()) {
            const me = Auth.me;
            el.innerHTML = `
                <a href="#/mypage" class="nav-me">${esc(me ? me.nickname : '내 정보')}</a>
                <button class="btn small" id="nav-logout">로그아웃</button>`;
            el.querySelector('#nav-logout').addEventListener('click', App.logout);
        } else {
            el.innerHTML = `<a class="btn small" href="#/login">로그인</a>
                            <a class="btn small primary" href="#/signup">회원가입</a>`;
        }
        document.querySelectorAll('[data-auth]').forEach(a => { a.hidden = !Auth.isLoggedIn(); });
    },

    async logout() {
        try { await Api.logout(); } catch { /* 토큰이 이미 만료된 경우에도 로컬 상태는 정리 */ }
        Auth.clear();
        window.dispatchEvent(new Event('auth-changed'));
        toast('로그아웃되었습니다.');
        location.hash = '#/';
    },
};

window.addEventListener('hashchange', App.route);
window.addEventListener('auth-changed', App.renderNav);

(async () => {
    // 저장된 토큰이 있으면 사용자 정보를 갱신 (만료되었으면 api.js가 재발급/정리)
    if (Auth.isLoggedIn()) {
        try { Auth.me = await Api.info(); } catch { Auth.clear(); }
    }
    App.renderNav();
    App.route();
})();
