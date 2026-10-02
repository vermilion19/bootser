/* ============================================================
   TMDB 가 영화 원천이 될 수 있는지 잰다 — SPEC.md §12 와 같은 모양
   ------------------------------------------------------------
       node apps/d-day/tools/probe-tmdb.mjs

   §12 는 야구 원천을 **실측으로** 정했다. 문서가 아니라 눌러 본 응답이
   "다음 1건 · 지난 1건만 준다" 를 말했고, 거기서 창 누적이 나왔다.
   영화도 같은 방법으로 정한다 — 이 스크립트가 그 실측이다.

   ------------------------------------------------------------
   두 부분이 있다.

   **A. 키 없이 재는 것.** 일일 ID 덤프(files.tmdb.org)는 키가 없어도 받아진다.
   전체 규모와 하루 증가분이 여기서 나온다.

   **B. 키가 있어야 재는 것.** API 는 키 없이 401 이다. 아래 다섯을 묻는다.

       1. 벌크가 되는가          discover 의 total_results/total_pages
       2. 국가별 개봉일이 있는가  release_dates 의 나라 수 · 타입
       3. 변경 감지가 되는가      changes 의 창 · 주는 것
       4. 200 으로 헛소리하는가   없는 region 을 줬을 때 (§12.3 의 그 고장)
       5. 얼마나 빨리 막는가      연속 호출에서 429 가 나오는 지점

   키는 환경변수로 준다. 공개 저장소에 적지 않는다.

       TMDB_API_KEY=... node apps/d-day/tools/probe-tmdb.mjs
   ============================================================ */

/* ── 전부 주석 처리돼 있다 ─────────────────────────────

   TMDB 키를 아직 발급받지 않았다. 키 없이 돌리면 A(덤프)만 재고 B 는
   401 이라, 재지도 못한 채 「쟀다」로 보이는 출력이 남는다. 그래서 돌 수
   있을 때까지 아예 안 돌게 둔다 — 켤 수 없는 것은 꺼져 있는 것으로
   보여야 한다 (SPEC §12.6 이 스포츠 수집에 쓴 것과 같은 규칙).

   키가 생기면 이 블록을 지우고, 아래 전체에서 줄머리 // 를 벗긴 뒤 돌린다.

       sed -i -E 's|^// ?||' apps/d-day/tools/probe-tmdb.mjs
       TMDB_API_KEY=... node apps/d-day/tools/probe-tmdb.mjs

   잰 결과는 SPEC 에 절로 남긴다. §12 가 야구에 한 것과 같은 모양으로.
   ──────────────────────────────────────────────────── */

