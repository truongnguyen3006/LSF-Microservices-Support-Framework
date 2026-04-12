package com.myorg.lsf.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

final class LsfGrantedAuthoritiesExtractor {

    private LsfGrantedAuthoritiesExtractor() {
    }

    static Collection<GrantedAuthority> extract(Jwt jwt, LsfSecurityProperties.Jwt properties) {
        Set<String> values = new LinkedHashSet<>();
        Object claim = jwt.getClaims().get(properties.getAuthoritiesClaim());
        if (claim instanceof String text) {
            for (String part : text.split("[,\\s]+")) {
                if (StringUtils.hasText(part)) {
                    values.add(part.trim());
                }
            }
        } else if (claim instanceof Collection<?> collection) {
            for (Object item : collection) {
                if (item != null && StringUtils.hasText(item.toString())) {
                    values.add(item.toString().trim());
                }
            }
        }

        String prefix = properties.getAuthorityPrefix() == null ? "" : properties.getAuthorityPrefix();
        return values.stream()
                .map(value -> value.startsWith(prefix) ? value : prefix + value)
                .map(SimpleGrantedAuthority::new)
                .map(GrantedAuthority.class::cast)
                .toList();
    }
}
