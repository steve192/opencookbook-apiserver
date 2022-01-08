package com.sterul.opencookbookapiserver.services;

import java.time.Instant;
import java.util.UUID;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.User;
import com.sterul.opencookbookapiserver.repositories.RefreshTokenRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OpencookbookConfiguration opencookbookConfiguration;

    public RefreshToken createRefreshTokenForUser(User user) {
        var refreshToken = new RefreshToken();
        refreshToken.setOwner(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken
                .setValidUntil(Instant.now().plusMillis(opencookbookConfiguration.getRefreshTokenDuration() * 1000));

        return refreshTokenRepository.save(refreshToken);
    }

    public boolean isTokenValid(String refreshToken) {
        var foundToken = refreshTokenRepository.findById(refreshToken);
        if (foundToken.isEmpty()) {
            return false;
        }

        if (foundToken.get().getValidUntil().isBefore(Instant.now())) {
            refreshTokenRepository.delete(foundToken.get());
            return false;
        }

        return true;
    }

    public RefreshToken getRefreshToken(String refreshToken) throws ElementNotFound {
        var token = refreshTokenRepository.findById(refreshToken);
        if (token.isEmpty()) {
            throw new ElementNotFound();
        }

        return token.get();
    }

    @Transactional
    public void deleteAllRefreshTokenForUser(User user) {
        refreshTokenRepository.deleteAllByOwner(user);
    }
}
