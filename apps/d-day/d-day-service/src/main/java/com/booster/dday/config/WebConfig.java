package com.booster.dday.config;

import com.booster.dday.shared.web.CurrentMemberArgumentResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * {@code @CurrentMember} 를 쓸 수 있게 한다.
 *
 * <p>컨트롤러마다 {@code @RequestHeader("X-User-Id")} 를 적고 널 검사를 하면
 * <b>그 검사를 한 군데서 빠뜨리는 날 개인 자원이 열린다.</b> 해석기 하나로 모아 두면
 * 빠뜨릴 자리가 없다.
 */
@Configuration
@RequiredArgsConstructor
public class WebConfig implements WebMvcConfigurer {

    private final CurrentMemberArgumentResolver currentMemberArgumentResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentMemberArgumentResolver);
    }
}
