package com.example.backend.auth.application.port.out;

import com.example.backend.auth.domain.model.AccessToken;
import com.example.backend.user.domain.model.User;
import java.time.Duration;

public interface AccessTokenIssuerPort {

    AccessToken issue(User user);

    Duration refreshTokenTtl();
}
