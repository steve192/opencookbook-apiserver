package com.sterul.opencookbookapiserver.services;

import java.time.Instant;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sterul.opencookbookapiserver.configurations.OpencookbookConfiguration;
import com.sterul.opencookbookapiserver.entities.RefreshToken;
import com.sterul.opencookbookapiserver.entities.account.CookpalUser;
import com.sterul.opencookbookapiserver.repositories.RefreshTokenRepository;
import com.sterul.opencookbookapiserver.services.exceptions.ElementNotFound;

@Service
@Transactional
public class RefreshTokenService {

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private OpencookbookConfiguration opencookbookConfiguration;

    public RefreshToken createRefreshTokenForUser(CookpalUser user) {
        //Delete all old tokens
        refreshTokenRepository.deleteAllByValidUntilBeforeAndOwner(Instant.now(), user);

        var refreshToken = new RefreshToken();
        refreshToken.setOwner(user);
        refreshToken.setToken(UUID.randomUUID().toString());
        refreshToken
                .setValidUntil(Instant.now().plusMillis(opencookbookConfiguration.getRefreshTokenDuration() * 1000));

        return refreshTokenRepository.save(refreshToken);
    }

    public boolean isTokenValid(String refreshToken) {
        if (refreshToken == null) {
            return false;
        }
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
    public void deleteAllRefreshTokenForUser(CookpalUser user) {
        refreshTokenRepository.deleteAllByOwner(user);
    }
}
