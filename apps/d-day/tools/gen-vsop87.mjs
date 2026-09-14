/* ============================================================
   VSOP87D 지구 급수를 원천에서 받아 잘라 낸다
   ------------------------------------------------------------
   서비스 빌드에 들어가지 않는다. 급수를 다시 자를 때만 손으로 돌린다.

       node apps/d-day/tools/gen-vsop87.mjs

   ⚠ 계수를 손으로 옮겨 적지 않는다. 절기 시각이 1분 안에 맞아야 하는데,
   계수 한 자리를 잘못 적으면 「계산이 틀린 것」과 「옮기다 틀린 것」을 구별할 수
   없다. 검산점을 원천에서 받아 적은 것과 같은 까닭이다
   (docs/ASTRO-CHECKPOINTS.md §1).

   왜 D 인가. VSOP87 에는 A~E 다섯 판이 있고 **D 만이 「그 시점의 황도와 분점」**
   기준이다. 절기의 정의가 그 시점의 겉보기 황경이므로 D 를 쓰면 세차 변환이
   필요 없다. A(J2000 고정)를 쓰면 매번 세차를 먹여야 하고, 그 자리가 바로
   ASTRO-CHECKPOINTS §4 가 경고한 기준계 섞임이 들어올 틈이다.

   자르는 기준은 「버린 항의 진폭 합」이다. 항 수가 아니다 — 작은 항이 수백 개면
   합은 작지 않다. 아래 BUDGET 이 그 합의 상한이고, 시간으로 환산한 값을 함께 적어
   두었다.
   ============================================================ */

