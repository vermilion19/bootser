/* ============================================================
   ELP2000-82B 달 급수를 원천에서 받아 잘라 낸다
   ------------------------------------------------------------
       node apps/d-day/tools/gen-elp2000.mjs

   원천: CDS VI/79 (Chapront-Touze & Chapront, 1988). 참조 구현 elp82b.f 와
   자료 파일 36개가 거기 있다. **그 Fortran 을 그대로 옮긴다** — 계수도 인자
   구성도 기억으로 적지 않는다 (tools/gen-vsop87.mjs 와 같은 규칙).

   ─────────────────────────────────────────────────────────────
   왜 생성기가 이렇게 두꺼운가 — 자바를 얇게 만들려고 그렇다.

   ELP82B 는 자료 파일을 세 무리로 나누고 무리마다 인자를 다르게 짠다.
     · 주문제(1~3)        : 들로네 인자 넷 × t^0..t^4
     · 지구꼴·조석 등(4~9, 22~36) : 위상 + zeta + 들로네 인자 × t^0..t^1
     · 행성 섭동(10~21)   : 위상 + 행성 평균황경 여덟 + 들로네 × t^0..t^1
   그런데 셋 다 결국 **인자가 t 의 다항식**이다. 그래서 여기서 다항식 계수로
   펴 두면 자바는 갈래를 몰라도 된다 — 진폭 · t 차수 · 다항식 계수 다섯이면 끝이다.

   갈래를 자바에 남기면 자바가 ELP 의 내부 구조를 알아야 하고, 그 지식이 두 군데
   (여기와 저기)에 생긴다. 한 군데에 둔다.
   ============================================================ */

