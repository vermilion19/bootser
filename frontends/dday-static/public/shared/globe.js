/* ============================================================
   나라를 고르는 또 하나의 손잡이. 넓은 화면에서는 첫 화면 오른쪽 여백에서 돌고,
   좁은 화면에서는 국가 목록 위에 접혀 있다가 눌러서 펼친다.

   왜 라이브러리가 없나 — 정사영(orthographic) 투영은 세 줄이고 역투영도 그만큼이다.
   클릭 판정은 역투영해서 가장 가까운 점을 찾는 것이라 색 버퍼도 레이캐스팅도
   필요 없다. 이 저장소가 태양 위치를 172항으로 계산하는데 지구본에 600KB 를
   받아 올 이유가 없다.

   왜 점이고 국경이 아닌가 — tools/gen-globe.mjs 의 머리말에 적었다.
   줄이면, 204개국에 소국이 많아서 국경으로 칠하면 몰타를 못 고른다.

   나라 이름과 주소는 자료로 받지 않는다. 첫 화면에 이미 204개 링크가
   li[data-cc] 로 깔려 있으므로 그것을 읽는다 — 늘어나는 바이트가 0 이고,
   목록에서 빠진 나라가 지구본에도 저절로 없다.

   보조기술에는 이 캔버스를 감춘다(aria-hidden). 같은 링크가 아래 목록에 그대로
   있으므로, 닿지 못하는 위젯을 나란히 만드는 것보다 그게 맞다. 펼치기 버튼도
   같은 이유로 감추고 탭 순서에서 뺀다 — 보조기술에 닿지 않는 것을 여는 버튼이
   탭 순서에 끼면 그 자체가 막다른 길이다.

   좁은 화면에서 자료를 미리 받지 않는다. data/globe.json 은 gzip 13.5KB 라
   보지도 않을 것을 받는 값이 아니다. CSS 로 감추기만 하면 받고 안 보이는 상태가
   되므로 게이트를 여기 둔다 — 넓은 화면은 바로 돌고, 좁은 화면은 버튼을 누른
   그 순간에 처음 받는다. check-pages 가 그 두 갈래를 다 밟아 본다.

   손가락과 마우스는 다른 물건이다. 손가락에는 hover 가 없어서 「한 번 눌러
   이름을 띄우고, 그 이름을 눌러 옮긴다」 로 두 걸음을 만들었다 — 소앤틸리스처럼
   점이 겹치는 곳에서 한 번에 옮기면 엉뚱한 나라로 뜬다. 잡히는 반경도 손가락
   쪽이 두 배 가깝고(GRAB_T), 끌린 것으로 보는 문턱도 넉넉하다(SLOP_T).
   ============================================================ */
'use strict';

