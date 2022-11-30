package com.sterul.opencookbookapiserver.configurations.security.requestfilters;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.sterul.opencookbookapiserver.util.JwtTokenUtil;

import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class JwtRequestFilter extends OncePerRequestFilter {

    @Autowired
    private UserDetailsService jwtUserDetailsService;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        tryAuthentication(request);

        chain.doFilter(request, response);
    }

    private void tryAuthentication(HttpServletRequest request) {
        final String requestTokenHeader = request.getHeader("Authorization");
        if (requestTokenHeader == null || !requestTokenHeader.startsWith("Bearer ")) {
            return;
        }

        var jwtToken = requestTokenHeader.substring(7);
        try {
            if (!jwtTokenUtil.isTokenValid(jwtToken)) {
                return;
            }

            var username = jwtTokenUtil.getUsernameFromToken(jwtToken);
            var userDetails = this.jwtUserDetailsService.loadUserByUsername(username);

            UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities());

            usernamePasswordAuthenticationToken
                    .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            // Tell spring security that we are now authenticated
            SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);

        } catch (IllegalArgumentException e) {
            logger.warn("JWT token is invalid");
        } catch (ExpiredJwtException e) {
            logger.info("JWT Token has expired");
        }
    }

}