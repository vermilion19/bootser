/* ============================================================
   country 시드를 원천에서 받아 적는다 — SPEC.md §9.2 · ARCHITECTURE.md 착수 3
   ------------------------------------------------------------
   서비스 빌드에 들어가지 않는다. 원천이 바뀌었을 때 손으로 돌린다.

       node apps/d-day/tools/gen-country-seed.mjs

   왜 스크립트인가. 204줄을 손으로 적으면 옮기다 틀린 것과 원천이 그런 것을
   구별할 수 없다 — VSOP87 계수와 천문 검산점에 쓴 규칙 그대로다.

   ⚠ dday-static 을 보지 않는다 (SPEC §3). 아래 검산점 셋은 그쪽이 재 둔 수치와
   견주는 것이지, 그쪽 자료를 가져오는 것이 아니다.

   ------------------------------------------------------------
   원천 넷. 각자 하는 일이 다르다.

     1. Nager.Date AvailableCountries  — **누가 목록에 드는가** (204개국)
        공휴일 자료가 있는 나라만 담는다. ISO 3166 전체(249)가 아니다.
     2. CLDR weekData                  — 주말 요일 → weekend_mask (A-7)
     3. CLDR territories (en · ko)     — 이름 둘. Nager 는 영어만 준다 (§11.1)
     4. IANA tzdb zone1970.tab         — 대표 시간대 (E-1)

   ------------------------------------------------------------
   대표 시간대를 「첫 줄」로 고르는 근거는 우리가 지어낸 것이 아니라 tzdb 가
   제 파일 머리에 적어 둔 것이다.

     "The table is sorted first by country code, then (if possible) by an order
      within the country that (1) makes some geographical sense, and (2) puts
      the most populous timezones first, where that does not contradict (1)."

   그래도 **고른 것은 고른 것이다.** 시간대가 둘 이상인 나라는 zoneAmbiguous 를
   켜고, 그 사실이 응답까지 따라간다 (SPEC §9.9(4)) — 조용히 대표로 퉁치면
   E-1 이 고치려던 고장을 자리만 옮겨 다시 만드는 셈이다.
   ============================================================ */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', 'd-day-service', 'src', 'main', 'resources', 'seed', 'country.tsv');

const CLDR = 'https://raw.githubusercontent.com/unicode-org/cldr-json/main/cldr-json';
const SOURCES = {
    nager: 'https://date.nager.at/api/v3/AvailableCountries',
    weekData: `${CLDR}/cldr-core/supplemental/weekData.json`,
    namesEn: `${CLDR}/cldr-localenames-full/main/en/territories.json`,
    namesKo: `${CLDR}/cldr-localenames-full/main/ko/territories.json`,
    tzdb: 'https://raw.githubusercontent.com/eggert/tz/main/zone1970.tab',
};

/* SPEC §5 가 적어 둔 정적 사이트의 실측. 여기가 안 맞으면 멈춘다.
   "국가 목록과 시간대는 자주 바뀌지 않고, 바뀌면 그것 자체가 사람이 확인할
   사건이다" (SPEC §9.2) — 그 확인을 강제하는 것이 이 셋이다. */
const EXPECTED = {
    countries: 204,
    weekend: { 96: 195, 48: 8, 64: 1 },   // 토·일 · 금·토 · 일요일만
};

/** ISO-8601 요일 비트. bit0=월 … bit6=일 (SCHEMA §4.3) */
const DAY_BIT = { mon: 0, tue: 1, wed: 2, thu: 3, fri: 4, sat: 5, sun: 6 };

async function fetchText(url) {
    const response = await fetch(url);
    if (!response.ok) {
        throw new Error(`${url} → HTTP ${response.status}`);
    }
    return response.text();
}

const fetchJson = async (url) => JSON.parse(await fetchText(url));

