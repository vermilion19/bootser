/* ============================================================
   2층 검산점을 원천에서 받아 적는다 — SPEC.md §B 검산점
   ------------------------------------------------------------
   서비스 빌드에 들어가지 않는다. 해가 굴러 픽스처를 채워야 할 때 손으로 돌린다.

       node apps/d-day/tools/fetch-astro-checkpoints.mjs

   왜 스크립트인가. 값을 손으로 옮겨 적으면 옮기다 틀린 것과 계산이 틀린 것을
   구별할 수 없다. 여기서 받아 적은 것은 「원천이 그렇게 말했다」 뿐이고,
   그것과 우리 계산을 견주는 일은 테스트가 한다.

   ⚠ 이 스크립트는 dday-static 을 보지 않는다 (SPEC §3). NAOJ · IMO 는 그쪽도
   보는 원천이지만, 그것은 두 서비스가 같은 공표값을 각자 읽는 것이지
   한쪽이 다른 쪽에서 가져오는 것이 아니다.
   ============================================================ */

import { writeFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = dirname(fileURLToPath(import.meta.url));
const OUT = join(HERE, '..', 'd-day-service', 'src', 'test', 'resources', 'astro', 'checkpoints.json');

/* NAOJ 暦要項 이 다루는 해. 2월에 이듬해 것을 낸다 — 2028 은 아직 없다. */
const YEARS = [2025, 2026, 2027];

/* 절기 스물넷. 이름은 NAOJ 영문판의 로마자, 황경은 정의값이다.
   황경을 여기 적어 두는 것은 대조용이다 — 페이지가 주는 값과 다르면 멈춘다. */
const TERMS = [
    ['Shoukan', 285, '소한'], ['Daikan', 300, '대한'], ['Risshun', 315, '입춘'], ['Usui', 330, '우수'],
    ['Keichitsu', 345, '경칩'], ['Shunbun', 0, '춘분'], ['Seimei', 15, '청명'], ['Kokuu', 30, '곡우'],
    ['Rikka', 45, '입하'], ['Shouman', 60, '소만'], ['Boushu', 75, '망종'], ['Geshi', 90, '하지'],
    ['Shousho', 105, '소서'], ['Taisho', 120, '대서'], ['Risshuu', 135, '입추'], ['Shosho', 150, '처서'],
    ['Hakuro', 165, '백로'], ['Shuubun', 180, '추분'], ['Kanro', 195, '한로'], ['Soukou', 210, '상강'],
    ['Rittou', 225, '입동'], ['Shousetsu', 240, '소설'], ['Taisetsu', 255, '대설'], ['Touji', 270, '동지'],
];
const BY_NAME = new Map(TERMS.map(([n, longitude, ko]) => [n, { longitude, ko }]));
const MONTHS = ['January', 'February', 'March', 'April', 'May', 'June',
                'July', 'August', 'September', 'October', 'November', 'December'];

/* 유성우 — IMO Meteor Shower Calendar 2026 의 본문에서 받아 적었다.
   본문 서술이라 표가 아니다. 그래서 여기만 손으로 옮겼고, 옮긴 값 옆에
   원문을 그대로 남긴다.

   ⚠ solarLongitude 는 **equinox 2000.0** 기준이다. 절기의 황경(그 시점의
   겉보기값)과 같은 눈금이 아니다 — 자세한 것은 docs/ASTRO-CHECKPOINTS.md.

   precision 은 원천이 준 만큼만 적은 것이다. IMO 스스로 "많은 경우 극대는
   황경 1도보다 정밀하게 알려져 있지 않다" 고 적어 두었으므로, 분까지 적힌
   것만 분으로 견준다. */
const METEORS_2026 = [
    { code: '010QUA', name: 'Quadrantids', ko: '사분의자리', utc: '2026-01-03T21:00Z', precision: 'hour', solarLongitude: null,
      source: 'peak on January 3 close to 21h UT' },
    { code: '404GUM', name: 'gamma-Ursae Minorids', ko: '작은곰자리 감마', utc: '2026-01-18', precision: 'date', solarLongitude: 298,
      source: 'Maximum: around January 18 (lambda = 298)' },
    { code: '102ACE', name: 'alpha-Centaurids', ko: '센타우루스자리 알파', utc: '2026-02-08', precision: 'date', solarLongitude: 319.4,
      source: 'Maximum: February 8 (lambda = 319.4)' },
    { code: '006LYR', name: 'April Lyrids', ko: '거문고자리', utc: '2026-04-22T19:40Z', precision: 'minute', solarLongitude: 32.32,
      source: 'Maximum: April 22, 19h40m UT (lambda = 32.32, but may vary)' },
    { code: '145ELY', name: 'eta-Lyrids', ko: '거문고자리 에타', utc: '2026-05-10', precision: 'date', solarLongitude: 50,
      source: 'Maximum: May 10 (lambda = 50)' },
    { code: '007PER', name: 'Perseids', ko: '페르세우스자리', utc: '2026-08-13T02:00Z', utcEnd: '2026-08-13T04:00Z', precision: 'range', solarLongitude: 140.0,
      source: 'Maximum: August 13, 02h to 04h UT (node at lambda = 140.0-140.1)' },
    { code: '281OCT', name: 'October Camelopardalids', ko: '기린자리', utc: '2026-10-06T04:40Z', precision: 'minute', solarLongitude: 192.58,
      source: 'Maximum: October 6, 04h40m (lambda = 192.58)' },
    { code: '008ORI', name: 'Orionids', ko: '오리온자리', utc: '2026-10-21', precision: 'date', solarLongitude: 208,
      source: 'Maximum: October 21 (lambda = 208)' },
    { code: '013LEO', name: 'Leonids', ko: '사자자리', utc: '2026-11-17T23:45Z', precision: 'minute', solarLongitude: 235.27,
      source: 'Maximum: November 17, 23h45m UT (nodal crossing at lambda = 235.27)' },
    { code: '004GEM', name: 'Geminids', ko: '쌍둥이자리', utc: '2026-12-14T14:00Z', precision: 'hour', solarLongitude: 262.2,
      source: 'Maximum: December 14, 14h UT (lambda = 262.2)' },
    { code: '015URS', name: 'Ursids', ko: '작은곰자리', utc: '2026-12-22T22:00Z', precision: 'hour', solarLongitude: null,
      source: 'reach their maximum on December 22, 22h UT' },
];

/* ------------------------------------------------------------------ 받아오기 */

const flatten = (html) => html
    .replace(/<[^>]+>/g, '|')
    .replace(/&nbsp;/g, ' ')
    .replace(/&deg;/g, '°')
    .replace(/\|+/g, '|')
    .replace(/[ \t]+/g, ' ');

async function page(year, section) {
    /* 영문판을 받는다. 일본어판은 Shift_JIS 라 그냥 읽으면 깨진다. */
    const yy = String(year).slice(2);
    const url = `https://eco.mtk.nao.ac.jp/koyomi/yoko/${year}/rekiyou${yy}${section}.html.en`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`${url} → HTTP ${res.status}`);
    return { url, text: flatten(await res.text()) };
}

