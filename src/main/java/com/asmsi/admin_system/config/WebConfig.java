package com.asmsi.admin_system.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.TimeUnit;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Profile pictures saved by ProfileController. File names are unique, so they can be cached.
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:uploads/")
                .setCacheControl(CacheControl.maxAge(30, TimeUnit.DAYS).cachePrivate());
    }

    /**
     * The sidebar navigation (static/js/app-navigation.js) swaps only the page body.
     * When it asks for a page, render just the {@code th:fragment="page"} block instead of
     * the whole document, so every page is smaller and faster to render. Each app page
     * marks its &lt;main&gt; with th:fragment="page".
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new HandlerInterceptor() {
            @Override
            public void postHandle(HttpServletRequest request, HttpServletResponse response,
                                   Object handler, ModelAndView mav) {
                if (mav == null || !"page".equals(request.getHeader("X-App-Navigation"))) return;
                String view = mav.getViewName();
                if (view == null || view.startsWith("redirect:") || view.startsWith("forward:") || view.contains("::")) return;
                mav.setViewName(view + " :: page");
                response.setHeader("Vary", "X-App-Navigation");
            }
        }).addPathPatterns("/**").excludePathPatterns("/css/**", "/js/**", "/images/**", "/vendor/**", "/webjars/**", "/uploads/**");
    }
}
