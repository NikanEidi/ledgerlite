package dev.nikan.ledgerlite.auth;

import dev.nikan.ledgerlite.config.JwtProperties;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class JwtService {

    private static final MacAlgorithm ALGORITHM = MacAlgorithm.HS256;

    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;

    public JwtService(JwtProperties jwtProperties) {
        SecretKey key = new SecretKeySpec(
                jwtProperties.getSecretKey().getBytes(StandardCharsets.UTF_8),
                ALGORITHM.getName()
        );

        this.jwtEncoder = NimbusJwtEncoder.withSecretKey(key).build();
        this.jwtDecoder = NimbusJwtDecoder.withSecretKey(key).build();
    }

    public String generateToken(String subjectEmail) {
        Instant now = Instant.now();

        JwsHeader header = JwsHeader.with(ALGORITHM).build();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("ledgerlite")
                .issuedAt(now)
                .expiresAt(now.plus(1, ChronoUnit.HOURS))
                .subject(subjectEmail)
                .build();

        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    public Jwt decodeToken(String token) {
        return jwtDecoder.decode(token);
    }
}