package fr.isep.simrecrut.i18n;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Locale;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration(proxyBeanMethods = false)
public class ApiLanguageConfiguration {
    @Bean public LocaleResolver localeResolver() {
        return new AcceptHeaderLocaleResolver() {
            @Override public Locale resolveLocale(HttpServletRequest request) {
                return ApiMessages.resolveLocale(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE));
            }
        };
    }
}
