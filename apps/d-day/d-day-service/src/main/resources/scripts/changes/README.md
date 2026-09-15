# scripts/changes — 자료가 찬 DB 를 고치는 스크립트

**근거: `docs/SCHEMA.md` §14.** 여기는 그 요약이다. 결정의 까닭은 문서에 있다.

마이그레이션 도구를 쓰지 않는다 (§11.2). 그래서 `schema.sql` 은 **빈 DB 를 세우는
스크립트**이고, 이미 자료가 찬 DB 는 이 디렉터리의 스크립트로 고친다.

## 이름

```
0002-add-holiday-name-ko.sql       ← 리비전 1 → 2
```

번호는 이름이 아니라 **리비전**이다. `schema.sql` 이 리비전 1 을 세우므로 첫 변경이 `0002` 다.

## 모양 — 가드로 시작해 이력으로 끝난다

```sql
-- 0002-add-holiday-name-ko.sql  ·  리비전 1 → 2
DO $$
BEGIN
    IF (SELECT coalesce(max(revision), 0) FROM schema_change) <> 1 THEN
        RAISE EXCEPTION '리비전 1 에서만 적용할 수 있다. 지금 = %',
            (SELECT coalesce(max(revision), 0) FROM schema_change);
    END IF;
END $$;

ALTER TABLE holiday ADD COLUMN name_ko varchar(200);

INSERT INTO schema_change (revision, name) VALUES (2, '0002-add-holiday-name-ko');
```

가드가 없으면 `0003` 을 `0002` 보다 먼저 돌려도 **대개 그냥 돌아간다. 틀린 채로.**

## 적용

```bash
psql "$SPRING_DATASOURCE_URL" -v ON_ERROR_STOP=1 -1 -f 0002-add-holiday-name-ko.sql
```

`-1` 이 있어야 가드 · 변경 · 이력이 한 트랜잭션이다. 중간에 터졌을 때 이력만 남으면
다음 사람이 「적용됐다」고 믿는다.

**예외는 `CREATE INDEX CONCURRENTLY` 하나다** (§14 R4). 그 문장은 트랜잭션 블록 안에서
못 도니 `-1` 없이 돌리고, 파일 머리에 그렇게 적는다.

## 규칙 다섯 (§14.4)

| | |
| --- | --- |
| **R1** | **한 변경은 두 파일을 같이 고친다** — 이 스크립트와 `scripts/schema.sql`(그리고 `SCHEMA.md` §12). 하나만 고치면 새로 세운 DB 와 고친 DB 가 갈라지는데 **둘 다 뜬다** |
| R2 | 가드로 시작하고 이력 `INSERT` 로 끝난다 |
| R3 | **되돌리기(down) 스크립트를 쓰지 않는다.** 잘못 갔으면 앞으로 가는 변경을 하나 더 쓴다 |
| R4 | 자료가 찬 표의 인덱스는 `CREATE INDEX CONCURRENTLY` |
| R5 | 자료 이행(DML)이 들었으면 먼저 `pg_dump` |

`SchemaScriptTest` 가 R1 · R2 와 번호가 이어지는지를 문다. **Docker 없이 돈다.**

## 첫 변경을 쓰는 사람에게 (§14.7)

지금 이 디렉터리는 비어 있고, 비어 있는 동안은 R1 이 깨질 수가 없다. 첫 변경은 넷을 함께 한다.

1. 지금의 `schema.sql` 을 `scripts/baseline/r1.sql` 로 얼린다
2. `changes/0002-*.sql` 을 쓴다
3. `schema.sql` 과 `SCHEMA.md` §12 를 고쳐 **최종 모양**으로 만든다
4. **동등성 테스트**를 세운다 — `schema.sql` 로 세운 DB 와 `(r1.sql + changes/*)` 로 세운 DB 의
   `pg_catalog` 가 같아야 한다

4번을 미리 안 만들어 둔 까닭은 기준 파일이 없어서다. 오늘 만들면 `r1.sql` 이 `schema.sql` 의
복사본이 되고, **막으려던 고장(두 파일이 갈라지는 것)을 먼저 만드는 셈**이 된다.