/**
 * 주말 요일을 비트마스크로 접는다.
 *
 * weekendStart 와 weekendEnd 는 **따로 온다.** 한쪽만 재정의한 나라가 있어서
 * (인도는 start 만 sun 이고 end 가 없다) 각각 001(전 세계 기본)로 떨어뜨린다.
 * 시작에서 끝까지 요일을 돌며 켜므로 주말이 주를 넘어가도(토→일, 목→금) 맞는다.
 */
function weekendMask(code, weekData) {
    const start = DAY_BIT[weekData.weekendStart[code] ?? weekData.weekendStart['001']];
    const end = DAY_BIT[weekData.weekendEnd[code] ?? weekData.weekendEnd['001']];

    let mask = 0;
    for (let day = start; ; day = (day + 1) % 7) {
        mask |= 1 << day;
        if (day === end) break;
    }
    return mask;
}

/**
 * zone1970.tab → 국가별 시간대 목록. **파일의 줄 순서를 그대로 지킨다.**
 *
 * 한 줄이 여러 나라를 실을 수 있다(`CH,DE,LI`). 그때 첫 나라는 그 시간대의
 * 주인이고 나머지는 "이 시간대에 걸쳐 있다" 는 뜻이라, 나라마다 등장 순서대로 쌓는다.
 */
function zonesByCountry(tab) {
    const zones = new Map();

    for (const line of tab.split('\n')) {
        if (!line || line.startsWith('#')) continue;

        const [codes, , zone] = line.split('\t');
        if (!zone) continue;

        for (const code of codes.split(',')) {
            if (!zones.has(code)) zones.set(code, []);
            if (!zones.get(code).includes(zone)) zones.get(code).push(zone);
        }
    }
    return zones;
}

function stop(message) {
    console.error(`\n✗ ${message}\n`);
    process.exit(1);
}

