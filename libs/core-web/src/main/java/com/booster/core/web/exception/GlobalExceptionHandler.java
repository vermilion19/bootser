package com.booster.core.web.exception;

import com.booster.core.web.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 던진 쪽이 적어 준 메시지를 쓴다.
     *
     * <p>{@code ApiResponse.error(errorCode)} 로만 만들면 {@code CoreException} 의
     * 두 번째 인자가 <b>조용히 버려진다.</b> 그러면 "그 해는 다루지 않는다" 까지만
     * 나가고 "1583~2999 안의 해를 달라" 가 사라진다 — <b>서버가 범위를 정해 놓고
     * 안 알리는 셈</b>이다.
     *
     * <p>인자 하나짜리 생성자를 쓴 경우에는 {@code getMessage()} 가 곧 열거형의
     * 메시지이므로 달라지는 것이 없다.
     */
    @ExceptionHandler(CoreException.class)
    public ResponseEntity<ApiResponse<Void>> handleCoreException(CoreException e) {
        log.warn("CoreException : {}", e.getMessage());
        return ResponseEntity
                .status(e.getErrorCode().getStatus())
                .body(ApiResponse.error(e.getErrorCode(), e.getMessage()));
    }

    /**
     * Valid 검증 실패 예외
     * - DTO의 유효성 검사가 실패할 경우 발생
     * - 첫 번째 에러 메시지를 응답 메시지로 사용
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        BindingResult bindingResult = e.getBindingResult();
        FieldError firstError = bindingResult.getFieldError();

        String logMessage = firstError != null
                ? String.format("[%s] %s", firstError.getField(), firstError.getDefaultMessage())
                : "Validation failed";

        String clientMessage = firstError != null
                ? firstError.getDefaultMessage() // "휴대폰 번호 형식이 올바르지 않습니다" 같은 메시지 전달
                : "잘못된 요청입니다.";

        log.warn("Validation Error : {}", logMessage);

        return ResponseEntity
                .status(400)
                .body(ApiResponse.error(clientMessage)); // ApiResponse에 String 받는 메소드가 있다고 가정
    }

    /**
     * 잘못된 포맷 요청
     * - JSON 형식이 깨졌거나, 타입이 맞지 않을 때 (Enum 변환 실패 등)
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleJsonException(HttpMessageNotReadableException e) {
        log.warn("Json Parsing Error : {}", e.getMessage());
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error("요청 포맷이 올바르지 않습니다. (JSON 형식 확인)"));
    }

    /**
     * 필수 파라미터가 빠졌다 — <b>부르는 쪽의 실수인데 500 으로 나가고 있었다.</b>
     *
     * <p>{@code @RequestParam} 이 없으면 Spring 이
     * {@link MissingServletRequestParameterException} 을 던지는데, 아래
     * {@code Exception} 처리기가 그것을 받아 <b>"서버 내부 오류가 발생했습니다"</b>
     * 로 500 을 냈다. 부르는 쪽은 자기가 빠뜨린 것을 모르고 서버를 의심한다.
     *
     * <p>{@code d-day} 의 검색 주소를 세우다 드러났지만 <b>이 저장소의 모든 서비스가
     * 같은 고장을 안고 있었다.</b>
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingParameter(
            MissingServletRequestParameterException e) {

        log.warn("Missing Parameter : {}", e.getMessage());
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error(
                        "필수 파라미터가 없습니다: " + e.getParameterName()));
    }

    /**
     * 파라미터 타입이 안 맞는다. {@code ?year=올해} 가 여기 온다.
     *
     * <p>같은 이유로 500 이었다. <b>무엇이 안 맞았는지</b>를 알려 준다 — 어느
     * 파라미터인지 안 적으면 부르는 쪽이 하나씩 빼 보며 찾아야 한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(
            MethodArgumentTypeMismatchException e) {

        log.warn("Type Mismatch : {}", e.getMessage());
        return ResponseEntity
                .status(400)
                .body(ApiResponse.error(
                        "파라미터 값이 올바르지 않습니다: " + e.getName() + "=" + e.getValue()));
    }

    /**
     * 없는 주소 — <b>404 여야 하는데 500 이 나가고 있었다.</b>
     *
     * <p>Spring 이 {@link NoResourceFoundException} 을 던지는데 그것을 받는 자리가
     * 없어 아래 {@code Exception} 처리기가 «서버 내부 오류» 로 500 을 냈다.
     *
     * <p>화면을 세우다 드러났다. 링크 하나가 오타여도 <b>서버가 고장 난 것처럼
     * 보이고</b>, 크롤러와 모니터링도 그것을 우리 장애로 읽는다.
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNoResource(NoResourceFoundException e) {
        log.warn("No Resource : {}", e.getResourcePath());
        return ResponseEntity
                .status(404)
                .body(ApiResponse.error("없는 주소입니다: " + e.getResourcePath()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        log.error("Unhandled Exception : ", e);
        return ResponseEntity
                .status(500)
                .body(ApiResponse.error("서버 내부 오류가 발생했습니다."));
    }
}
