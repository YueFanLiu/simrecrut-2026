package fr.isep.simrecrut.i18n;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Runs before security so authentication failures share the requested language. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ApiLanguageFilter extends OncePerRequestFilter {
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        LocaleContext previous = LocaleContextHolder.getLocaleContext();
        Locale locale = ApiMessages.resolveLocale(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
        LocaleContextHolder.setLocale(locale);
        headers(response, locale);
        try { chain.doFilter(request, response); }
        finally { LocaleContextHolder.setLocaleContext(previous); }
    }

    public static void headers(HttpServletResponse response, Locale locale) {
        response.setHeader(HttpHeaders.CONTENT_LANGUAGE, locale.toLanguageTag());
        boolean varies = response.getHeaders(HttpHeaders.VARY).stream()
                .flatMap(value -> Arrays.stream(value.split(",")))
                .anyMatch(value -> value.trim().equalsIgnoreCase(HttpHeaders.ACCEPT_LANGUAGE));
        if (!varies) response.addHeader(HttpHeaders.VARY, HttpHeaders.ACCEPT_LANGUAGE);
    }
}