(function () {

    /* 다시 그려야 하는지. 화면 칸의 값이지만 mark() 도 건드리므로 여기 둔다 —
       mark() 는 그리기가 아예 안 도는 화면 폭에서도 불린다. */
    var dirty = true;

    /* ----------------------------------------------------------- 순수 계산

       하니스는 캔버스를 못 본다. 그래서 계산을 전부 이 칸에 두고 window.GLOBE 로
       내보낸다 — check-pages 가 그리기를 거치지 않고 이 함수들을 직접 때린다.
       아래 「화면」 칸은 얇은 껍데기여야 한다. */

    var RAD = Math.PI / 180;

    /**
     * 정사영. 단위구 위의 (lon, lat) 을 보는 방향 (l0, p0) 기준 평면으로 옮긴다.
     * z > 0 이 앞면이고 뒷면은 그리지 않는다.
     * y 는 위쪽이 양수다 — 캔버스는 아래가 양수라 그릴 때 뒤집는다.
     */
    function project(lon, lat, l0, p0) {
        var dl = (lon - l0) * RAD, p = lat * RAD, q = p0 * RAD;
        var cp = Math.cos(p), sp = Math.sin(p);
        var cq = Math.cos(q), sq = Math.sin(q), cd = Math.cos(dl);
        return {
            x: cp * Math.sin(dl),
            y: cq * sp - sq * cp * cd,
            z: sq * sp + cq * cp * cd
        };
    }

    /** 역투영. 원판 밖(x²+y² > 1)이면 null 이다. */
    function unproject(x, y, l0, p0) {
        var r2 = x * x + y * y;
        if (r2 > 1) return null;
        var z = Math.sqrt(1 - r2), q = p0 * RAD;
        var cq = Math.cos(q), sq = Math.sin(q);
        return [
            l0 + Math.atan2(x, z * cq - y * sq) / RAD,
            Math.asin(z * sq + y * cq) / RAD
        ];
    }

    /**
     * 화면 좌표 (sx, sy) 의 grab 안에 드는 **앞면** 점 전부를 가까운 순으로.
     * pts 는 [code, lon, lat] 배열, 좌표는 원 중심 기준 픽셀이다.
     * r 은 확대가 반영된 반지름이다 — 확대는 이 값만 키우는 것이 전부다.
     */
    function tied(sx, sy, pts, l0, p0, r, grab) {
        var out = [], lim = grab * grab;
        for (var i = 0; i < pts.length; i++) {
            var v = project(pts[i][1], pts[i][2], l0, p0);
            if (v.z <= 0) continue;
            var dx = v.x * r - sx, dy = -v.y * r - sy;
            var d = dx * dx + dy * dy;
            if (d <= lim) out.push([i, d]);
        }
        out.sort(function (a, b) { return a[1] - b[1]; });
        return out.map(function (e) { return e[0]; });
    }

    /** 가장 가까운 앞면 점의 자리번호. 없으면 -1. */
    function pick(sx, sy, pts, l0, p0, r, grab) {
        var all = tied(sx, sy, pts, l0, p0, r, grab);
        return all.length ? all[0] : -1;
    }

    /* 회전하려고 끌었는데 손을 떼는 순간 점이 눌린 것으로 판정되는 일이 있었다.
       끌린 거리가 SLOP 을 넘으면 회전으로 본다. 거리만으로는 모자라다 —
       빈 자리를 누르고 점 위에서 놓아도 눌린 것이 되므로, 누른 곳과 놓은 곳이
       **같은 점**인 것까지 본다.

       손가락은 가만히 누르려 해도 이만큼은 움직인다. 마우스 문턱을 그대로 쓰면
       누른 것이 죄다 회전으로 넘어가 아무것도 안 골라진다. */
    var SLOP = 6, SLOP_T = 12;

    /**
     * 놓은 자리를 「눌렀다」로 볼지. moved 는 누른 뒤 끌린 거리(픽셀),
     * from 은 누를 때 잡힌 자리번호, upAt 은 놓을 때 잡힌 자리번호다.
     * slop 을 안 주면 마우스 문턱이다.
     */
    function clickable(moved, from, upAt, slop) {
        return moved <= (slop === undefined ? SLOP : slop) && from >= 0 && upAt === from;
    }

    /* 확대 범위. ×1 에서 소앤틸리스 18개국이 한 번에 겹치는데 ×8 이면 4개로
       줄고 그 뒤로는 평평해진다(AI·BL·MF·SX 는 서로 30km 안이라 어떤 배율로도
       갈라지지 않는다). 그래서 8 에서 멈춘다 — 더 키워도 얻는 것이 없고 해안선만
       거칠어진다. */
    var MAX_ZOOM = 8;

    /** 범위 안으로 자른다. 확대는 어느 길로 들어와도 이 문을 지난다. */
    function clamp(z) {
        return Math.max(1, Math.min(MAX_ZOOM, z));
    }

    /** 휠 한 번을 배율로. */
    function rezoom(zoom, deltaY) {
        return clamp(zoom * Math.exp(-deltaY * 0.0015));
    }

    /**
     * 두 손가락을 벌린 것을 배율로. d0 은 손가락이 두 개가 된 순간의 거리,
     * d 는 지금 거리다. 거리의 비를 그때의 배율에 그대로 곱한다 —
     * 벌린 만큼 커지고, 손가락을 처음 자리로 되돌리면 배율도 되돌아온다.
     */
    function pinch(zoom0, d0, d) {
        if (!(d0 > 0) || !(d > 0)) return clamp(zoom0);
        return clamp(zoom0 * d / d0);
    }

    /* 두 번 누르기. 손가락에는 dblclick 이 안 오는 기기가 있어 간격과 거리로
       직접 잡는다. 확대해 놓고 길을 잃었을 때 유일한 출구다. */
    var TAP_MS = 300, TAP_PX = 24;

    /** 앞 탭과 시간·자리가 둘 다 가까우면 두 번 누른 것이다. */
    function retap(dt, dx, dy) {
        return dt >= 0 && dt <= TAP_MS
            && Math.abs(dx) <= TAP_PX && Math.abs(dy) <= TAP_PX;
    }

    /* 점 크기와 잡히는 반경. 좁은 화면은 지구본이 화면 폭을 거의 다 쓰므로 점을
       조금 키운다 — 같은 2.6px 이 손가락 밑에서는 안 보인다. 잡히는 반경은 그보다
       훨씬 넉넉하게 둔다. 크게 잡아도 pick() 이 가장 가까운 것을 고르므로, 넓은
       반경은 「못 잡는 일이 없다」 는 뜻일 뿐이다 — 손가락은 두 걸음으로 옮기니
       잘못 잡혀도 이름을 보고 되돌릴 수 있다. */
    var DOT_WIDE = 2.6, DOT_TOUCH = 3.2, GRAB = 11, GRAB_T = 22;

    /**
     * 지구본 반지름. 상자 폭 w 와 점 크기 dot 에서 나온다 — 점이 창틀에 물리지
     * 않게 그만큼 빼 둔다. 화면 기하지만 계산이라 이 칸에 있다: 하니스가 화면과
     * **같은 값**을 짚어야 포인터를 흉내낼 수 있다.
     */
    function radius(w, dot) {
        return w / 2 - dot - 2;
    }

    /* 오늘 공휴일인 나라. **지구본이 자료를 또 받지 않는다** — 첫 화면의
       「오늘 공휴일인 나라」 절을 채우는 dday.js 가 이미 계산하므로 그것을
       넘겨받는다. 같은 규칙을 두 벌 두면 같은 날에 두 화면이 다른 말을 한다.

       화면 게이트 앞에 두는 것이 중요하다. 좁은 화면에서 접혀 있는 동안에는
       그리기가 돌지 않는데, mark() 는 그때도 불린다(dday.js 는 화면 폭을 모른다). */
    var holidays = null;

    /** 코드 목록을 받아 둔다. 몇 나라가 잡혔는지 돌려준다. */
    function mark(codes) {
        holidays = null;
        if (codes && codes.length) {
            holidays = {};
            for (var i = 0; i < codes.length; i++) holidays[codes[i]] = 1;
        }
        dirty = true;
        return holidays ? Object.keys(holidays).length : 0;
    }

    /** 받아 둔 코드. 검사기가 되읽는다. */
    function marked() {
        return holidays ? Object.keys(holidays).sort() : [];
    }

    window.GLOBE = {
        project: project, unproject: unproject, pick: pick, tied: tied,
        clickable: clickable, SLOP: SLOP, SLOP_T: SLOP_T,
        rezoom: rezoom, pinch: pinch, MAX_ZOOM: MAX_ZOOM,
        retap: retap, TAP_MS: TAP_MS, TAP_PX: TAP_PX,
        radius: radius, GRAB: GRAB, GRAB_T: GRAB_T,
        DOT_WIDE: DOT_WIDE, DOT_TOUCH: DOT_TOUCH,
        mark: mark, marked: marked
    };

    /* --------------------------------------------------------------- 화면 */

    /* 본문 칸 820px 의 오른쪽 여백에 지구본이 들어가는 폭. dday.css 의
       min-width 와 짝이고, 그 아래는 max-width:1399px 로 이어받는다.
       두 값이 갈라지면 화면과 코드가 서로 다른 자리를 믿게 되므로
       check-pages 가 문턱 셋을 맞춰 본다. */
    var WIDE = '(min-width: 1400px)';
    var SPIN = 5;                      /* 도/초 — 손을 대면 멈춘다 */

    var box = document.getElementById('globe');
    if (!box) return;

    var wide = !!(window.matchMedia && window.matchMedia(WIDE).matches);

    var cv = box.querySelector('canvas');
    var out = box.querySelector('.globe-name');
    if (!cv || !cv.getContext || !out) return;

    var DOT = wide ? DOT_WIDE : DOT_TOUCH;

    /** 이 손가락(또는 마우스)에 맞는 잡히는 반경. */
    function grabFor(e) {
        return e.pointerType && e.pointerType !== 'mouse' ? GRAB_T : GRAB;
    }

    /** 손가락으로 다루는 중인가. 화면 폭이 아니라 실제로 닿은 것으로 가른다. */
    function byTouch(e) {
        return !!(e.pointerType && e.pointerType !== 'mouse');
    }

    /* 이름과 주소는 첫 화면의 국가 목록에서 읽는다 */
    var meta = {};
    Array.prototype.forEach.call(
        document.querySelectorAll('#countries li[data-cc]'),
        function (li) {
            var a = li.querySelector('a'), n = li.querySelector('.cn');
            if (a && n) {
                meta[li.getAttribute('data-cc')] = {
                    href: a.getAttribute('href'), name: n.textContent
                };
            }
        }
    );

    var ctx = cv.getContext('2d');
    var pts = null, land = null, l0 = 127, p0 = 18, r = 0, cx = 0, cy = 0;
    var hot = -1, spinning = true, last = 0, zoom = 1;
    var moved = 0, downAt = -1;

    /* 눌려 있는 손가락. 두 개가 되면 확대고, 그동안 회전과 누르기는 쉰다.
       pinched 는 「이번에 확대를 했다」는 걸쇠로, 마지막 손가락이 뜰 때까지 안 풀린다 —
       벌린 뒤 손을 떼는 것을 누른 것으로 읽으면 배율이 제멋대로 되돌아간다. */
    var down = {}, pinching = false, pinched = false, baseD = 0, baseZ = 1;
    var tapAt = 0, tapX = 0, tapY = 0;

    /* 확대는 반지름만 키운다. 원판(구멍)은 r 로 그대로 두고 안쪽만 커지므로
       배율을 올리면 창문 크기는 같은 채 내용이 확대된다. */
    function R() { return r * zoom; }

    var root = document.documentElement;
    function ink(name) {
        return getComputedStyle(root).getPropertyValue(name).trim() || '#888';
    }

    function size() {
        var w = box.clientWidth;
        /* 접혀 있으면 폭이 0 이다. 그대로 재면 반지름이 음수가 되고 다음 프레임의
           ctx.arc 가 그 자리에서 던진다 — 접은 채 화면을 돌리면 그렇게 됐다.
           돌아서면 앞서 재 둔 값이 그대로 남으므로, 다시 펼칠 때 쓰인다. */
        if (!(w > 0)) return;
        var dpr = window.devicePixelRatio || 1;
        cv.width = Math.round(w * dpr);
        cv.height = Math.round(w * dpr);
        cv.style.height = w + 'px';
        ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        cx = w / 2; cy = w / 2; r = radius(w, DOT);
        dirty = true;
    }

    /** 한 줄을 앞면 토막만 이어 그린다. 뒷면에서 끊고 다시 시작한다. */
    function arc(step, make) {
        ctx.beginPath();
        var on = false;
        for (var t = step[0]; t <= step[1]; t += step[2]) {
            var v = make(t);
            if (v.z <= 0) { on = false; continue; }
            var X = cx + v.x * R(), Y = cy - v.y * R();
            if (on) ctx.lineTo(X, Y); else { ctx.moveTo(X, Y); on = true; }
        }
        ctx.stroke();
    }

    /** 링 하나(펴 담은 [lon, lat, ...])를 앞면 토막만 이어 그린다. */
    function ring(flat) {
        ctx.beginPath();
        var on = false;
        for (var i = 0; i < flat.length; i += 2) {
            var v = project(flat[i], flat[i + 1], l0, p0);
            if (v.z <= 0) { on = false; continue; }
            var X = cx + v.x * R(), Y = cy - v.y * R();
            if (on) ctx.lineTo(X, Y); else { ctx.moveTo(X, Y); on = true; }
        }
        ctx.stroke();
    }

    /** 대륙 윤곽. 칠하지 않는다 — 까닭은 tools/gen-globe.mjs 에 적었다. */
    function coast() {
        if (!land) return;
        ctx.strokeStyle = ink('--ink-3');
        ctx.lineWidth = 1;
        ctx.globalAlpha = 0.45;
        for (var i = 0; i < land.length; i++) ring(land[i]);
        ctx.globalAlpha = 1;
    }

    /** 위선·경선 격자. 30도마다 한 줄이다. */
    function graticule() {
        ctx.strokeStyle = ink('--rule');
        ctx.lineWidth = 1;
        ctx.globalAlpha = 0.5;
        /* 확대하면 30도 격자가 화면 밖으로 다 나간다. 간격과 걸음을 같이 줄인다. */
        var gap = zoom >= 4 ? 10 : 30, step = zoom >= 4 ? 1 : 3, k;
        for (k = -180; k < 180; k += gap) {
            arc([-90, 90, step], (function (m) {
                return function (t) { return project(m, t, l0, p0); };
            })(k));
        }
        for (k = -80; k <= 80; k += gap) {
            arc([-180, 180, step], (function (m) {
                return function (t) { return project(t, m, l0, p0); };
            })(k));
        }
        ctx.globalAlpha = 1;
    }

    function draw() {
        ctx.clearRect(0, 0, cv.width, cv.height);
        ctx.save();
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.clip();
        graticule();
        coast();
        /* 자료가 아직 없어도 restore 까지는 지나가야 한다 — 여기서 돌아서면
           save 한 것이 쌓인 채 다음 프레임이 또 save 한다. */
        if (pts) {
            var dim = ink('--ink-3'), lit = ink('--ink'), red = ink('--today');
            for (var i = 0; i < pts.length; i++) {
                var v = project(pts[i][1], pts[i][2], l0, p0);
                if (v.z <= 0) continue;
                var big = i === hot;
                /* 오늘 쉬는 나라. 손이 얹힌 점은 크기로 알리고 색은 빨강을 지킨다 —
                   가리키는 동안 「오늘 쉰다」는 사실이 사라지면 안 된다. */
                var off = !!(holidays && holidays[pts[i][0]]);
                ctx.beginPath();
                ctx.arc(cx + v.x * R(), cy - v.y * R(),
                    big ? DOT + 2 : (off ? DOT + 0.8 : DOT), 0, Math.PI * 2);
                ctx.fillStyle = off ? red : (big ? lit : dim);
                /* 지평선 가까이를 연하게 한다 — 구로 보이게 하는 것은 이 한 줄이다 */
                ctx.globalAlpha = big ? 1 : 0.35 + 0.65 * v.z;
                ctx.fill();
            }
            ctx.globalAlpha = 1;
        }
        ctx.restore();
        /* 창틀은 자르기 밖에서 긋는다 — 안에서 그으면 확대할 때 같이 커진다 */
        ctx.strokeStyle = ink('--rule');
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.stroke();
    }

    /* 손을 대지 않으면 그리지 않는다. 옛 판은 멈춘 지구본을 초당 60번 다시
       그렸는데, 손가락으로 보는 화면에서 그것은 그냥 배터리다. */
    function frame(t) {
        if (spinning) {
            if (last) l0 = (l0 + SPIN * (t - last) / 1000 + 540) % 360 - 180;
            last = t;
            dirty = true;
        } else last = 0;
        if (dirty) { dirty = false; draw(); }
        requestAnimationFrame(frame);
    }

    function at(e) {
        var b = cv.getBoundingClientRect();
        return [e.clientX - b.left - cx, e.clientY - b.top - cy];
    }

    function say(i) {
        if (hot === i) return;
        hot = i;
        dirty = true;
        var m = i < 0 || !pts ? null : meta[pts[i][0]];
        out.textContent = '';
        cv.style.cursor = m ? 'pointer' : 'grab';
        if (!m) return;
        if (wide) { out.textContent = m.name; return; }
        /* 손가락에는 두 번째 걸음이 필요하다. 이름 자체를 링크로 세워 누를 자리를
           만든다 — aria-hidden 안이라 탭 순서에서는 뺀다. */
        var a = document.createElement('a');
        a.setAttribute('href', m.href);
        a.setAttribute('tabindex', '-1');
        a.textContent = m.name + ' →';
        out.appendChild(a);
    }

    /** 눌려 있는 두 손가락 사이 거리. 두 개가 아니면 0. */
    function spread() {
        var k = Object.keys(down);
        if (k.length !== 2) return 0;
        var a = down[k[0]], b = down[k[1]];
        return Math.sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y));
    }

    cv.addEventListener('pointerdown', function (e) {
        down[e.pointerId] = { x: e.clientX, y: e.clientY };
        spinning = false;
        dirty = true;
        if (Object.keys(down).length === 1) {
            moved = 0;
            var p = at(e);
            downAt = pts ? pick(p[0], p[1], pts, l0, p0, R(), grabFor(e)) : -1;
            if (cv.setPointerCapture) cv.setPointerCapture(e.pointerId);
            return;
        }
        /* 두 번째 손가락. 확대로 넘어가므로 잡아 둔 점을 놓는다 —
           벌리다가 손을 떼는 것이 나라 이동이 되면 안 된다. */
        pinching = true;
        pinched = true;
        downAt = -1;
        baseD = spread();
        baseZ = zoom;
        say(-1);
    });

    cv.addEventListener('pointermove', function (e) {
        var prev = down[e.pointerId];
        if (!prev) {
            /* 누르지 않은 채 지나간다 — 마우스뿐이다 */
            var q = at(e);
            say(pts ? pick(q[0], q[1], pts, l0, p0, R(), grabFor(e)) : -1);
            return;
        }
        /* 움직인 양을 직접 뺀다. e.movementX 를 쓰던 자리인데, 그 값은 손가락
           쪽에서 0 으로 오는 브라우저가 있어 모바일에서 지구가 안 돌았다. */
        var dx = e.clientX - prev.x, dy = e.clientY - prev.y;
        prev.x = e.clientX;
        prev.y = e.clientY;
        dirty = true;

        if (pinching) {
            zoom = pinch(baseZ, baseD, spread());
            say(-1);
            return;
        }
        moved += Math.abs(dx) + Math.abs(dy);
        /* 확대하면 같은 픽셀이 더 좁은 각도에 해당한다. 나누지 않으면
           ×8 에서 손을 조금만 움직여도 지구가 달아난다. */
        var deg = 0.4 / zoom;
        l0 = (l0 - dx * deg + 540) % 360 - 180;
        p0 = Math.max(-85, Math.min(85, p0 + dy * deg));
        say(-1);
    });

    function lift(e) {
        var had = down[e.pointerId];
        delete down[e.pointerId];
        var rest = Object.keys(down).length;
        dirty = true;

        /* 손가락이 하나로 줄면 회전을 이어 간다. 저마다의 마지막 자리를 계속
           갱신하고 있으므로 이어받아도 지구가 어긋나지 않는다. */
        pinching = false;

        var from = downAt;
        downAt = -1;
        if (pinched) {
            /* 확대를 끝낸 손을 떼는 것은 누른 것이 아니다. 마지막 손가락까지
               떠야 걸쇠가 풀린다 — 안 그러면 벌린 뒤 떼는 것이 두 번 누르기로
               읽혀 배율이 되돌아간다. */
            if (!rest) pinched = false;
            return;
        }
        if (!had || !pts) return;

        var touch = byTouch(e);
        var now = Date.now();
        /* 두 번 눌러 처음으로. ×1 에서는 되돌릴 것이 없으므로 보지 않는다 —
           그래야 점을 두 번 눌러도 배율이 흔들리지 않는다. */
        if (touch && zoom > 1 && retap(now - tapAt, e.clientX - tapX, e.clientY - tapY)) {
            tapAt = 0;
            zoom = 1;
            say(-1);
            return;
        }
        tapAt = now; tapX = e.clientX; tapY = e.clientY;

        var p = at(e);
        var upAt = pick(p[0], p[1], pts, l0, p0, R(), grabFor(e));
        if (!clickable(moved, from, upAt, touch ? SLOP_T : SLOP)) {
            /* 빈 자리를 눌렀으면 앞서 띄운 이름을 접는다. 손가락 쪽은 아래에서
               pointerleave 를 안 듣기로 했으므로 지우는 자리가 여기뿐이다.
               끌린 경우는 pointermove 가 이미 지웠다. */
            if (touch && from < 0 && upAt < 0) say(-1);
            return;
        }
        if (!meta[pts[from][0]]) return;
        /* 마우스는 한 걸음, 손가락은 두 걸음이다 — 까닭은 머리말에 적었다. */
        if (touch) say(from);
        else location.href = meta[pts[from][0]].href;
    }

    cv.addEventListener('pointerup', lift);
    cv.addEventListener('pointercancel', lift);

    cv.addEventListener('wheel', function (e) {
        e.preventDefault();
        spinning = false;
        zoom = rezoom(zoom, e.deltaY);
        say(-1);
        dirty = true;
    }, { passive: false });

    /* 되돌리기. 손가락 쪽은 lift() 가 직접 잡는다. */
    cv.addEventListener('dblclick', function () { zoom = 1; say(-1); dirty = true; });

    /* 손가락은 pointerup 뒤에 pointerout · pointerleave 가 따라온다 — 떼는 순간
       포인터 자체가 없어지므로 규격이 그렇게 정해 두었다. 그것을 그대로 받으면
       lift() 가 방금 띄운 이름이 같은 걸음에서 지워져, 두 걸음의 두 번째 걸음이
       사라진다. 모바일에서 「점을 눌러도 아무 일도 안 난다」 던 것이 이것이다.
       hover 가 있는 마우스만 이 문을 지난다. */
    cv.addEventListener('pointerleave', function (e) { if (!byTouch(e)) say(-1); });
    window.addEventListener('resize', size);

    var started = false;

    function load() {
        fetch('/data/globe.json').then(function (res) {
            return res.ok ? res.json() : null;
        }).then(function (d) {
            /* 목록에 없는 나라는 버린다 — 눌러도 갈 곳이 없다 */
            if (!d) return;
            if (d.p) pts = d.p.filter(function (p) { return meta[p[0]]; });
            if (d.l) land = d.l;
            dirty = true;
        }).catch(function () {
            /* 지구본은 덤이다. 못 받으면 격자만 돈다. */
        });
    }

    /** 펼치는 순간. 자료를 받는 것도 그리기를 시작하는 것도 여기 한 번뿐이다. */
    function start() {
        box.classList.add('on');
        if (started) { size(); return; }
        started = true;
        size();
        requestAnimationFrame(frame);
        load();
    }

    if (wide) {
        box.classList.add('wide');
        start();
        return;
    }

    /* 좁은 화면. 버튼이 없으면 조용히 아무것도 안 한다 — 지구본은 덤이고,
       같은 나라가 바로 아래 목록에 다 있다. */
    box.classList.add('touch');
    var btn = document.getElementById('globeopen');
    if (!btn) return;
    btn.removeAttribute('hidden');

    btn.addEventListener('click', function () {
        if (box.classList.contains('on')) {
            box.classList.remove('on');
            spinning = false;               /* 접힌 채로 돌지 않는다 */
            btn.textContent = btn.getAttribute('data-open');
            return;
        }
        /* 목록 바로 위로 옮긴다. 마크업에서는 넓은 화면 자리(카드 앞)에 있고,
           거기서 오른쪽 여백에 절대배치로 걸린다 — 그 자리를 건드리지 않으려면
           좁은 화면에서 옮기는 쪽이 맞다. */
        var list = document.getElementById('clist');
        if (!started && list && list.parentNode) {
            list.parentNode.insertBefore(box, list);
        }
        start();
        spinning = true;
        last = 0;
        btn.textContent = btn.getAttribute('data-close');
    });

})();