async function main() {
    console.log('원천 넷을 받는다…');
    const [nager, weekRaw, enRaw, koRaw, tab] = await Promise.all([
        fetchJson(SOURCES.nager),
        fetchJson(SOURCES.weekData),
        fetchJson(SOURCES.namesEn),
        fetchJson(SOURCES.namesKo),
        fetchText(SOURCES.tzdb),
    ]);

    const weekData = weekRaw.supplemental.weekData;
    const namesEn = enRaw.main.en.localeDisplayNames.territories;
    const namesKo = koRaw.main.ko.localeDisplayNames.territories;
    const zones = zonesByCountry(tab);

    if (nager.length !== EXPECTED.countries) {
        stop(`Nager 가 ${nager.length}개국을 준다. SPEC §11.1 은 ${EXPECTED.countries} 다.\n`
            + `  원천이 나라를 더했거나 뺐다. SPEC §5 · §11.1 의 수치를 먼저 고칠 것.`);
    }

    const rows = [];
    const missing = [];
    const renamed = [];

    for (const { countryCode: code, name: nagerName } of nager.sort(
        (a, b) => a.countryCode.localeCompare(b.countryCode))) {

        const nameEn = namesEn[code];
        const nameKo = namesKo[code];
        const zoneList = zones.get(code) ?? [];

        if (!nameEn || !nameKo || zoneList.length === 0) {
            missing.push(`${code} (en=${!!nameEn} ko=${!!nameKo} zones=${zoneList.length})`);
            continue;
        }
        /* 이름은 CLDR 것을 쓴다 — 영어와 한국어가 한 원천에서 와야 둘이 같은 것을
           가리킨다. Nager 의 영어 이름과 다른 것은 세어서 알려만 준다. */
        if (nameEn !== nagerName) {
            renamed.push(`${code}: CLDR "${nameEn}" ≠ Nager "${nagerName}"`);
        }

        rows.push({
            code,
            nameEn,
            nameKo,
            zone: zoneList[0],
            ambiguous: zoneList.length > 1,
            mask: weekendMask(code, weekData),
        });
    }

    if (missing.length > 0) {
        stop(`원천이 비어 있는 나라 ${missing.length}개:\n  ${missing.join('\n  ')}`);
    }

    /* SPEC §5 의 검산점. 여기가 안 맞으면 A-7 의 544 도 안 맞는다 */
    const byMask = {};
    for (const row of rows) byMask[row.mask] = (byMask[row.mask] ?? 0) + 1;

    const actual = JSON.stringify(byMask);
    const expected = JSON.stringify(EXPECTED.weekend);
    if (actual !== expected) {
        stop(`주말 분포가 SPEC §5 와 다르다.\n  받은 것: ${actual}\n  기대한 것: ${expected}\n`
            + `  CLDR 이 어느 나라의 주말을 바꿨거나 Nager 의 목록이 바뀌었다.`);
    }

    const ambiguous = rows.filter((row) => row.ambiguous);
    const today = new Date().toISOString().slice(0, 10);

    const header = [
        '# country 시드 — 원천에서 받아 적은 것. 손으로 고치지 않는다.',
        '# tools/gen-country-seed.mjs 가 낸다. 고칠 것이 있으면 그 스크립트를 고치고 다시 돌린다.',
        '#',
        `# 받은 날: ${today}`,
        `# 원천: Nager.Date AvailableCountries (목록 ${rows.length})`,
        '#       CLDR weekData (주말) · CLDR territories en·ko (이름)',
        '#       IANA tzdb zone1970.tab (대표 시간대)',
        '#',
        '# 목록은 ISO 3166 전체(249)가 아니라 **공휴일 자료가 있는 나라**다 (SPEC §11.1).',
        '#',
        `# 이름 둘 다 CLDR 것이다. Nager 도 영어 이름을 주지만 ${renamed.length}개국에서 다르고`,
        '#   (Nager "Ivory Coast" ↔ CLDR "Côte d’Ivoire"), 영어와 한국어가 한 원천에서 와야',
        '#   둘이 같은 것을 가리킨다. Nager 의 이름은 목록에 드는지를 정할 때만 쓴다.',
        '#',
        `# 주말 (SCHEMA §4.3 · ISO-8601 bit0=월 … bit6=일): ${
            Object.entries(byMask).map(([m, n]) => `${m}→${n}개국`).join(' · ')}`,
        `# 시간대가 여럿이라 대표를 고른 나라: ${ambiguous.length}`,
        '#   고른 규칙은 zone1970.tab 의 줄 순서다 — 그 파일이 "가장 인구가 많은 시간대를',
        '#   앞에 둔다" 고 제 머리에 적어 두었다. 고른 사실은 zoneAmbiguous 로 나간다 (SPEC §9.9(4)).',
        '#',
        '# 줄 모양: code|nameEn|nameKo|primaryZoneId|zoneAmbiguous|weekendMask',
    ].join('\n');

    const body = rows
        .map((row) => [row.code, row.nameEn, row.nameKo, row.zone, row.ambiguous, row.mask].join('|'))
        .join('\n');

    mkdirSync(dirname(OUT), { recursive: true });
    writeFileSync(OUT, `${header}\n${body}\n`, 'utf8');

    console.log(`\n✓ ${rows.length}줄 → ${OUT}`);
    console.log(`  주말 분포 ${actual} — SPEC §5 와 같다`);
    console.log(`  대표를 고른 나라 ${ambiguous.length}개: ${
        ambiguous.slice(0, 8).map((row) => row.code).join(' ')}${ambiguous.length > 8 ? ' …' : ''}`);

    if (renamed.length > 0) {
        console.log(`\n  CLDR 과 Nager 의 영어 이름이 다른 나라 ${renamed.length}개 (CLDR 을 쓴다):`);
        for (const line of renamed.slice(0, 10)) console.log(`    ${line}`);
        if (renamed.length > 10) console.log(`    … 그 밖 ${renamed.length - 10}개`);
    }
}

main().catch((error) => stop(error.message));