import { writeFileSync, mkdirSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', 'astro-core', 'src', 'main', 'resources', 'astro', 'elp2000-moon.txt');
const CACHE = join(HERE, '..', '..', '..', '.gradle', 'elp2000');
const BASE = 'https://cdsarc.cds.unistra.fr/ftp/VI/79';

/* 버린 항의 진폭 합 상한. 변수마다 단위가 다르다.

   황경 0.5초각은 삭망 시각으로 1초쯤이다 — 달과 해의 이각이 시간당 0.508°로
   벌어지므로 1분이 30.5초각이다. 거리는 이각에 아무 영향이 없어 넉넉히 둔다. */
const BUDGET = {
    1: { name: 'V', unit: 'arcsec', max: 0.5 },   // 황경
    2: { name: 'U', unit: 'arcsec', max: 2.0 },   // 황위
    3: { name: 'R', unit: 'km', max: 5.0 },       // 거리
};

/* 우리가 답하기로 한 연도 범위에서 |T|(J2000 기준 율리우스 세기)의 최대값.
   1583년이 -4.17, 2999년이 +10.0 이다. t 가 곱해진 항은 그만큼 커지므로
   자를 때 무게로 쓴다 — VSOP87(천년 단위라 |T|<=1)과 다른 점이다. */
const MAX_T = 10.0;

const DEG = Math.PI / 180;
const RAD = 648000 / Math.PI;          // 초각 → 라디안의 역수

/* ─────────────────────────────────────────── elp82b.f 의 상수 그대로 */

const c1 = 60, c2 = 3600;

const w = [[], [], []];                 // w[body][power] — 1-based 를 0-based 로
w[0] = [(218 + 18 / c1 + 59.95571 / c2) * DEG, 1732559343.73604 / RAD, -5.8883 / RAD, 0.6604e-2 / RAD, -0.3169e-4 / RAD];
w[1] = [(83 + 21 / c1 + 11.67475 / c2) * DEG, 14643420.2632 / RAD, -38.2776 / RAD, -0.45047e-1 / RAD, 0.21301e-3 / RAD];
w[2] = [(125 + 2 / c1 + 40.39816 / c2) * DEG, -6967919.3622 / RAD, 6.3622 / RAD, 0.7625e-2 / RAD, -0.3586e-4 / RAD];

const eart = [(100 + 27 / c1 + 59.22059 / c2) * DEG, 129597742.2758 / RAD, -0.0202 / RAD, 0.9e-5 / RAD, 0.15e-6 / RAD];
const peri = [(102 + 56 / c1 + 14.42753 / c2) * DEG, 1161.2283 / RAD, 0.5327 / RAD, -0.138e-3 / RAD, 0.0];

const preces = 5029.0966 / RAD;

/* 행성 평균황경 여덟 — 수성 · 금성 · 지구 · 화성 · 목성 · 토성 · 천왕성 · 해왕성 */
const p = [
    [(252 + 15 / c1 + 3.25986 / c2) * DEG, 538101628.68898 / RAD],
    [(181 + 58 / c1 + 47.28305 / c2) * DEG, 210664136.43355 / RAD],
    [eart[0], eart[1]],
    [(355 + 25 / c1 + 59.78866 / c2) * DEG, 68905077.59284 / RAD],
    [(34 + 21 / c1 + 5.34212 / c2) * DEG, 10925660.42861 / RAD],
    [(50 + 4 / c1 + 38.89694 / c2) * DEG, 4399609.65932 / RAD],
    [(314 + 3 / c1 + 18.01841 / c2) * DEG, 1542481.19393 / RAD],
    [(304 + 20 / c1 + 55.19575 / c2) * DEG, 786550.32074 / RAD],
];

/* DE200/LE200 에 맞춘 상수 보정 */
const ath = 384747.9806743165;
const a0 = 384747.9806448954;
const am = 0.074801329518;
const alfa = 0.002571881335;
const dtasm = 2 * alfa / (3 * am);
const delnu = +0.55604 / RAD / w[0][1];
const dele = +0.01789 / RAD;
const delg = -0.08066 / RAD;
const delnp = -0.06424 / RAD / w[0][1];
const delep = -0.12879 / RAD;

/* 들로네 인자 넷 — D · l' · l · F. 각각 t^0..t^4 계수를 갖는다 */
const del = [[], [], [], []];
for (let k = 0; k < 5; k++) {
    del[0][k] = w[0][k] - eart[k];      // D
    del[1][k] = eart[k] - peri[k];      // l' (태양의 평균근점이각)
    del[2][k] = w[0][k] - w[1][k];      // l  (달의 평균근점이각)
    del[3][k] = w[0][k] - w[2][k];      // F  (승교점에서 잰 위도인수)
}
del[0][0] += Math.PI;

const zeta = [w[0][0], w[0][1] + preces];

/* ─────────────────────────────────────────── 파일 받기 */

async function file(index) {
    const name = `ELP${index}`;
    const cached = join(CACHE, name);
    if (existsSync(cached)) {
        return readFileSync(cached, 'utf8');
    }
    const res = await fetch(`${BASE}/${name}`);
    if (!res.ok) throw new Error(`${name} → HTTP ${res.status}`);
    const text = await res.text();
    try {
        mkdirSync(CACHE, { recursive: true });
        writeFileSync(cached, text);
    } catch { /* 캐시는 있으면 좋은 것일 뿐이다 */ }
    return text;
}

/* 고정 폭으로 자른다. 공백으로 쪼개면 안 된다 — i3 칸에 -10 이 들어가면
   앞 칸과 붙어 버린다(" -1-10"). Fortran 형식이 칸을 세는 이유가 그것이다. */
const int3 = (line, at) => parseInt(line.slice(at, at + 3).trim() || '0', 10);
const num = (line, at, width) => parseFloat(line.slice(at, at + width).trim() || '0');

/* ─────────────────────────────────────────── 항 모으기 */

const terms = [];                        // {iv, tPower, amp, arg:[a0..a4]}

function push(iv, tPower, amp, arg) {
    terms.push({ iv, tPower, amp, arg });
}

for (let ific = 1; ific <= 36; ific++) {
    const iv = ((ific - 1) % 3) + 1;
    const lines = (await file(ific)).split(/\r?\n/).slice(1);   // 첫 줄은 제목이다

    for (const line of lines) {
        if (line.trim().length === 0) continue;

        if (ific <= 3) {
            /* 주문제. format (4i3,2x,f13.5,6(2x,f10.2)) */
            const ilu = [int3(line, 0), int3(line, 3), int3(line, 6), int3(line, 9)];
            const coef = [num(line, 14, 13)];
            for (let i = 0; i < 6; i++) coef.push(num(line, 27 + i * 12 + 2, 10));

            /* 거리 급수만 진폭 자체가 평균운동 보정을 탄다 */
            if (ific === 3) coef[0] = coef[0] - 2 * coef[0] * delnu / 3;

            const tgv = coef[1] + dtasm * coef[5];
            const amp = coef[0] + tgv * (delnp - am * delnu)
                + coef[2] * delg + coef[3] * dele + coef[4] * delep;

            const arg = [0, 0, 0, 0, 0];
            for (let k = 0; k < 5; k++) {
                for (let i = 0; i < 4; i++) arg[k] += ilu[i] * del[i][k];
            }
            if (iv === 3) arg[0] += Math.PI / 2;
            push(iv, 0, amp, arg);

        } else if (ific <= 9 || ific >= 22) {
            /* 지구꼴 · 조석 · 상대론 · 태양 이심률. format (5i3,1x,f9.5,1x,f9.5,1x,f9.3) */
            const iz = int3(line, 0);
            const ilu = [int3(line, 3), int3(line, 6), int3(line, 9), int3(line, 12)];
            const pha = num(line, 16, 9);
            const amp = num(line, 26, 9);

            let tPower = 0;
            if (ific >= 7 && ific <= 9) tPower = 1;
            if (ific >= 25 && ific <= 27) tPower = 1;
            if (ific >= 34 && ific <= 36) tPower = 2;

            const arg = [pha * DEG, 0, 0, 0, 0];
            for (let k = 0; k < 2; k++) {
                arg[k] += iz * zeta[k];
                for (let i = 0; i < 4; i++) arg[k] += ilu[i] * del[i][k];
            }
            push(iv, tPower, amp, arg);

        } else {
            /* 행성 섭동. format (11i3,1x,f9.5,1x,f9.5,1x,f9.3) */
            const ipla = [];
            for (let i = 0; i < 11; i++) ipla.push(int3(line, i * 3));
            const pha = num(line, 34, 9);
            const amp = num(line, 44, 9);

            const tPower = (ific >= 13 && ific <= 15) || (ific >= 19 && ific <= 21) ? 1 : 0;

            const arg = [pha * DEG, 0, 0, 0, 0];
            if (ific <= 15) {
                for (let k = 0; k < 2; k++) {
                    arg[k] += ipla[8] * del[0][k] + ipla[9] * del[2][k] + ipla[10] * del[3][k];
                    for (let i = 0; i < 8; i++) arg[k] += ipla[i] * p[i][k];
                }
            } else {
                for (let k = 0; k < 2; k++) {
                    for (let i = 0; i < 4; i++) arg[k] += ipla[i + 7] * del[i][k];
                    for (let i = 0; i < 7; i++) arg[k] += ipla[i] * p[i][k];
                }
            }
            push(iv, tPower, amp, arg);
        }
    }
}

/* ─────────────────────────────────────────── 자르기 */

const kept = [];
const report = [];

for (const iv of [1, 2, 3]) {
    const budget = BUDGET[iv];
    const mine = terms.filter((t) => t.iv === iv)
        .map((t) => ({ ...t, weight: Math.abs(t.amp) * Math.pow(MAX_T, t.tPower) }));
    mine.sort((a, b) => b.weight - a.weight);

    let dropped = 0;
    let cut = mine.length;
    for (let i = mine.length - 1; i >= 0; i--) {
        if (dropped + mine[i].weight > budget.max) break;
        dropped += mine[i].weight;
        cut = i;
    }
    kept.push(...mine.slice(0, cut));
    report.push({ name: budget.name, total: mine.length, kept: cut, dropped, unit: budget.unit });
}

kept.sort((a, b) => a.iv - b.iv || a.tPower - b.tPower || Math.abs(b.amp) - Math.abs(a.amp));

const NAME = { 1: 'V', 2: 'U', 3: 'R' };
const lines = [
    '# ELP2000-82B 달 급수 — 잘라 낸 것. 손으로 고치지 않는다.',
    '# tools/gen-elp2000.mjs 가 다시 만든다.',
    `# 원천: ${BASE}/ELP1..ELP36 · 참조 구현 elp82b.f`,
    `# 받은 날: ${new Date().toISOString().slice(0, 10)}`,
    '#',
    '# 세 무리의 인자 구성을 t 의 다항식 하나로 폈다 — 자바는 갈래를 모른다.',
    '# 항: amp · T^p · sin(a0 + a1·T + a2·T² + a3·T³ + a4·T⁴), T 는 J2000 기준 율리우스 세기(TT).',
    '# V·U 는 초각, R 은 km. 상수 보정(DE200/LE200 적합)은 여기서 이미 먹였다.',
    '# 줄 모양: <변수 V|U|R> <t차수> <진폭> <a0> <a1> <a2> <a3> <a4>',
    '#',
    '# 머리에 상수 줄이 넷 있다. 이것도 elp82b.f 의 값이라 자바가 따로 들고 있지 않는다.',
    '#   W1    달의 평균황경 다항식(라디안). 황경 급수의 합에 더한다',
    '#   SCALE 거리 급수에 곱하는 눈금 (a0/ath)',
    '#   P · Q 황도면이 J2000 으로 돌아가는 회전. elp82b.f 의 p1..p5 · q1..q5',
    '#',
    ...report.map((r) => `# ${r.name}: ${r.total} 항 중 ${r.kept} 항을 남겼다 `
        + `— 버린 진폭 합 ${r.dropped.toExponential(3)} ${r.unit}`),
    `W1 ${w[0].join(' ')}`,
    `SCALE ${a0 / ath}`,
    `P ${[0.10180391e-4, 0.47020439e-6, -0.5417367e-9, -0.2507948e-11, 0.463486e-14].join(' ')}`,
    `Q ${[-0.113469002e-3, 0.12372674e-6, 0.1265417e-8, -0.1371808e-11, -0.320334e-14].join(' ')}`,
];

const g = (x) => (Object.is(x, -0) ? 0 : x);
for (const t of kept) {
    lines.push([NAME[t.iv], t.tPower, g(t.amp), ...t.arg.map(g)].join(' '));
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, lines.join('\n') + '\n');

for (const r of report) {
    console.log(`${r.name}: ${r.total} → ${r.kept} 항 · 버린 합 ${r.dropped.toExponential(3)} ${r.unit}`);
}
console.log(`총 ${kept.length} 항 → ${OUT}`);
