package com.booster.dday.config;

import com.booster.dday.shared.web.AdminOnlyInterceptor;
import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * {@code @CurrentMember} 를 쓸 수 있게 한다.
 *
 * <p>컨트롤러마다 {@code @RequestHeader("X-User-Id")} 를 적고 널 검사를 하면
 * <b>그 검사를 한 군데서 빠뜨리는 날 개인 자원이 열린다.</b> 해석기 하나로 모아 두면
 * 빠뜨릴 자리가 없다.
 *
 * <p>{@code @AdminOnly} 도 여기서 붙는다. 그쪽은 <b>부를 수 있는지를 정하는</b>
 * 일이라 인자가 아니라 인터셉터다 — 인자로 두면 그 인자를 안 적은 메서드가
 * 검사 없이 열린다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;
    private final AdminOnlyInterceptor adminOnlyInterceptor;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        /* 경로로 좁히지 않는다. 애노테이션이 붙은 핸들러만 검사하므로 경로 목록을
           따로 두면 그 목록과 애노테이션이 갈라질 수 있다 */
        registry.addInterceptor(adminOnlyInterceptor);
    }
}