/* 中央標準時(JST, UTC+9) 을 UTC 로. 원천이 분까지만 주므로 분까지만 남긴다. */
function toUtc(year, monthName, day, hour, minute) {
    const m = MONTHS.indexOf(monthName);
    if (m < 0) throw new Error(`달 이름을 못 읽었다: ${monthName}`);
    return new Date(Date.UTC(year, m, day, hour - 9, minute))
        .toISOString().replace(':00.000Z', 'Z');
}

async function solarTerms(year) {
    const { url, text } = await page(year, 2);
    /* 이름 · 황경 · 날짜 · 시각. 뒤에 붙는 잡절(土用 · 節分 등)은 이름으로 걸러진다 */
    const re = /\|\s*([A-Za-z0-9]+)\s*\|(\d+)°\|[^|]*\|\s*([A-Z][a-z]+) (\d+)(?:st|nd|rd|th)\|\s*(\d+)\|h\|\s*(\d+)\|m\|/g;
    const got = [];
    let m;
    while ((m = re.exec(text))) {
        const [, name, longitude, month, day, hh, mm] = m;
        const want = BY_NAME.get(name);
        if (!want) continue;
        if (want.longitude !== Number(longitude)) {
            throw new Error(`${year} ${name}: 페이지의 황경이 ${longitude}° 다 (${want.longitude}° 이어야 한다)`);
        }
        got.push({ year, name, ko: want.ko, longitude: want.longitude, utc: toUtc(year, month, +day, +hh, +mm) });
    }
    if (got.length !== 24) throw new Error(`${year} 절기가 ${got.length}개다 — 24개여야 한다 (${url})`);
    return got;
}

async function moonPhases(year) {
    const { url, text } = await page(year, 3);
    /* 삭 · 망만 담는다 (B-2). 상현 · 하현은 쓰지 않는다 */
    const re = /\|\s*(New Moon|Full Moon)\|\s*([A-Z][a-z]+) (\d+)(?:st|nd|rd|th)\|\s*(\d+)\|h\|\s*(\d+)\|m\|/g;
    const got = [];
    let m;
    while ((m = re.exec(text))) {
        const [, phase, month, day, hh, mm] = m;
        got.push({ year, phase: phase === 'New Moon' ? 'NEW' : 'FULL', utc: toUtc(year, month, +day, +hh, +mm) });
    }
    /* 한 해에 삭 · 망은 각각 12~13 번이다. 스물넷에 한참 못 미치면 파싱이 깨진 것이다 */
    if (got.length < 24) throw new Error(`${year} 삭망이 ${got.length}건이다 — 24건 이상이어야 한다 (${url})`);
    return got;
}

const flat = (xs) => xs.flat();
const [terms, moons] = await Promise.all([
    Promise.all(YEARS.map(solarTerms)).then(flat),
    Promise.all(YEARS.map(moonPhases)).then(flat),
]);

const doc = {
    note: '2층 검산점 — SPEC.md §B 검산점. 손으로 고치지 않는다. tools/fetch-astro-checkpoints.mjs 가 다시 만든다.',
    acquired: new Date().toISOString().slice(0, 10),
    sources: {
        solarTerms: 'NAOJ 暦計算室 暦要項 (https://eco.mtk.nao.ac.jp/koyomi/yoko/) — 中央標準時, 분 단위',
        moonPhases: 'NAOJ 暦計算室 暦要項 — 中央標準時, 분 단위',
        meteorShowers: 'IMO Meteor Shower Calendar 2026 (https://www.imo.net/files/meteor-shower/cal2026.pdf) — UT',
    },
    tolerance: {
        solarTermMinutes: 1,
        moonPhaseMinutes: 1,
        note: '원천이 분 단위로 반올림돼 있으므로 계산이 완벽해도 최대 1분이 벌어진다. 유성우는 갈래마다 precision 이 다르다.',
    },
    solarTerms: terms,
    moonPhases: moons,
    meteorShowers: METEORS_2026,
};

mkdirSync(dirname(OUT), { recursive: true });
writeFileSync(OUT, JSON.stringify(doc, null, 2) + '\n');
console.log(`절기 ${terms.length} · 삭망 ${moons.length} · 유성우 ${METEORS_2026.length} → ${OUT}`);