// const KEY = (process.env.TMDB_API_KEY ?? '').trim();
// const API = 'https://api.themoviedb.org/3';
// const EXPORTS = 'http://files.tmdb.org/p/exports';
//
// const log = (...a) => console.log(...a);
// const head = (t) => log(`\n=== ${t} ===`);
//
// /** 날짜를 TMDB 덤프 파일 이름의 MM_DD_YYYY 로. */
// function exportName(d) {
//     const p = (n) => String(n).padStart(2, '0');
//     return `movie_ids_${p(d.getUTCMonth() + 1)}_${p(d.getUTCDate())}_${d.getUTCFullYear()}.json.gz`;
// }
//
// /* ── A. 키 없이 ────────────────────────────────────────── */
//
// async function probeExport() {
//     head('A. 일일 ID 덤프 — 키 없이');
//
//     /* 덤프는 UTC 08:00 에 생성된다. 오늘 것이 아직 없을 수 있어 어제부터 본다. */
//     const day = (back) => new Date(Date.now() - back * 86400_000);
//     const recent = exportName(day(1));
//     const weekAgo = exportName(day(8));
//
//     const grab = async (name) => {
//         const res = await fetch(`${EXPORTS}/${name}`);
//         if (!res.ok) return { name, status: res.status, ids: null };
//         const buf = new Uint8Array(await res.arrayBuffer());
//         const { gunzipSync } = await import('node:zlib');
//         const text = gunzipSync(buf).toString('utf8');
//         const ids = new Set();
//         for (const line of text.split('\n')) {
//             if (line) ids.add(JSON.parse(line).id);
//         }
//         return { name, status: res.status, bytes: buf.length, ids };
//     };
//
//     const [now, then] = await Promise.all([grab(recent), grab(weekAgo)]);
//     if (!now.ids) {
//         log(`받지 못했다 — ${now.name} → ${now.status}`);
//         return;
//     }
//
//     log(`${now.name}  ${(now.bytes / 1e6).toFixed(1)}MB(gz) · ${now.ids.size.toLocaleString()} 편`);
//     log('덤프가 들고 있는 것: id · original_title · popularity · adult · video');
//     log('**개봉일이 없다.** 덤프만으로는 movie_release 를 못 채운다.');
//
//     if (then.ids) {
//         let added = 0;
//         let removed = 0;
//         for (const id of now.ids) if (!then.ids.has(id)) added++;
//         for (const id of then.ids) if (!now.ids.has(id)) removed++;
//         log(`7일 — 추가 ${added.toLocaleString()} · 삭제 ${removed.toLocaleString()} · 하루 평균 추가 ${Math.round(added / 7)}`);
//     }
// }
//
// /* ── B. 키가 있어야 ────────────────────────────────────── */
//
// async function call(path, params = {}) {
//     const url = new URL(API + path);
//     url.searchParams.set('api_key', KEY);
//     for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v);
//     const started = Date.now();
//     const res = await fetch(url);
//     const ms = Date.now() - started;
//     const body = await res.json().catch(() => null);
//     return { status: res.status, ms, body };
// }
//
// /** 1. 벌크가 되는가 — 무료 키가 시즌 전수를 안 준 것이 §12.2 였다. */
// async function probeBulk() {
//     head('B-1. 벌크 — 한국 개봉 예정을 한 번에 받을 수 있나');
//     const today = new Date().toISOString().slice(0, 10);
//     const r = await call('/discover/movie', {
//         region: 'KR',
//         with_release_type: '2|3',
//         'release_date.gte': today,
//         sort_by: 'primary_release_date.asc',
//         page: '1',
//     });
//     if (r.status !== 200) {
//         log(`status=${r.status}`, r.body);
//         return;
//     }
//     log(`total_results=${r.body.total_results} · total_pages=${r.body.total_pages} · page당 ${r.body.results.length}건 · ${r.ms}ms`);
//     log('첫 5건:');
//     for (const m of r.body.results.slice(0, 5)) {
//         log(`  ${m.release_date}  ${m.title}  (id=${m.id})`);
//     }
//
//     /* 페이지 상한이 있나. 있으면 「전수」가 거짓이 된다. */
//     const deep = await call('/discover/movie', { region: 'KR', page: '501' });
//     log(`page=501 → status=${deep.status}${deep.body?.status_message ? ` "${deep.body.status_message}"` : ''}`);
// }
//
// /** 2. 국가별 개봉일 — §9.7 이 「국가 없이는 개봉일이 성립하지 않는다」고 적은 그것. */
// async function probeReleaseDates() {
//     head('B-2. 국가별 개봉일 — release_dates 의 모양');
//     const TYPE = { 1: '시사', 2: '제한개봉', 3: '극장', 4: '디지털', 5: '패키지', 6: 'TV' };
//     for (const id of [155, 496243]) {      /* 다크나이트 · 기생충 */
//         const r = await call(`/movie/${id}/release_dates`);
//         if (r.status !== 200) {
//             log(`id=${id} status=${r.status}`);
//             continue;
//         }
//         const rows = r.body.results;
//         const kr = rows.find((x) => x.iso_3166_1 === 'KR');
//         log(`id=${id} — 나라 ${rows.length}개 · ${r.ms}ms`);
//         if (kr) {
//             for (const d of kr.release_dates) {
//                 log(`  KR  ${d.release_date.slice(0, 10)}  ${TYPE[d.type] ?? d.type}`);
//             }
//         } else {
//             log('  KR 없음');
//         }
//     }
// }
//
// /** 3. 변경 감지 — D-4 사슬이 여기에 걸린다. */
// async function probeChanges() {
//     head('B-3. 변경 감지 — changes 가 주는 것');
//     const d = (back) => new Date(Date.now() - back * 86400_000).toISOString().slice(0, 10);
//
//     const ok = await call('/movie/changes', { start_date: d(1), end_date: d(0), page: '1' });
//     log(`창 1일 → status=${ok.status} · total_results=${ok.body?.total_results} · total_pages=${ok.body?.total_pages} · ${ok.ms}ms`);
//     if (ok.body?.results?.length) {
//         log(`  주는 것: ${Object.keys(ok.body.results[0]).join(' · ')}`);
//         log('  **무엇이 바뀌었는지는 안 준다** — id 를 받아 상세를 다시 부르고 source_hash 로 비교해야 한다');
//     }
//
//     const wide = await call('/movie/changes', { start_date: d(20), end_date: d(0) });
//     log(`창 20일 → status=${wide.status}${wide.body?.status_message ? ` "${wide.body.status_message}"` : ' (창 제한이 안 걸렸다)'}`);
// }
//
// /** 4. 200 으로 헛소리하는가 — §12.3 에서 한 번 관측된 종류. */
// async function probeLies() {
//     head('B-4. 200 으로 헛소리하나 — 없는 region 을 준다');
//     const bogus = await call('/discover/movie', { region: 'ZZ', with_release_type: '3', page: '1' });
//     const real = await call('/discover/movie', { region: 'KR', with_release_type: '3', page: '1' });
//     log(`region=ZZ → status=${bogus.status} · total_results=${bogus.body?.total_results}`);
//     log(`region=KR → status=${real.status} · total_results=${real.body?.total_results}`);
//     const same = bogus.body?.results?.[0]?.id === real.body?.results?.[0]?.id;
//     log(same
//         ? '**같은 것을 준다 — 없는 나라를 줘도 에러가 아니라 거르지 않은 목록이다.** 오타가 조용히 지나간다'
//         : '다른 것을 준다 — region 이 실제로 걸린다');
// }
//
// /** 5. 얼마나 빨리 막는가 — RateLimiter 값의 근거. */
// async function probeRateLimit() {
//     head('B-5. 속도 제한 — 연속 호출');
//     const N = 60;
//     const started = Date.now();
//     const results = await Promise.all(
//         Array.from({ length: N }, (_, i) => call('/movie/550', { _: String(i) })),
//     );
//     const ms = Date.now() - started;
//     const codes = {};
//     for (const r of results) codes[r.status] = (codes[r.status] ?? 0) + 1;
//     log(`${N}건 동시 → ${ms}ms · ${JSON.stringify(codes)} · 실효 ${(N / (ms / 1000)).toFixed(1)} rps`);
//     log(codes[429] ? '**429 가 나왔다** — 이 수가 RateLimiter 상한의 근거다' : '429 없음');
// }
//
// /* ── ─────────────────────────────────────────────────── */
//
// await probeExport();
//
// if (!KEY) {
//     head('B. 키가 없다');
//     log('TMDB_API_KEY 가 비어 있어 API 를 안 부른다.');
//     log('키 없이 부르면 401 "Invalid API key" 다 — 무료 키도 발급이 필요하다.');
//     log('');
//     log('    TMDB_API_KEY=... node apps/d-day/tools/probe-tmdb.mjs');
//     process.exit(0);
// }
//
// await probeBulk();
// await probeReleaseDates();
// await probeChanges();
// await probeLies();
// await probeRateLimit();
