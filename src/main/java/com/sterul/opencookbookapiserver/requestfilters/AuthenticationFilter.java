package com.sterul.opencookbookapiserver.requestfilters;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;

import javax.servlet.FilterChain;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sterul.opencookbookapiserver.Constants;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;

public class AuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    
    private AuthenticationManager authenticationManager;

    public AuthenticationFilter(AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        setFilterProcessesUrl("/login");
    }

    static class LoginRequest {
        public String emailAddress;
        public String password;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {

                try {
                    LoginRequest loginRequest = new ObjectMapper()
                        .readValue(request.getInputStream(), com.sterul.opencookbookapiserver.requestfilters.AuthenticationFilter.LoginRequest.class);
                    return authenticationManager.authenticate(
                        new UsernamePasswordAuthenticationToken(loginRequest.emailAddress, loginRequest.password, new ArrayList<>()));
                } catch (IOException e) {
                    throw new RuntimeException("Could not read login request." + e);
                }
        
    }

    @Override
    protected void successfulAuthentication(HttpServletRequest request, HttpServletResponse response, FilterChain FilterChain, Authentication authentication) {
        String jsonWebToken = Jwts.builder()
            .setSubject(((User) authentication.getPrincipal()).getUsername())
            .setExpiration(new Date(System.currentTimeMillis() + 864_000_000))
            .signWith(SignatureAlgorithm.HS256, Constants.JWTS_SIGNING_KEY.getBytes())
            .compact();
        response.addHeader("Authorization", "Bearer " + jsonWebToken);
    }

    

}
