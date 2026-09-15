/* ============================================================
   1층 검산점을 원천에서 받아 적는다 — SPEC.md §B 검산점
   ------------------------------------------------------------
       node apps/d-day/tools/fetch-holiday-checkpoints.mjs

   1층은 **날짜**를 검산한다. 2층(NAOJ 시각)이 못 잡는 것이 아니라 그 반대다 —
   2층이 훨씬 촘촘하다. 1층의 값어치는 **다른 곳에서 온다는 것**에 있다.
   NAOJ 도 우리도 같은 천문학을 쓰지만, 일본 법령이 정한 春分の日 과 한국 달력의
   설날은 그것과 독립된 자리에서 정해진 날짜다.

   ------------------------------------------------------------
   ⚠ SPEC §B 검산점은 "코퍼스가 생기면 공짜로 따라오는 검산점" 이라고 적었다.
   **일본은 그렇고 한국은 아니다.** 받아 보고 알았다.

   한국 자료는 **일요일에 걸린 공휴일을 빼고 준다.** 설날이 일요일이면 그 날이
   목록에 없고 대신 대체공휴일이 뒤에 붙는다.

       2023 설날  01-21 · 01-23 · 01-24     ← 실제 설날 01-22(일)이 없다
       2025 추석  10-06 · 10-07 · 10-08     ← 추석 전날 10-05(일)이 없다

   그래서 «가운데 날이 음력 1월 1일» 도 «첫날 다음이 설날» 도 성립하지 않는다.
   위치로는 못 집는다. 검산은 **세 날의 모양**으로 한다 (Tier1CheckpointTest).

   그러므로 이 스크립트는 **날짜 셋을 그대로 적는다.** 여기서 하나를 골라
   «이것이 설날이다» 라고 적으면, 고르는 규칙이 틀렸을 때 그것이 우리 계산이
   틀린 것으로 보이게 된다.
   ============================================================ */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', 'astro-core', 'src', 'test', 'resources',
    'astro', 'holiday-checkpoints.txt');

const SOURCE = 'https://date.nager.at/api/v3/PublicHolidays';

/* 넉넉히 받아 둔다. 고정 픽스처와 달리 이것은 해마다 다시 받을 수 있으므로
   범위를 넓혀도 늙지 않는다 — 다시 돌리면 그만이다. */
const YEARS = Array.from({ length: 11 }, (_, i) => 2020 + i);

/**
 * 뽑을 것.
 *
 * 일본은 법이 «춘분일/추분일» 을 공휴일로 정해 두었다. 그 날짜는 일본 표준시
 * 기준의 분점 날짜이고, 우리가 계산한 분점 시각을 JST 로 옮긴 날짜와 같아야 한다.
 *
 * 한국은 설날·추석이 각각 세 날로 온다. 어느 것이 음력 1/1 인지는 위의 까닭으로
 * 여기서 정하지 않는다.
 */
const WANTED = [
    { country: 'JP', localName: '春分の日', key: 'VERNAL', shape: 'one' },
    { country: 'JP', localName: '秋分の日', key: 'AUTUMNAL', shape: 'one' },
    { country: 'KR', localName: '설날', key: 'SEOLLAL', shape: 'group' },
    { country: 'KR', localName: '추석', key: 'CHUSEOK', shape: 'group' },
];

async function fetchJson(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`${url} → HTTP ${response.status}`);
    }
    return response.json();
}

function stop(message) {
    console.error(`\n✗ ${message}\n`);
    process.exit(1);
}

async function main() {
    console.log(`원천에서 받는다 — ${YEARS.at(0)}~${YEARS.at(-1)}`);

    const byCountry = new Map();
    for (const country of ['JP', 'KR']) {
        const years = new Map();
        for (const year of YEARS) {
            years.set(year, await fetchJson(`${SOURCE}/${year}/${country}`));
        }
        byCountry.set(country, years);
        console.log(`  ${country} ${YEARS.length}해`);
    }

    const lines = [];
    const notes = [];

    for (const want of WANTED) {
        for (const year of YEARS) {
            const rows = byCountry.get(want.country).get(year);
            const dates = rows
                .filter((row) => row.localName === want.localName)
                .map((row) => row.date)
                .sort();

            if (dates.length === 0) {
                stop(`${year} ${want.country} 에 ${want.localName} 이 없다 — `
                    + `원천이 이름을 바꿨거나 그 해에 없는 공휴일이다`);
            }
            if (want.shape === 'one' && dates.length !== 1) {
                stop(`${year} ${want.localName} 이 ${dates.length}건이다. 하나여야 한다`);
            }
            if (want.shape === 'group' && dates.length !== 3) {
                stop(`${year} ${want.localName} 이 ${dates.length}건이다. 셋이어야 한다 — `
                    + `모양이 바뀌었으면 Tier1CheckpointTest 의 규칙부터 다시 본다`);
            }

            /* 연속하지 않는 것이 정상이라는 사실을 눈에 보이게 적어 둔다 */
            if (want.shape === 'group' && !isConsecutive(dates)) {
                notes.push(`${year} ${want.localName}: ${dates.join(' ')} (일요일이 빠졌다)`);
            }
            lines.push(`${year}|${want.key}|${dates.join(',')}`);
        }
    }

    const today = new Date().toISOString().slice(0, 10);
    const header = [
        '# 1층 검산점 — 원천에서 받아 적은 것. 손으로 고치지 않는다.',
        '# tools/fetch-holiday-checkpoints.mjs 가 낸다.',
        '#',
        `# 받은 날: ${today}`,
        `# 원천: Nager.Date v3 PublicHolidays (${YEARS.at(0)}~${YEARS.at(-1)})`,
        '#',
        '# VERNAL · AUTUMNAL  일본 春分の日 · 秋分の日 — 한 날. 일본 표준시 기준이다',
        '# SEOLLAL · CHUSEOK  한국 설날 · 추석 — 세 날. **어느 것이 음력 1/1 인지는 적지 않는다**',
        '#',
        '# 한국 자료는 일요일에 걸린 공휴일을 빼고 준다. 그래서 세 날이 연속하지 않는',
        '# 해가 있고, 위치로는 음력 1/1 을 집을 수 없다. 검산 규칙은',
        '# Tier1CheckpointTest 에 있다.',
        '#',
        '# 줄 모양: 연도|무엇|날짜(쉼표로 이음)',
    ].join('\n');

    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, `${header}\n${lines.join('\n')}\n`, 'utf8');

    console.log(`\n✓ ${lines.length}줄 → ${OUT}`);
    if (notes.length > 0) {
        console.log(`\n  연속하지 않는 해 ${notes.length}건 — 정상이다:`);
        for (const note of notes) console.log(`    ${note}`);
    }
}

function isConsecutive(dates) {
    for (let i = 1; i < dates.length; i++) {
        const previous = new Date(`${dates[i - 1]}T00:00:00Z`);
        const current = new Date(`${dates[i]}T00:00:00Z`);
        if (current - previous !== 86_400_000) {
            return false;
        }
    }
    return true;
}

main().catch((error) => stop(error.message));
