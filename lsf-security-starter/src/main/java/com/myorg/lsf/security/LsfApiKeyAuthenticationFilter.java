package com.myorg.lsf.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.util.StringUtils;

import java.io.IOException;

public class LsfApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final LsfSecurityProperties.ApiKey properties;

    public LsfApiKeyAuthenticationFilter(LsfSecurityProperties.ApiKey properties) {
        this.properties = properties;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String headerValue = request.getHeader(properties.getHeaderName());
        if (!StringUtils.hasText(headerValue)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!headerValue.equals(properties.getValue())) {
            SecurityContextHolder.clearContext();
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid API key");
            return;
        }

        if (SecurityContextHolder.getContext().getAuthentication() == null) {
            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(
                    properties.getPrincipal(),
                    "N/A",
                    properties.getAuthorities().stream()
                            .map(SimpleGrantedAuthority::new)
                            .toList()
            );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }

        filterChain.doFilter(request, response);
    }
}