import { writeFileSync, mkdirSync, readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', 'astro-core', 'src', 'main', 'resources', 'astro', 'vsop87-earth.txt');

/* CDS(스트라스부르 천문자료센터)의 VSOP87 카탈로그 VI/81. 저자 원본이다. */
const SOURCE = 'https://cdsarc.cds.unistra.fr/ftp/VI/81/VSOP87D.ear';

/* 버린 항의 진폭 합 상한.

   L 은 라디안이다. 1e-7 rad = 0.0206" 이고, 태양이 하루 0.9856° 를 가므로
   시간으로는 0.5초쯤이다 — 허용오차 1분에 견주면 없는 것이나 같다.

   B(위도)는 겉보기 황경에 FK5 보정(약 0.09")으로만 스며들고, R 은 광행차
   (-20.4898"/R)와 광행시간에 쓰인다. 둘 다 L 만큼 조일 까닭이 없다. */
const BUDGET = {
    1: { name: 'L', unit: 'rad', max: 1e-7 },
    2: { name: 'B', unit: 'rad', max: 1e-6 },
    3: { name: 'R', unit: 'AU', max: 1e-6 },
};

/* 우리가 답하기로 한 연도 범위에서 |T| (J2000 기준 율리우스 천년)의 최대값.
   1583년이 -0.417, 2999년이 +0.999 다. T^k 의 무게가 1을 넘지 않으므로
   차수가 높은 항을 따로 깎지 않아도 된다 — 상한을 1로 두고 보수적으로 잰다. */
const MAX_T = 1.0;

async function fetchSource() {
    /* 한 번 받은 것을 옆에 두고 다시 쓴다. 원천을 두 번 때릴 까닭이 없다 */
    const cache = join(HERE, '..', '..', '..', '.gradle', 'vsop87d-ear.cache');
    if (existsSync(cache)) {
        return readFileSync(cache, 'utf8');
    }
    const res = await fetch(SOURCE);
    if (!res.ok) throw new Error(`${SOURCE} → HTTP ${res.status}`);
    const text = await res.text();
    try {
        mkdirSync(dirname(cache), { recursive: true });
        writeFileSync(cache, text);
    } catch { /* 캐시는 있으면 좋은 것일 뿐이다 */ }
    return text;
}

const raw = await fetchSource();

/* 헤더 한 줄이 갈래(변수·차수)를 정하고, 그 아래가 그 갈래의 항이다.
   항 줄의 **마지막 세 수**가 A · B · C 이고 항은 A cos(B + C·τ) 다. */
const HEADER = /VSOP87 VERSION \w+\s+(\w+)\s+VARIABLE (\d) \(LBR\)\s+\*T\*\*(\d)/;

const groups = new Map();               // "1:0" → [{a,b,c}]
let current = null;

for (const line of raw.split(/\r?\n/)) {
    const head = HEADER.exec(line);
    if (head) {
        const [, body, variable, power] = head;
        if (body !== 'EARTH') throw new Error(`지구가 아닌 자료가 섞였다: ${body}`);
        current = `${variable}:${power}`;
        if (!groups.has(current)) groups.set(current, []);
        continue;
    }
    if (!line.trim() || current === null) continue;

    const nums = line.trim().split(/\s+/);
    const c = Number(nums[nums.length - 1]);
    const b = Number(nums[nums.length - 2]);
    const a = Number(nums[nums.length - 3]);
    if (![a, b, c].every(Number.isFinite)) throw new Error(`항을 못 읽었다: ${line}`);
    groups.get(current).push({ a, b, c });
}

if (groups.size === 0) throw new Error('갈래를 하나도 못 찾았다 — 원천의 헤더 모양이 바뀌었다');

/* 자르기. 갈래마다가 아니라 **변수마다** 예산을 쓴다 — L0 를 조이고 L3 를 푸는
   식이 아니라, L 전체가 넘지 않아야 하는 것이 라디안 합이기 때문이다. */
const kept = [];
const report = [];

for (const variable of [1, 2, 3]) {
    const budget = BUDGET[variable];
    const all = [];
    for (const [key, terms] of groups) {
        const [v, power] = key.split(':').map(Number);
        if (v !== variable) continue;
        for (const t of terms) {
            all.push({ power, ...t, weight: Math.abs(t.a) * Math.pow(MAX_T, power) });
        }
    }
    all.sort((x, y) => y.weight - x.weight);

    let dropped = 0;
    let cut = all.length;
    /* 뒤에서부터 버린다. 버린 합이 예산을 넘는 순간 멈춘다 */
    for (let i = all.length - 1; i >= 0; i--) {
        if (dropped + all[i].weight > budget.max) break;
        dropped += all[i].weight;
        cut = i;
    }

    const take = all.slice(0, cut);
    kept.push(...take.map((t) => ({ variable, ...t })));
    report.push({
        name: budget.name, total: all.length, kept: take.length,
        dropped: dropped, unit: budget.unit,
    });
}

/* 차수별로 묶어서 내보낸다 — 읽는 쪽이 차수마다 호너법으로 더하기 때문이다 */
kept.sort((x, y) => x.variable - y.variable || x.power - y.power || Math.abs(y.a) - Math.abs(x.a));

const lines = [
    '# VSOP87D EARTH — 잘라 낸 급수. 손으로 고치지 않는다.',
    '# tools/gen-vsop87.mjs 가 다시 만든다.',
    `# 원천: ${SOURCE}`,
    `# 받은 날: ${new Date().toISOString().slice(0, 10)}`,
    '#',
    '# 기준계: 그 시점의 황도와 분점 (VSOP87 D 판). L·B 는 라디안, R 은 AU.',
    '# 항: A cos(B + C·tau), tau 는 J2000 기준 율리우스 천년(TT).',
    '# 줄 모양: <변수 L|B|R> <차수> <A> <B> <C>',
    '#',
    ...report.map((r) => `# ${r.name}: ${r.total} 항 중 ${r.kept} 항을 남겼다 `
        + `— 버린 진폭 합 ${r.dropped.toExponential(3)} ${r.unit}`),
];

const NAME = { 1: 'L', 2: 'B', 3: 'R' };
for (const t of kept) {
    lines.push(`${NAME[t.variable]} ${t.power} ${t.a} ${t.b} ${t.c}`);
}

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, lines.join('\n') + '\n');

for (const r of report) {
    console.log(`${r.name}: ${r.total} → ${r.kept} 항 · 버린 합 ${r.dropped.toExponential(3)} ${r.unit}`);
}
console.log(`총 ${kept.length} 항 → ${OUT}`);
