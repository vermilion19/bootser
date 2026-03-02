# JWT(JSON Web Token) 50페이지 가이드 (이론 + 실무 적용 + 주의점 + 사용팁)

- 작성일: 2026-03-02  
- 버전: v1.0  
- 언어: ko-KR  

> 이 문서는 JWT를 “정확히” 이해하고 실무에서 안전하게 적용하기 위한 긴 분량의 정리 노트입니다.  
> 보안은 맥락(위협 모델, 조직, 규제, 트래픽, 데이터 민감도)에 따라 달라집니다. 프로덕션 적용 전에는 보안 리뷰/침투 테스트를 권장합니다.
>
> **참고 표준/가이드:** RFC 7519(JWT), RFC 8725(Best Current Practices), OWASP JWT Cheat Sheet 등

---
## 목차 (50 pages)

- [01. JWT 한눈에 보기: 왜 쓰고, 무엇을 해결하나](#page-01--JWT-한눈에-보기-왜-쓰고,-무엇을-해결하나)
- [02. 세션 vs 토큰: 상태(state)와 책임이 어디에 있나](#page-02--세션-vs-토큰-상태(state)와-책임이-어디에-있나)
- [03. JWT의 구성요소 1: Header(JOSE Header)](#page-03--JWT의-구성요소-1-Header(JOSE-Header))
- [04. JWT의 구성요소 2: Payload(Claims)](#page-04--JWT의-구성요소-2-Payload(Claims))
- [05. JWT의 구성요소 3: Signature / JWS / JWE 관계](#page-05--JWT의-구성요소-3-Signature---JWS---JWE-관계)
- [06. Base64URL 인코딩: ‘암호화가 아님’과 디버깅 포인트](#page-06--Base64URL-인코딩-‘암호화가-아님과-디버깅-포인트)
- [07. JWT 생태계 지도: JOSE(JWS/JWE/JWK/JWA)](#page-07--JWT-생태계-지도-JOSE(JWS-JWE-JWK-JWA))
- [08. 표준 클레임(registered claims) 완전정복](#page-08--표준-클레임(registered-claims)-완전정복)
- [09. 커스텀 클레임 설계 원칙: 최소/안전/진화 가능](#page-09--커스텀-클레임-설계-원칙-최소-안전-진화-가능)
- [10. 인증(Authentication)과 인가(Authorization)에서 JWT의 역할](#page-10--인증(Authentication)과-인가(Authorization)에서-JWT의-역할)
- [11. OAuth 2.0에서의 JWT 액세스 토큰 프로필(개념)](#page-11--OAuth-2.0에서의-JWT-액세스-토큰-프로필(개념))
- [12. OIDC의 ID Token: 액세스 토큰과 절대 섞지 말 것](#page-12--OIDC의-ID-Token-액세스-토큰과-절대-섞지-말-것)
- [13. ‘Stateless’의 의미와 흔한 오해](#page-13--‘Stateless의-의미와-흔한-오해)
- [14. 토큰 수명 설계: Access/Refresh의 역할 분담](#page-14--토큰-수명-설계-Access-Refresh의-역할-분담)
- [15. Refresh Token 운영 전략: 회전·재사용 감지](#page-15--Refresh-Token-운영-전략-회전·재사용-감지)
- [16. 로그아웃/폐기(Revocation): 현실적인 접근](#page-16--로그아웃-폐기(Revocation)-현실적인-접근)
- [17. 블랙리스트/세션스토어 패턴: 언제, 어떻게 쓰나](#page-17--블랙리스트-세션스토어-패턴-언제,-어떻게-쓰나)
- [18. 전송 방식: Authorization 헤더 vs Cookie](#page-18--전송-방식-Authorization-헤더-vs-Cookie)
- [19. 저장 위치: LocalStorage/SessionStorage/Cookie/메모리](#page-19--저장-위치-LocalStorage-SessionStorage-Cookie-메모리)
- [20. XSS/CSRF 관점에서의 토큰 보관/전송 전략](#page-20--XSS-CSRF-관점에서의-토큰-보관-전송-전략)
- [21. 알고리즘 선택: HS vs RS/ES, 무엇을 기준으로 고르나](#page-21--알고리즘-선택-HS-vs-RS-ES,-무엇을-기준으로-고르나)
- [22. 대칭키(HS*) 운영: 키 길이·배포·회수의 함정](#page-22--대칭키(HS*)-운영-키-길이·배포·회수의-함정)
- [23. 비대칭키(RS/ES) 운영: JWKS와 키 관리의 정석](#page-23--비대칭키(RS-ES)-운영-JWKS와-키-관리의-정석)
- [24. 키 로테이션: kid, 캐시, 중단 없는 전환](#page-24--키-로테이션-kid,-캐시,-중단-없는-전환)
- [25. 검증 파이프라인: ‘무조건 다 검증’의 체크리스트](#page-25--검증-파이프라인-‘무조건-다-검증의-체크리스트)
- [26. 'none' 알고리즘/alg 혼동: 대표적 JWT 취약점](#page-26--none-알고리즘-alg-혼동-대표적-JWT-취약점)
- [27. JOSE 헤더 기반 공격: kid/jku/x5u/crit 주의](#page-27--JOSE-헤더-기반-공격-kid-jku-x5u-crit-주의)
- [28. issuer/ audience 검증 실수: 멀티 환경에서 특히 위험](#page-28--issuer--audience-검증-실수-멀티-환경에서-특히-위험)
- [29. 시간(exp/nbf/iat)과 clock skew: 운영에서 자주 터지는 지점](#page-29--시간(exp-nbf-iat)과-clock-skew-운영에서-자주-터지는-지점)
- [30. 리플레이(replay) 대응: jti/nonce/단기 토큰의 조합](#page-30--리플레이(replay)-대응-jti-nonce-단기-토큰의-조합)
- [31. JWT에 민감정보를 넣으면 안 되는 이유](#page-31--JWT에-민감정보를-넣으면-안-되는-이유)
- [32. JWE(암호화 JWT): 필요한 경우와 트레이드오프](#page-32--JWE(암호화-JWT)-필요한-경우와-트레이드오프)
- [33. 마이크로서비스에서 JWT: 내부 트러스트의 착각](#page-33--마이크로서비스에서-JWT-내부-트러스트의-착각)
- [34. 권한 모델링: scope/role/permission 매핑 방법](#page-34--권한-모델링-scope-role-permission-매핑-방법)
- [35. 멀티테넌시: 테넌트 경계와 클레임 설계](#page-35--멀티테넌시-테넌트-경계와-클레임-설계)
- [36. DB/캐시와 권한 동기화: 어디까지 토큰에 담나](#page-36--DB-캐시와-권한-동기화-어디까지-토큰에-담나)
- [37. 성능: 서명 검증 비용, 캐싱, p99 최적화](#page-37--성능-서명-검증-비용,-캐싱,-p99-최적화)
- [38. 가용성: JWKS 장애·캐시·서킷브레이커](#page-38--가용성-JWKS-장애·캐시·서킷브레이커)
- [39. 관측/모니터링: 토큰 오류를 ‘원인별’로 보자](#page-39--관측-모니터링-토큰-오류를-‘원인별로-보자)
- [40. 사고 대응: 키 유출·토큰 탈취 시 플레이북](#page-40--사고-대응-키-유출·토큰-탈취-시-플레이북)
- [41. Java/Kotlin에서의 라이브러리 선택 기준](#page-41--Java-Kotlin에서의-라이브러리-선택-기준)
- [42. Java 예제: Nimbus로 JWT 발급(서명)](#page-42--Java-예제-Nimbus로-JWT-발급(서명))
- [43. Java 예제: Nimbus로 JWT 검증(검증 파이프라인)](#page-43--Java-예제-Nimbus로-JWT-검증(검증-파이프라인))
- [44. Kotlin 예제: 코루틴/서버에서의 인증 미들웨어 구성](#page-44--Kotlin-예제-코루틴-서버에서의-인증-미들웨어-구성)
- [45. Spring Security: Resource Server로 JWT 검증 실무](#page-45--Spring-Security-Resource-Server로-JWT-검증-실무)
- [46. 서블릿 Filter로 직접 구현: 프레임워크 없는 최소 구성](#page-46--서블릿-Filter로-직접-구현-프레임워크-없는-최소-구성)
- [47. 테스트 전략: 단위/통합/보안 테스트 체크리스트](#page-47--테스트-전략-단위-통합-보안-테스트-체크리스트)
- [48. 트러블슈팅 20선: 현장에서 가장 많이 겪는 문제](#page-48--트러블슈팅-20선-현장에서-가장-많이-겪는-문제)
- [49. 프로덕션 체크리스트: 배포 전 마지막 점검](#page-49--프로덕션-체크리스트-배포-전-마지막-점검)
- [50. 부록: 용어집 + 참고문서(RFC/OWASP)](#page-50--부록-용어집-+-참고문서(RFC-OWASP))

---

## Page 01 / 50 — JWT 한눈에 보기: 왜 쓰고, 무엇을 해결하나
<a id="page-01--JWT-한눈에-보기-왜-쓰고,-무엇을-해결하나"></a>

### 핵심 요약
- JWT는 **서명(또는 암호화)된 JSON 클레임 묶음**을 URL-safe 문자열로 표현한 토큰입니다.
- 가장 흔한 용도는 **Bearer Access Token**: “토큰을 가진 자가 권한을 가진다”는 모델입니다.
- 장점은 분산 환경에서 **검증을 로컬에서 빠르게** 할 수 있다는 점, 단점은 **폐기/회수(revocation)가 어렵다**는 점입니다.

### 이론적 배경
JWT는 IETF 표준 **RFC 7519**로 정의됩니다. JWT는 내부적으로 JWS(서명) 또는 JWE(암호화) 형식으로 감싸져 “무결성/기밀성”을 제공합니다. (JWT = “클레임을 담은 JOSE 객체”)  
- RFC 7519: JWT 기본 정의  
- RFC 8725: JWT 보안 Best Current Practices(실무 지침)

### 실무에서의 대표 사용 시나리오
- API 인증: `Authorization: Bearer <token>`
- SSO/연합 로그인: OIDC ID Token(로그인 결과) + Access Token(API 호출)
- 서비스 간 호출: 내부 서비스가 서로를 인증할 때(단, 내부망이라도 신뢰 가정은 위험)

### 꼭 기억할 한 문장
> JWT는 “세션을 완전히 대체하는 만능”이 아니라, **짧은 수명의 액세스 토큰**으로 쓸 때 가장 안전하고 예측 가능합니다.



---

## Page 02 / 50 — 세션 vs 토큰: 상태(state)와 책임이 어디에 있나
<a id="page-02--세션-vs-토큰-상태(state)와-책임이-어디에-있나"></a>

### 핵심 요약
- **세션 기반**: 서버가 상태(세션)를 저장하고, 클라이언트는 세션 ID만 보냄.
- **토큰 기반(JWT)**: 서버가 상태를 덜 저장하고, 토큰이 클레임(사용자/권한/만료)을 담음.
- “서버가 상태를 안 갖는다”는 말은 **대체로 ‘액세스 토큰 검증에 DB 조회가 필수가 아니다’** 정도로 이해하면 안전합니다.

### 비교(감각 잡기)
- 세션
  - 장점: 로그아웃/강제 만료 쉬움, 토큰 탈취 시 대응이 상대적으로 쉬움(세션 삭제)
  - 단점: 세션 스토어/클러스터링/스케일 문제, 분산 환경에서 추가 구성 필요
- JWT
  - 장점: 로컬 검증 가능(서명 검증), 마이크로서비스에서 확장 쉬움
  - 단점: 토큰 탈취 시 위험(만료까지 유효), 회수 어려움, 클레임 설계/키 관리 부담

### 실무 포인트
- “완전 무상태(stateless)”를 고집하기보다,
  - Access Token: 짧게(분~수십분)
  - Refresh Token: 서버 상태(저장/회전)로 통제
  - 이 조합이 실무에서 안정적입니다.



---

## Page 03 / 50 — JWT의 구성요소 1: Header(JOSE Header)
<a id="page-03--JWT의-구성요소-1-Header(JOSE-Header)"></a>

### JWT의 3파트(Compact Serialization)
JWT 문자열은 보통 점(`.`)으로 3개가 나뉩니다.

```
base64url(header) . base64url(payload) . base64url(signature)
```

### Header(JOSE Header)에 들어가는 것
- `alg`: 서명 알고리즘(예: `HS256`, `RS256`, `ES256`)
- `typ`: 보통 `JWT`(필수는 아님)
- `kid`: Key ID(키 로테이션에서 자주 사용)
- (그 외) `cty`, `crit`, `jku`, `x5u`, `x5c` 등

### 실무에서의 중요한 원칙
- **Header는 신뢰할 수 없는 입력**입니다.
- 서버는 `alg`를 “토큰이 말하는 대로” 믿으면 안 되고,
  - **허용 알고리즘을 서버 설정으로 고정**해야 합니다(RFC 8725 권고).
- `kid`는 편리하지만 공격 표면을 넓힙니다(27p에서 자세히).

### 사용팁
- 디버깅할 때 Header를 디코딩해 `alg/kid`를 확인하되,
- 실제 운영 토큰을 외부 사이트에 붙여넣는 것은 유출 위험이 있습니다.



---

## Page 04 / 50 — JWT의 구성요소 2: Payload(Claims)
<a id="page-04--JWT의-구성요소-2-Payload(Claims)"></a>

### Payload = Claims(클레임)
Payload에는 “누구에게, 어떤 권한으로, 언제까지 유효한가” 같은 정보가 들어갑니다.

### 클레임의 종류
- Registered Claims(표준): `iss`, `sub`, `aud`, `exp`, `nbf`, `iat`, `jti` 등
- Public Claims: 충돌 방지를 위해 URI 네임스페이스 등으로 구분
- Private Claims: 사내/서비스 내부에서만 합의된 클레임

### 실무 설계 가이드(핵심)
- 토큰에 너무 많은 정보를 넣지 말 것(크기↑, 유출 리스크↑, 변경 비용↑)
- “권한”은 가능하면 `scope` 또는 최소한의 역할(role)만 담고,
  - **권한의 실제 해석은 서버에서**(정책 변경에 대비)

### 주의
- JWT는 **기본적으로 암호화가 아니라 서명**입니다.
- payload는 Base64URL 인코딩이라 쉽게 복원됩니다 → 민감정보 넣으면 위험.



---

## Page 05 / 50 — JWT의 구성요소 3: Signature / JWS / JWE 관계
<a id="page-05--JWT의-구성요소-3-Signature---JWS---JWE-관계"></a>

### JWT는 ‘서명(JWS)’ 또는 ‘암호화(JWE)’로 보호된다
- JWS: 무결성/인증(서명) 제공 → 가장 흔한 JWT 형태
- JWE: 기밀성(암호화) 제공 → 필요할 때만 사용(복잡/비용 증가)

### 왜 대부분은 JWS만 쓰나?
- 액세스 토큰은 보통 “서버 간 전달”이고, HTTPS로 전송 구간이 보호됨
- 토큰이 유출되면 암호화해도 위험(어차피 bearer) — 근본은 저장/전송 보안
- 운영 복잡도(키 교환, 암호화 알고리즘, 장애 시 디버깅)가 상승

### 그래도 JWE가 필요한 경우(대표)
- 토큰이 다양한 중간자(프록시/로그/APM)를 통과하며 노출될 수 있고,
- 토큰 내부 클레임이 노출되면 큰 사고가 되는 경우

### 참고 표준
- JWT: RFC 7519
- JWS: RFC 7515
- JWE: RFC 7516



---

## Page 06 / 50 — Base64URL 인코딩: ‘암호화가 아님’과 디버깅 포인트
<a id="page-06--Base64URL-인코딩-‘암호화가-아님과-디버깅-포인트"></a>

### Base64URL은 ‘보안’이 아니다
JWT의 헤더/페이로드는 Base64URL로 인코딩되어 있을 뿐입니다.
- 누구나 디코딩 가능
- 따라서 **payload를 “암호문”으로 착각하면 안 됩니다**

### Base64URL 디코딩 팁
- Base64URL은 `+ /` 대신 `- _`를 사용하고, padding(`=`)이 생략될 수 있습니다.
- 디버깅 시 흔한 실수: padding 처리 때문에 디코딩 실패

### 디버깅 순서(안전하게)
1) 운영 토큰을 그대로 외부에 공유하지 말 것(로그/캡처도 주의)  
2) 로컬에서 header/payload만 디코딩해 구조 확인  
3) 서명 검증은 “토큰 문자열 전체 + 올바른 키”로 검증

### 현장 팁
- 토큰은 길어질수록 HTTP 헤더 제한(프록시/서버 설정)에 걸릴 수 있습니다.
- ‘정보를 더 담아 DB 조회를 줄이자’는 유혹이 토큰 폭발을 만듭니다.



---

## Page 07 / 50 — JWT 생태계 지도: JOSE(JWS/JWE/JWK/JWA)
<a id="page-07--JWT-생태계-지도-JOSE(JWS-JWE-JWK-JWA)"></a>

### JOSE(자바스크립트 오브젝트 서명/암호화) 스위트
JWT는 JOSE 계열 표준 위에 있습니다.

- JWA: 알고리즘 이름/레지스트리(RFC 7518)
- JWS: 서명 형식(RFC 7515)
- JWE: 암호화 형식(RFC 7516)
- JWK: 키 표현(JSON)(RFC 7517)
- JWT: “클레임을 담은 토큰”(RFC 7519)

### 실무 관점에서 ‘그림’
- 발급자(Authorization Server)가 서명 키로 JWT를 발급
- 소비자(Resource Server)가 공개키/비밀키로 서명 검증
- 키는 JWKS(JSON Web Key Set)로 배포하는 경우가 많음

### 왜 이걸 알아야 하나?
- 문제의 80%는 “JWT 자체”가 아니라,
  - **키 배포(JWKS)**
  - **알고리즘/검증 설정**
  - **클레임 의미(iss/aud)**
  에서 터지기 때문입니다.



---

## Page 08 / 50 — 표준 클레임(registered claims) 완전정복
<a id="page-08--표준-클레임(registered-claims)-완전정복"></a>

### Registered Claims(대표)
- `iss`(issuer): 발급자 식별자(예: `https://auth.example.com`)
- `sub`(subject): 사용자/주체의 고유 ID(변하지 않는 식별자 권장)
- `aud`(audience): 이 토큰을 소비할 대상(리소스 서버)
- `exp`(expiration): 만료 시각(Unix time)
- `nbf`(not before): 이 시각 전에는 유효하지 않음
- `iat`(issued at): 발급 시각
- `jti`(JWT ID): 토큰 고유 식별자(리플레이 방지/블랙리스트 키)

### 실무 팁
- `iss`, `aud`를 **반드시 검증**: 환경/서비스가 늘수록 중요해짐
- `sub`는 이메일처럼 바뀔 수 있는 값보다 내부 userId 같은 것을 권장
- `exp`는 “반드시 존재”하게 만들고, 너무 길게 잡지 말 것(RFC 8725 권고)

### 함정
- `aud`는 문자열이거나 배열일 수 있음 → 코드에서 케이스 처리 필요



---

## Page 09 / 50 — 커스텀 클레임 설계 원칙: 최소/안전/진화 가능
<a id="page-09--커스텀-클레임-설계-원칙-최소-안전-진화-가능"></a>

### 커스텀 클레임 설계 원칙(실무형)
1) **최소화**: 토큰은 복사·로그·유출될 수 있음  
2) **변경 가능성**: 권한/조직 구조는 바뀜 → 토큰에 ‘정책’을 박아두지 말 것  
3) **호환성**: 버전업 시 클레임을 추가/폐기할 수 있어야 함  
4) **충돌 회피**: 공개 클레임은 URI 네임스페이스를 고려

### 권장 패턴
- `scope`: 문자열(공백 구분) 또는 배열(합의 필요)
- `roles`: 너무 자세히 넣지 말고, coarse-grained 역할만
- `tenant_id`, `org_id`: 멀티테넌시라면 거의 필수(하지만 접근 통제 설계와 함께)

### 지양 패턴
- ‘DB 대체’ 수준의 사용자 프로필/권한 상세를 토큰에 넣기
- 민감정보(주민번호, 계좌 등) 포함

### 사용팁
- “토큰 스키마 문서”를 만들어 클레임 의미/형식/버전을 관리하세요(49p 템플릿 참고).



---

## Page 10 / 50 — 인증(Authentication)과 인가(Authorization)에서 JWT의 역할
<a id="page-10--인증(Authentication)과-인가(Authorization)에서-JWT의-역할"></a>

### 인증 vs 인가
- 인증(Authentication): “너 누구야?”
- 인가(Authorization): “너 이거 해도 돼?”

JWT는 보통 **인증 결과를 운반**하고, 동시에 인가에 필요한 최소 정보(scope 등)를 함께 실어 나릅니다.

### 실무에서의 위치
- 로그인(인증) 자체는 보통 ID/PW, SSO, OIDC 등으로 수행
- 로그인 결과로 Access Token(JWT)을 받고,
- API 호출 시 JWT를 제시해 인가 판단

### 흔한 오해
- “JWT만 있으면 인증이 끝났다” → 토큰은 **발급 시점의 인증 결과**일 뿐,
  - 탈취되면 누가 갖고 있든 통과(=bearer)합니다.
- 그래서 토큰 보관/전송/만료가 보안의 핵심입니다.



---

## Page 11 / 50 — OAuth 2.0에서의 JWT 액세스 토큰 프로필(개념)
<a id="page-11--OAuth-2.0에서의-JWT-액세스-토큰-프로필(개념)"></a>

### OAuth 2.0에서 JWT는 ‘액세스 토큰 표현’ 중 하나
OAuth에서 Access Token은 원래 “형식이 정해져 있지” 않습니다(opaque일 수도).
하지만 상호운용을 위해 JWT 형식의 프로필이 표준화되어 있습니다(RFC 9068).

### 실무 관점: JWT access token을 쓰는 이유
- Resource Server가 Authorization Server에 매번 문의(introspection)하지 않고도
  - 로컬에서 서명 검증 후 클레임으로 권한 판단 가능

### 주의
- Access Token과 ID Token의 목적은 다릅니다.
- “토큰을 JWT로 쓴다” = “무조건 안전”이 아니고,
  - 검증(iss/aud/exp/signature)과 키 관리가 안전을 좌우합니다.



---

## Page 12 / 50 — OIDC의 ID Token: 액세스 토큰과 절대 섞지 말 것
<a id="page-12--OIDC의-ID-Token-액세스-토큰과-절대-섞지-말-것"></a>

### OIDC ID Token은 ‘로그인 결과’(인증)에 대한 토큰
- ID Token은 “사용자가 인증되었다”는 정보를 클라이언트에 전달하기 위한 토큰입니다.
- Access Token은 “API 호출 권한”을 나타냅니다.

### 절대 섞지 말아야 하는 이유
- ID Token을 API 인증에 쓰면,
  - `aud`(대상)가 클라이언트 앱일 가능성이 높고,
  - 리소스 서버 관점에서 검증이 불완전해질 수 있습니다.
- 반대로 Access Token을 로그인 상태 표시로 쓰면,
  - 사용자 프로필/로그인 시각 같은 의미가 맞지 않을 수 있습니다.

### 실무 팁
- 프론트엔드/모바일은 보통:
  - ID Token: 로그인 UI/프로필 표시(필요 시)
  - Access Token: API 호출
  - Refresh Token: 백그라운드 재발급(보안 주의)



---

## Page 13 / 50 — ‘Stateless’의 의미와 흔한 오해
<a id="page-13--‘Stateless의-의미와-흔한-오해"></a>

### Stateless의 ‘정확한’ 의미
JWT가 자주 “stateless 인증”이라고 불리지만, 현실은 이렇게 정리하는 게 안전합니다:

- **검증을 위해 DB 조회가 필수가 아니다**(서명 검증 + 클레임)
- 하지만 시스템 전체가 상태가 없는 건 아님:
  - Refresh Token 저장/회전
  - 사용자 강제 로그아웃(토큰 무효화) 요구
  - 이상 징후 탐지, 디바이스 관리, 세션 정책

### 실무에서의 균형점
- Access Token은 stateless(짧게)
- Refresh Token은 stateful(통제/회수/정책 적용)
이 구성이 가장 흔한 “정합성/보안/운영 난이도” 균형입니다.



---

## Page 14 / 50 — 토큰 수명 설계: Access/Refresh의 역할 분담
<a id="page-14--토큰-수명-설계-Access-Refresh의-역할-분담"></a>

### Access Token vs Refresh Token
- Access Token: 자주 쓰고, 탈취되면 위험 → **짧게**
- Refresh Token: 덜 쓰고, 더 민감 → **더 강하게 보호 + 서버 통제**

### 권장 수명(예시, 조직마다 다름)
- Access Token: 5~30분
- Refresh Token: 며칠~수주(디바이스/보안 정책에 따라)

### 운영 팁
- Access Token을 너무 길게 잡으면:
  - 탈취 리스크 증가
  - 권한 변경 반영 지연(“토큰에 담긴 권한이 오래 유지”)
- 너무 짧게 잡으면:
  - 재발급 트래픽 증가(특히 모바일/웹에서)
  - 그래서 Refresh Token + 회전 전략이 중요해집니다.



---

## Page 15 / 50 — Refresh Token 운영 전략: 회전·재사용 감지
<a id="page-15--Refresh-Token-운영-전략-회전·재사용-감지"></a>

### Refresh Token 회전(Rotation) 패턴
- 매번 새 Refresh Token을 발급하고, 이전 것을 폐기
- “이전 토큰 재사용”이 감지되면 탈취로 판단하고 세션 전체를 무효화

### 실무 적용 흐름(개념)
1) 클라이언트가 refresh 요청
2) 서버가 refresh token을 검증(저장된 해시/상태 확인)
3) 새 access token + 새 refresh token 발급
4) 이전 refresh token은 더 이상 유효하지 않게 처리

### 주의점
- refresh token은 유출 시 피해가 큼 → 저장 위치/전송 방식에 특히 민감
- 모바일은 OS 보안 저장소(Keychain/Keystore) 사용 고려
- 웹은 refresh token을 JS에서 접근 못 하게 **HttpOnly cookie** 전략을 자주 사용



---

## Page 16 / 50 — 로그아웃/폐기(Revocation): 현실적인 접근
<a id="page-16--로그아웃-폐기(Revocation)-현실적인-접근"></a>

### 로그아웃/폐기(Revocation)가 JWT에서 어려운 이유
JWT는 “서명만 검증되면” 만료 전까지 유효합니다.
- 서버가 ‘상태를 안 본다면’, 토큰을 강제로 죽일 방법이 없음

### 현실적인 해법 3가지
1) Access Token을 짧게 + Refresh Token을 서버 상태로 통제(가장 흔함)  
2) 블랙리스트/Revocation 리스트(일부 케이스에서)  
3) 토큰 버전(tokenVersion) 또는 사용자 세션 버전으로 강제 무효화

### 선택 기준(감각)
- “즉시 로그아웃이 반드시 필요” + “규모가 크다” → (1)+(3) 조합이 자주 쓰임
- “고위험(금융/관리자)” → revocation 체크를 더 강하게 고려



---

## Page 17 / 50 — 블랙리스트/세션스토어 패턴: 언제, 어떻게 쓰나
<a id="page-17--블랙리스트-세션스토어-패턴-언제,-어떻게-쓰나"></a>

### 블랙리스트(Revocation List) 패턴
아이디어: `jti`(또는 토큰 해시)를 저장하고, 요청마다 “폐기 여부”를 조회.

### 장점
- 만료 전 강제 폐기 가능
- 사고 대응(토큰 유출)에서 즉시 차단 가능

### 단점(중요)
- 요청마다 저장소 조회가 필요 → 다시 stateful + 병목 가능
- TTL/만료 관리 필요(블랙리스트가 무한히 커지면 안 됨)

### 실무 팁
- 블랙리스트는 “기본 전략”이 아니라 **특정 요구(고위험/규제)에서만** 쓰는 경우가 많음
- 사용한다면:
  - 저장은 Redis 같은 in-memory + TTL(토큰 exp와 같은 TTL) 조합이 흔함
  - 토큰 전체를 저장하지 말고 “해시 저장” 고려(유출 대비)



---

## Page 18 / 50 — 전송 방식: Authorization 헤더 vs Cookie
<a id="page-18--전송-방식-Authorization-헤더-vs-Cookie"></a>

### 전송 방식 1: Authorization 헤더(Bearer)
```
Authorization: Bearer <access_token>
```
- API/모바일/서비스 간 호출에서 가장 흔함
- CORS/프론트엔드 구성에 따라 관리가 명확

### 전송 방식 2: Cookie
- 브라우저 기반에서 많이 사용(특히 refresh token을 HttpOnly cookie에)
- 장점: JS 접근 차단(HttpOnly), 자동 전송
- 단점: CSRF 방어가 필요(SameSite, CSRF 토큰 등)

### 실무 팁
- access token은 헤더, refresh token은 HttpOnly cookie로 나누는 설계가 많습니다.
- 절대 권장하지 않는 것: 토큰을 URL 쿼리스트링으로 전달(로그/레퍼러로 유출 가능).



---

## Page 19 / 50 — 저장 위치: LocalStorage/SessionStorage/Cookie/메모리
<a id="page-19--저장-위치-LocalStorage-SessionStorage-Cookie-메모리"></a>

### 저장 위치별 특징(웹 기준)
- LocalStorage: XSS에 매우 취약(탈취 쉬움) → 접근 제어만으로는 위험
- SessionStorage: 탭 종료 시 사라지지만 XSS엔 여전히 취약
- Cookie(HttpOnly): JS 접근 차단 가능(좋음) / CSRF 방어 필요
- 메모리(런타임 변수): 새로고침 시 소멸(UX 불편) / XSS 대비는 상대적으로 낫지만 완전 해결 아님

### 추천 패턴(웹)
- Refresh Token: HttpOnly + Secure + SameSite 쿠키
- Access Token: 메모리(또는 짧게 유지) + 필요 시 silent refresh

### 모바일
- OS 보안 저장소(Keychain/Keystore)를 우선 고려



---

## Page 20 / 50 — XSS/CSRF 관점에서의 토큰 보관/전송 전략
<a id="page-20--XSS-CSRF-관점에서의-토큰-보관-전송-전략"></a>

### XSS vs CSRF 관점에서의 핵심
- **XSS**: 악성 스크립트가 페이지에서 실행되면 토큰을 훔칠 수 있음(특히 LocalStorage)
- **CSRF**: 쿠키는 자동 전송되므로 공격자가 사용자의 브라우저로 요청을 보내게 할 수 있음

### 정리
- 토큰을 쿠키에 두면 XSS에 강해지지만 CSRF 대비가 필요
- 토큰을 헤더로 보내면 CSRF 부담이 줄어들지만, 토큰을 JS가 관리하므로 XSS에 취약

### 실무 팁(쿠키 전략)
- `Secure`(HTTPS만), `HttpOnly`, `SameSite=Lax/Strict` 기본
- 상태 변경 요청(POST/PUT/DELETE)에 CSRF 토큰 또는 SameSite 전략 정교화



---

## Page 21 / 50 — 알고리즘 선택: HS vs RS/ES, 무엇을 기준으로 고르나
<a id="page-21--알고리즘-선택-HS-vs-RS-ES,-무엇을-기준으로-고르나"></a>

### 알고리즘 선택의 큰 기준
- HS*(HMAC): 대칭키(서명/검증에 같은 키) → 키 공유 필요
- RS*/ES*(RSA/ECDSA): 비대칭키(서명은 private, 검증은 public) → 키 배포가 상대적으로 안전

### 실무에서 RS/ES가 선호되는 이유
- 리소스 서버(검증자)가 많아질수록 “비밀키 공유”는 위험
- 공개키는 노출되어도 괜찮고, JWKS로 배포하기 쉬움

### 선택 가이드
- 단일 서비스/단순 환경: HS도 가능하지만 키 관리가 관건
- 마이크로서비스/외부 공개 API: RS/ES가 보통 더 안전한 운영 모델

### 참고
- RFC 8725는 안전한 알고리즘 선택과 검증을 강조합니다.



---

## Page 22 / 50 — 대칭키(HS*) 운영: 키 길이·배포·회수의 함정
<a id="page-22--대칭키(HS*)-운영-키-길이·배포·회수의-함정"></a>

### HS256(대칭키) 운영의 함정
- 키가 유출되면 “발급도, 검증도” 다 뚫립니다.
- 여러 서비스가 검증하려면 같은 비밀키를 배포해야 함 → 확산 리스크

### 최소 권장 사항
- 충분히 긴 랜덤 키(예: 256비트 이상) 사용
- 소스코드/환경변수/로그에 키가 섞이지 않게
- 키 로테이션 계획(키 ID, 이중검증 기간) 수립

### 실무 팁
- 가능하면 RS/ES로 전환하면 운영/보안 측면에서 유리한 경우가 많습니다.



---

## Page 23 / 50 — 비대칭키(RS/ES) 운영: JWKS와 키 관리의 정석
<a id="page-23--비대칭키(RS-ES)-운영-JWKS와-키-관리의-정석"></a>

### RS/ES(비대칭키) 운영: 정석 패턴
- 발급자(Authorization Server)가 private key로 서명
- 리소스 서버는 public key로 검증(비밀키 불필요)

### JWKS(JSON Web Key Set)
- 공개키를 JSON 형태로 묶어 배포하는 방식(RFC 7517)
- `jwks_uri`는 OAuth 메타데이터(RFC 8414)에서 제공되기도 함

### 장점
- 공개키 배포/캐시가 쉬움
- 리소스 서버가 private key를 갖지 않으므로 유출 위험 감소

### 주의
- issuer를 고정해 신뢰 경계를 명확히 하고, JWKS URL을 토큰이 지정하게 하지 말 것(27p)



---

## Page 24 / 50 — 키 로테이션: kid, 캐시, 중단 없는 전환
<a id="page-24--키-로테이션-kid,-캐시,-중단-없는-전환"></a>

### 키 로테이션(교체)의 목표
- 키를 정기적으로 바꿀 수 있어야 하며,
- 바꾸는 동안에도 서비스가 끊기지 않아야 합니다.

### 일반적인 로테이션 절차
1) 새 키 추가(새 `kid`)
2) 발급은 새 키로 전환
3) 일정 기간 검증은 (구키 + 신키) 둘 다 허용
4) 구키 폐기(더 이상 JWKS에 노출하지 않음)

### 실무 팁
- 토큰 만료(Access Token TTL)보다 “조금 더 긴” 기간 동안 구키 검증을 허용
- 키 교체 직후 401이 폭증하면 JWKS 캐시/갱신 정책을 먼저 의심



---

## Page 25 / 50 — 검증 파이프라인: ‘무조건 다 검증’의 체크리스트
<a id="page-25--검증-파이프라인-‘무조건-다-검증의-체크리스트"></a>

### 검증 파이프라인(실무 체크리스트)
JWT 검증은 “서명만 확인”하면 끝이 아닙니다. RFC 8725는 아래 같은 검증을 강조합니다.

#### 1) 기본 구조 검증
- 3파트인지, 파싱 가능한지
- 허용하지 않는 헤더 파라미터/알고리즘이 아닌지

#### 2) 서명 검증
- 서버 설정으로 `alg` 화이트리스트 고정
- 올바른 키로 검증(안전한 `kid` 룩업)

#### 3) 클레임 검증(필수)
- `iss` 정확히 일치
- `aud` 포함
- `exp` 만료 확인(+leeway)
- `nbf` 확인(+leeway)
- 필요 시 `jti`/폐기 여부, 사용자 상태(정지 등)

### 실무 팁
- 실패 사유를 분류해 운영 지표로 남기면(39p) 장애 분석이 빨라집니다.



---

## Page 26 / 50 — 'none' 알고리즘/alg 혼동: 대표적 JWT 취약점
<a id="page-26--none-알고리즘-alg-혼동-대표적-JWT-취약점"></a>

### 대표 취약점 1: alg='none'
과거 일부 구현은 `{"alg":"none"}`인 토큰을 “서명 없는 토큰”으로 받아들였습니다.

### 대표 취약점 2: 알고리즘 혼동(HS ↔ RS)
예: RS256을 기대하는 서버에서 공격자가 HS256으로 바꾸고,
공개키를 HMAC secret처럼 쓰게 만들어 서명을 통과시키는 유형.

### 예방 원칙(강력)
- 서버에서 허용 알고리즘을 고정(화이트리스트)
- 키 타입과 알고리즘 매칭을 강제
- 라이브러리 기본값을 그대로 믿지 말고 설정을 확인

### 참고
- OWASP JWT Cheat Sheet와 RFC 8725가 반복 경고하는 항목입니다.



---

## Page 27 / 50 — JOSE 헤더 기반 공격: kid/jku/x5u/crit 주의
<a id="page-27--JOSE-헤더-기반-공격-kid-jku-x5u-crit-주의"></a>

### JOSE 헤더 기반 공격 표면
- `kid`: 키 선택 인덱스. 이를 파일 경로/DB 키로 직접 쓰면 인젝션 위험
- `jku`: JWKS URL을 토큰이 지정하게 하면 SSRF/키 바꿔치기 가능
- `x5u`: 인증서 URL 지정(유사 위험)
- `crit`: 이해 못 하는 확장 헤더가 있으면 거부해야 함(무시하면 위험)

### 안전한 운영 원칙
- 키는 “서버가 신뢰하는 고정 저장소/URL”에서만 가져오기
- `kid`는 단순 식별자 + 화이트리스트 룩업
- 외부 토큰이 “키 URL”을 지정하게 하지 말 것

### 실무 팁
- `kid unknown` 급증은 공격 신호일 수 있으니 모니터링 지표로.



---

## Page 28 / 50 — issuer/ audience 검증 실수: 멀티 환경에서 특히 위험
<a id="page-28--issuer--audience-검증-실수-멀티-환경에서-특히-위험"></a>

### issuer(iss)/audience(aud) 검증 실수는 “환경이 늘 때” 폭발한다
예를 들어:
- staging과 prod에서 issuer가 다르거나,
- 여러 API가 있고 각각 audience가 다른데,
- 검증에서 `aud`를 빼먹으면 “다른 서비스용 토큰”이 통과할 수 있습니다.

### 안전한 검증 규칙
- `iss`는 **정확히 일치**(prefix 매칭 금지)
- `aud`는 내 서비스 식별자가 포함되어야 함
- 멀티테넌시면 `tenant_id`도 함께 검증(필요 시)

### 실무 팁
- 서비스별 audience 분리는 사고 범위를 줄이는 매우 효과적인 방법입니다.



---

## Page 29 / 50 — 시간(exp/nbf/iat)과 clock skew: 운영에서 자주 터지는 지점
<a id="page-29--시간(exp-nbf-iat)과-clock-skew-운영에서-자주-터지는-지점"></a>

### 시간 클레임은 운영 이슈의 단골
- `exp`: 만료
- `nbf`: 아직 유효하지 않음
- `iat`: 발급 시각

### clock skew(서버 시계 오차)
시간이 조금씩 다르면 막 발급된 토큰이 `nbf/iat` 때문에 거부될 수 있습니다.

### 실무 가이드
- NTP 시간 동기화는 기본
- 검증 시 leeway(허용 오차) 30~120초를 두는 경우가 많음(서비스 특성에 따라)
- leeway가 길수록 보안 정책이 느슨해지므로 최소화

### 모니터링
- `nbf`/`iat` 거부 비율 상승은 시간 동기화/클라이언트 버그 신호.



---

## Page 30 / 50 — 리플레이(replay) 대응: jti/nonce/단기 토큰의 조합
<a id="page-30--리플레이(replay)-대응-jti-nonce-단기-토큰의-조합"></a>

### 리플레이(replay)란?
bearer 토큰은 탈취되면 만료 전까지 반복 사용될 수 있습니다.

### 대응 전략(현실적인 조합)
- **짧은 exp**: 피해 창을 줄이는 1순위
- `jti` + 서버 저장(고위험 요청만): 재사용 감지/차단
- 중요한 동작은 step-up auth(재인증) 같은 추가 보호 고려

### 실무 팁
- 모든 요청에 jti 검사하면 비용이 큽니다.
- 위험도가 높은 엔드포인트(결제/관리자)부터 단계적으로 적용하세요.



---

## Page 31 / 50 — JWT에 민감정보를 넣으면 안 되는 이유
<a id="page-31--JWT에-민감정보를-넣으면-안-되는-이유"></a>

### 왜 민감정보를 넣으면 안 되나?
- JWT payload는 쉽게 디코딩 가능
- 프록시/로그/APM/브라우저 확장/클라이언트 저장소 등 노출 경로가 많음
- 토큰은 여러 시스템을 거치며 복제될 수 있음

### 지양 예시
- 주민번호/여권번호/계좌/전화번호
- 내부 정책의 핵심 비즈니스 데이터

### 대안
- 토큰에는 식별자/최소 권한만 담고 상세는 서버에서 조회(캐시로 보완)
- 정말 불가피하면 JWE 고려(단, 운영 복잡도 증가)



---

## Page 32 / 50 — JWE(암호화 JWT): 필요한 경우와 트레이드오프
<a id="page-32--JWE(암호화-JWT)-필요한-경우와-트레이드오프"></a>

### JWE를 고려해야 하는 케이스(요약)
- 토큰이 다양한 중간자(프록시/서드파티)를 통과하고,
- 토큰 내부 클레임이 노출되면 큰 사고가 되는 경우,
- 그리고 그 클레임을 토큰에 담아야만 하는 경우

### 트레이드오프
- 장점: payload 기밀성
- 단점: 성능 비용 + 운영 복잡도 + 디버깅 난이도 증가

### 실무 조언
- JWE는 “기본값”이 아니라 “필요할 때만” 선택하는 옵션입니다.



---

## Page 33 / 50 — 마이크로서비스에서 JWT: 내부 트러스트의 착각
<a id="page-33--마이크로서비스에서-JWT-내부-트러스트의-착각"></a>

### 내부망이라도 JWT가 만능은 아니다
- 내부 트래픽을 ‘완전 신뢰’하면 횡적 이동 공격 위험
- 토큰 탈취/재사용은 여전히 가능

### 권장 패턴
- 사용자 토큰과 서비스 토큰 분리
- 서비스별 audience 분리
- 짧은 만료 + 키/issuer 신뢰 경계 명확화
- 필요 시 mTLS/추가 보강 고려

### 팁
- “내부라서 안전”은 운영 규모가 커질수록 깨지는 가정입니다.



---

## Page 34 / 50 — 권한 모델링: scope/role/permission 매핑 방법
<a id="page-34--권한-모델링-scope-role-permission-매핑-방법"></a>

### 권한 모델: scope vs role vs permission
- scope: OAuth의 권한 범위(예: `read:orders`)
- role: 직무/그룹(예: `admin`)
- permission: 더 세밀한 액션(예: `order:cancel`)

### 실무 설계 팁
- 토큰에는 scope 중심이 운영하기 쉬움(정책 변경 시 토큰 스키마를 덜 건드림)
- role은 coarse-grained로 유지
- permission을 토큰에 전부 넣는 건 지양(크기/변경 비용 폭발)

### 서버 매핑 전략
- `scope` → 애플리케이션 권한 체크로 매핑
- 복잡한 정책은 서버가 관리(토큰은 “증거” 역할)



---

## Page 35 / 50 — 멀티테넌시: 테넌트 경계와 클레임 설계
<a id="page-35--멀티테넌시-테넌트-경계와-클레임-설계"></a>

### 멀티테넌시에서의 JWT 설계 포인트
- 테넌트마다 사용자 권한이 달라질 수 있음
- 토큰에 `tenant_id`를 포함하고, 리소스 접근 시 반드시 검증

### 권장 검증
- URL/헤더 테넌트 식별자와 토큰 `tenant_id`가 일치해야 함
- 또는 audience 자체를 테넌트/서비스별로 분리

### 주의
- 테넌트 경계는 토큰뿐 아니라 DB/권한/감사 로깅까지 일관되게 설계해야 합니다.



---

## Page 36 / 50 — DB/캐시와 권한 동기화: 어디까지 토큰에 담나
<a id="page-36--DB-캐시와-권한-동기화-어디까지-토큰에-담나"></a>

### 토큰에 담을 것 vs 서버에서 조회할 것
결정 기준:
- 자주 바뀌지 않고, 노출되어도 괜찮으며, 검증에 꼭 필요한 최소 정보 → 토큰
- 자주 바뀌거나, 민감하거나, 정책 변화가 잦은 정보 → 서버 조회

### 예시
- 토큰에 담기 좋은 것: `sub`, `iss`, `aud`, `exp`, `scope`, `tenant_id`
- 서버 조회가 좋은 것: 사용자 상태(정지/탈퇴), 세부 권한, 리스크 레벨

### 캐시 팁
- 서버 조회가 부담되면 캐시로 보완하되,
- 정합성 요구 수준에 맞는 TTL/무효화 전략을 문서화하세요.



---

## Page 37 / 50 — 성능: 서명 검증 비용, 캐싱, p99 최적화
<a id="page-37--성능-서명-검증-비용,-캐싱,-p99-최적화"></a>

### 성능 관점에서의 JWT
- 서명 검증은 CPU 비용이 있음(특히 RS/ECDSA)
- 하지만 DB 조회를 줄이면 전체적으로 더 빠를 수 있음

### 최적화 포인트
- JWKS 캐시: 매 요청마다 원격 호출 금지
- 파싱/검증 라이브러리 객체 생성 비용 주의(p99)
- 큰 토큰은 네트워크/헤더 파싱 비용 증가

### 실무 팁
- 검증 실패는 빠르게 실패(불필요한 작업 금지)
- 동일 요청에서 토큰을 여러 번 검증하지 않게(미들웨어에서 한 번)



---

## Page 38 / 50 — 가용성: JWKS 장애·캐시·서킷브레이커
<a id="page-38--가용성-JWKS-장애·캐시·서킷브레이커"></a>

### JWKS 장애/지연이 전체 장애로 번질 수 있다
패턴:
- JWKS 갱신 호출이 느려짐 → 요청 지연
- 스레드/코루틴 점유 증가 → 처리율 감소 → 대기열 증가 → 재시도 증가 → 증폭

### 방어 전략
- JWKS는 캐시 + 백그라운드 갱신 + 타임아웃/백오프
- 갱신 실패 시 정책:
  - 이전 키로 검증 지속(일시 장애 대응) vs 즉시 실패(보안 엄격)

### 팁
- 키 로테이션과 JWKS 캐시 TTL 조합을 반드시 시뮬레이션하세요(통합 테스트 권장).



---

## Page 39 / 50 — 관측/모니터링: 토큰 오류를 ‘원인별’로 보자
<a id="page-39--관측-모니터링-토큰-오류를-‘원인별로-보자"></a>

### 모니터링: JWT 오류를 ‘원인별’로 분해하라
유용한 분류:
- `signature_invalid`
- `token_expired`
- `nbf_not_reached`
- `issuer_mismatch`
- `audience_mismatch`
- `kid_unknown`
- `malformed_token`

### 효과
- 만료 폭증: 클라이언트 갱신 문제/시간 문제
- 서명 실패: 키 불일치/공격 가능성
- issuer/aud 불일치: 환경 설정/토큰 오남용

### 실무 팁
- 401/403의 비율만 보지 말고, 실패 이유를 태깅해 대시보드화하세요.



---

## Page 40 / 50 — 사고 대응: 키 유출·토큰 탈취 시 플레이북
<a id="page-40--사고-대응-키-유출·토큰-탈취-시-플레이북"></a>

### 사고 대응 플레이북(요약)
#### 1) 키 유출(서명 키 유출)
- 공격자가 “정상 토큰” 발급 가능 → 최우선 사고
- 즉시:
  - 키 로테이션(새 키) + 구키 폐기
  - 영향 범위 추정(유출 시점 ~ 조치 시점)
  - 필요 시 강제 재로그인/토큰 버전 무효화

#### 2) 토큰 탈취(개별 토큰 유출)
- refresh 세션 무효화(회전/폐기)
- 고위험이면 블랙리스트/jti 차단
- 원인(XSS/로그/클라이언트 저장소) 파악 및 재발 방지

### 팁
- “키/토큰/클레임”의 노출 경로를 사전에 문서화해 두면 대응 속도가 크게 올라갑니다.



---

## Page 41 / 50 — Java/Kotlin에서의 라이브러리 선택 기준
<a id="page-41--Java-Kotlin에서의-라이브러리-선택-기준"></a>

### Java/Kotlin 라이브러리 선택 기준(실무)
대표적으로 많이 쓰는 선택지:
- Nimbus JOSE + JWT: JOSE 전반 지원, 엔터프라이즈에서 흔함
- JJWT: 사용이 비교적 간단

선택 기준 체크리스트:
- RS/ES + JWKS + kid 로테이션 지원이 필요한가?
- alg 화이트리스트/claim 검증을 강제하기 쉬운가?
- 업데이트/취약점 대응이 활발한가?
- Spring Security와의 통합이 중요한가?

### 팁
- 가능하면 프레임워크 검증을 우선 사용하고(예: Spring Resource Server),
- 커스텀은 “클레임 매핑/특수 정책”만 최소화하는 편이 안전합니다.



---

## Page 42 / 50 — Java 예제: Nimbus로 JWT 발급(서명)
<a id="page-42--Java-예제-Nimbus로-JWT-발급(서명)"></a>

### Java 예제: Nimbus로 JWT 발급(개념 코드)
> 운영에서는 키 보관(HSM/KMS), 로테이션, 감사 로그까지 포함해야 합니다.

```java
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;

import java.time.Instant;
import java.util.Date;

public class JwtIssuer {
  public static String issue(com.nimbusds.jose.jwk.RSAKey privateJwk,
                             String issuer, String subject) throws Exception {
    JWSSigner signer = new RSASSASigner(privateJwk.toPrivateKey());

    var header = new JWSHeader.Builder(JWSAlgorithm.RS256)
        .type(JOSEObjectType.JWT)
        .keyID(privateJwk.getKeyID())
        .build();

    var now = Instant.now();
    var claims = new JWTClaimsSet.Builder()
        .issuer(issuer)
        .subject(subject)
        .audience("api://orders")
        .issueTime(Date.from(now))
        .expirationTime(Date.from(now.plusSeconds(15 * 60)))
        .claim("scope", "read:orders write:orders")
        .build();

    var jwt = new SignedJWT(header, claims);
    jwt.sign(signer);
    return jwt.serialize();
  }
}
```

### 포인트
- `iss/aud/exp`를 명확히 넣기
- `kid`는 로테이션에 유리하지만 안전한 룩업 방식이 필수



---

## Page 43 / 50 — Java 예제: Nimbus로 JWT 검증(검증 파이프라인)
<a id="page-43--Java-예제-Nimbus로-JWT-검증(검증-파이프라인)"></a>

### Java 예제: Nimbus로 JWT 검증(검증 파이프라인)
```java
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.*;
import com.nimbusds.jwt.*;

import java.time.Instant;
import java.util.Date;
import java.util.Set;

public class JwtVerifier {
  private final Set<JWSAlgorithm> allowedAlgs = Set.of(JWSAlgorithm.RS256);
  private final String expectedIssuer = "https://auth.example.com";
  private final String expectedAudience = "api://orders";
  private final long leewaySeconds = 60;

  public JWTClaimsSet verify(String token,
      java.security.interfaces.RSAPublicKey publicKey) throws Exception {

    SignedJWT jwt = SignedJWT.parse(token);

    // 1) alg 화이트리스트
    JWSAlgorithm alg = jwt.getHeader().getAlgorithm();
    if (!allowedAlgs.contains(alg)) throw new JOSEException("disallowed alg: " + alg);

    // 2) 서명 검증
    if (!jwt.verify(new RSASSAVerifier(publicKey))) throw new JOSEException("invalid signature");

    // 3) 클레임 검증
    var claims = jwt.getJWTClaimsSet();
    if (!expectedIssuer.equals(claims.getIssuer())) throw new JOSEException("issuer mismatch");

    if (claims.getAudience() == null || !claims.getAudience().contains(expectedAudience))
      throw new JOSEException("audience mismatch");

    Date exp = claims.getExpirationTime();
    if (exp == null) throw new JOSEException("missing exp");

    Instant now = Instant.now();
    if (now.isAfter(exp.toInstant().plusSeconds(leewaySeconds))) throw new JOSEException("expired");

    Date nbf = claims.getNotBeforeTime();
    if (nbf != null && now.isBefore(nbf.toInstant().minusSeconds(leewaySeconds)))
      throw new JOSEException("nbf not reached");

    return claims;
  }
}
```

### 실무 팁
- 공개키는 JWKS에서 캐시해 받아오는 구조가 일반적(38p)
- 실패 사유 태깅/모니터링(39p)



---

## Page 44 / 50 — Kotlin 예제: 코루틴/서버에서의 인증 미들웨어 구성
<a id="page-44--Kotlin-예제-코루틴-서버에서의-인증-미들웨어-구성"></a>

### Kotlin 서버에서의 인증 미들웨어(개념)
코루틴 기반 서버(Ktor 등)에서도 원칙은 같습니다.
- 검증은 요청당 1회
- JWKS 원격 호출은 요청 path에서 제거(캐시/백그라운드)

#### 코루틴 환경 팁
- JWKS 갱신: 별도 코루틴에서 주기 수행 + 백오프
- 요청 처리: 로컬 캐시 키로 즉시 검증해 p99 안정화

#### 구조 예시(개념)
- `AuthPlugin`/`AuthMiddleware`
  - Bearer 토큰 파싱
  - 검증(서명/iss/aud/exp)
  - principal(사용자/권한)을 request context에 저장

> “느린 JWKS 호출”이 스레드/코루틴을 점유해 장애를 증폭시키지 않게 설계하세요.



---

## Page 45 / 50 — Spring Security: Resource Server로 JWT 검증 실무
<a id="page-45--Spring-Security-Resource-Server로-JWT-검증-실무"></a>

### Spring Security(Resource Server) 실무 포인트
- 검증 파이프라인과 JWKS 처리(캐시/갱신)를 잘 갖춘 편
- 직접 구현보다 실수 확률이 낮음

### 전형적 구성(개념)
- issuer 기반 자동설정(`issuer-uri`)
- 또는 jwks 직접 지정(`jwk-set-uri`)

### 커스터마이징 포인트
- scope → authorities 매핑
- aud 검증기 추가
- 멀티테넌시 issuer/aud 라우팅(고급)

### 팁
- Spring 버전별 설정 방식이 다를 수 있으니, 사용 중인 버전 문서를 함께 확인하세요.



---

## Page 46 / 50 — 서블릿 Filter로 직접 구현: 프레임워크 없는 최소 구성
<a id="page-46--서블릿-Filter로-직접-구현-프레임워크-없는-최소-구성"></a>

### 서블릿 Filter로 직접 구현하는 최소 흐름
#### 처리 순서(개념)
1) `Authorization`에서 Bearer 토큰 추출  
2) 토큰 파싱/검증(alg 화이트리스트, 서명, iss/aud/exp…)  
3) 성공 시: 사용자 정보/권한을 요청 컨텍스트에 연결  
4) 실패 시: 401 응답(토큰/비밀값은 마스킹)

#### 주의점
- 필터는 멀티스레드 환경 → 공유 상태 금지
- 토큰 전체를 로그에 남기지 말 것
- JWKS 갱신을 요청마다 하지 말 것(38p)

#### 팁
- 검증 로직을 별도 클래스로 분리해 단위 테스트 가능하게.



---

## Page 47 / 50 — 테스트 전략: 단위/통합/보안 테스트 체크리스트
<a id="page-47--테스트-전략-단위-통합-보안-테스트-체크리스트"></a>

### 테스트 전략 요약
#### 단위 테스트
- 만료(exp)/미도래(nbf)/issuer/audience/alg 정책 검증
- leeway(허용 오차) 케이스

#### 통합 테스트
- JWKS 캐시/갱신/키 로테이션 시나리오
- 프록시/게이트웨이 환경에서 헤더 제한 테스트

#### 보안 테스트
- OWASP WSTG의 JWT 테스트 항목 참고
- 대표 공격: alg=none, HS/RS 혼동, kid 인젝션

### 팁
- 테스트 키와 운영 키를 절대 섞지 않기(키 관리 사고 방지).



---

## Page 48 / 50 — 트러블슈팅 20선: 현장에서 가장 많이 겪는 문제
<a id="page-48--트러블슈팅-20선-현장에서-가장-많이-겪는-문제"></a>

### 트러블슈팅 20선(요약)
1) 401 폭증: JWKS 캐시/키 로테이션/issuer 변경 확인  
2) 특정 클라이언트만 만료: 시간 동기화/clock skew  
3) aud 없음/불일치: 잘못된 토큰(ID Token 사용 등)  
4) kid unknown: JWKS 갱신 실패/캐시 TTL 과도  
5) 일부 요청만 서명 실패: 토큰 손상/헤더 truncation/프록시 제한  
6) 토큰 너무 김: 클레임 과다(권한 과다 포함)  
7) 로그에 토큰 노출: 마스킹 정책 미비  
8) 모바일 간헐적 401: 동시 refresh 경쟁(락/단일화 필요)  
9) logout 안 됨: access TTL 과도/세션 무효화 설계 부재  
10) staging 토큰이 prod 통과: iss/aud 검증 누락  
…(나머지는 체크리스트(49p)로 예방)

### 팁
- “원인별 실패 메트릭”을 만들면, 트러블슈팅 시간이 크게 줄어듭니다.



---

## Page 49 / 50 — 프로덕션 체크리스트: 배포 전 마지막 점검
<a id="page-49--프로덕션-체크리스트-배포-전-마지막-점검"></a>

### 프로덕션 체크리스트(요약)
#### 설계
- [ ] Access Token TTL은 짧다(분 단위)  
- [ ] Refresh Token 회전/재사용 감지 전략이 있다  
- [ ] 토큰에 민감정보가 없다  
- [ ] iss/aud/exp/alg 정책이 문서화되어 있다  

#### 구현
- [ ] alg 화이트리스트 강제  
- [ ] kid/jku/x5u/crit 등 헤더 기반 공격 표면 차단  
- [ ] JWKS 캐시/갱신(타임아웃/백오프/서킷브레이커)  
- [ ] 토큰/비밀값 로깅 마스킹  

#### 운영
- [ ] 키 로테이션 절차가 있다(중단 없는 전환)  
- [ ] 모니터링(서명 실패, 만료, iss/aud 불일치, kid unknown)  
- [ ] 사고 대응(키 유출/토큰 탈취) 플레이북  

### 팁
- 체크리스트를 CI 파이프라인(설정 검증/테스트)과 연결하면 실수를 줄일 수 있습니다.



---

## Page 50 / 50 — 부록: 용어집 + 참고문서(RFC/OWASP)
<a id="page-50--부록-용어집-+-참고문서(RFC-OWASP)"></a>

### 용어집(초간단)
- JWT: JSON Web Token (RFC 7519)
- JWS: 서명 포맷 (RFC 7515)
- JWE: 암호화 포맷 (RFC 7516)
- JWK/JWKS: 키 표현 / 키 세트 (RFC 7517)
- issuer(iss): 토큰 발급자
- audience(aud): 토큰 소비 대상
- bearer token: “가진 사람이 권한을 가짐”

### 참고문서(권장)
- RFC 7519 (JWT): https://datatracker.ietf.org/doc/html/rfc7519
- RFC 8725 (Best Current Practices): https://www.rfc-editor.org/rfc/rfc8725.html
- RFC 7515 (JWS): https://www.rfc-editor.org/rfc/rfc7515.html
- RFC 7516 (JWE): https://www.rfc-editor.org/rfc/rfc7516.html
- RFC 7517 (JWK): https://datatracker.ietf.org/doc/html/rfc7517
- RFC 9068 (OAuth 2.0 JWT Access Token Profile): https://datatracker.ietf.org/doc/html/rfc9068
- RFC 8414 (Authorization Server Metadata): https://datatracker.ietf.org/doc/html/rfc8414
- OWASP JWT Cheat Sheet (Java): https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html
- OWASP WSTG - Testing JWTs: https://owasp.org/www-project-web-security-testing-guide/latest/4-Web_Application_Security_Testing/06-Session_Management_Testing/10-Testing_JSON_Web_Tokens

### 마지막 한 줄
> JWT의 핵심은 “형식”이 아니라 **검증(iss/aud/exp/alg), 키 관리, 만료/회수 전략**입니다.

