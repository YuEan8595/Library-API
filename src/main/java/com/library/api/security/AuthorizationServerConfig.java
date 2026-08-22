package com.library.api.security;

import com.library.api.repository.BorrowerRepository;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configuration.OAuth2AuthorizationServerConfiguration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.MediaTypeRequestMatcher;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * The app's own local OAuth2 authorization server: issues tokens so the resource-server side
 * (SecurityConfig) has something to validate, with no external identity provider dependency.
 * Dev/demo-shaped on purpose: in-memory clients, in-memory users (SecurityConfig), and an RSA
 * signing key generated fresh on every boot (so restarting the app invalidates every token
 * issued before the restart, and running more than one instance would not work - each would
 * mint and verify against its own key). A production deployment would swap this whole class
 * for either Spring Authorization Server backed by a database, or an external IdP such as
 * Keycloak/Auth0/Okta.
 */
@Configuration
public class AuthorizationServerConfig {

    @Bean
    @Order(1)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfiguration.applyDefaultSecurity(http);
        http.exceptionHandling(exceptions -> exceptions
                .defaultAuthenticationEntryPointFor(
                        new LoginUrlAuthenticationEntryPoint("/login"),
                        new MediaTypeRequestMatcher(MediaType.TEXT_HTML)));
        return http.build();
    }

    /**
     * Two clients for the two roles: a confidential client_credentials client for staff
     * tooling, and a public authorization_code+PKCE client for members logging in as
     * themselves through the AS's own login form.
     */
    @Bean
    public RegisteredClientRepository registeredClientRepository(
            PasswordEncoder passwordEncoder,
            @Value("${oauth2.staff-client-secret}") String staffClientSecret) {

        TokenSettings tokenSettings = TokenSettings.builder()
                .accessTokenTimeToLive(Duration.ofMinutes(30))
                .build();

        RegisteredClient libraryStaff = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("library-staff")
                .clientSecret(passwordEncoder.encode(staffClientSecret))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .scope("library.api")
                .tokenSettings(tokenSettings)
                .build();

        RegisteredClient libraryWeb = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("library-web")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:8080/swagger-ui/oauth2-redirect.html")
                .redirectUri("https://oauth.pstmn.io/v1/callback")
                .scope("library.api")
                .clientSettings(ClientSettings.builder()
                        .requireAuthorizationConsent(false)
                        .requireProofKey(true)
                        .build())
                .tokenSettings(tokenSettings)
                .build();

        return new InMemoryRegisteredClientRepository(libraryStaff, libraryWeb);
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource() {
        KeyPair keyPair = generateRsaKey();
        RSAKey rsaKey = new RSAKey.Builder((RSAPublicKey) keyPair.getPublic())
                .privateKey((RSAPrivateKey) keyPair.getPrivate())
                .keyID(UUID.randomUUID().toString())
                .build();
        return new ImmutableJWKSet<>(new JWKSet(rsaKey));
    }

    private static KeyPair generateRsaKey() {
        try {
            KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
            keyPairGenerator.initialize(2048);
            return keyPairGenerator.generateKeyPair();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }

    /**
     * Shared with the resource-server filter chain in SecurityConfig, so verifying a token
     * never makes a network call back into this same app (which wouldn't be accepting
     * connections yet if this ran through issuer-uri discovery at startup).
     */
    @Bean
    public JwtDecoder jwtDecoder(JWKSource<SecurityContext> jwkSource) {
        return OAuth2AuthorizationServerConfiguration.jwtDecoder(jwkSource);
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(
            @Value("${oauth2.issuer-uri}") String issuer) {
        return AuthorizationServerSettings.builder().issuer(issuer).build();
    }

    /**
     * Stamps a "roles" claim onto every access token: ["LIBRARIAN"] for the staff
     * client_credentials client, or the logged-in member's own authorities otherwise. For a
     * member it also looks their email up in BorrowerRepository and adds a "borrower_id"
     * claim when a matching borrower is on file, which is how AuthorizationHelper resolves
     * "act as yourself" without a hardcoded id mapping.
     */
    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> jwtTokenCustomizer(BorrowerRepository borrowerRepository) {
        return context -> {
            if (context.getTokenType() != OAuth2TokenType.ACCESS_TOKEN) {
                return;
            }

            if (context.getRegisteredClient() != null
                    && "library-staff".equals(context.getRegisteredClient().getClientId())) {
                context.getClaims().claim("roles", List.of("LIBRARIAN"));
                return;
            }

            Authentication principal = context.getPrincipal();
            List<String> roles = principal.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .filter(authority -> authority.startsWith("ROLE_"))
                    .map(authority -> authority.substring("ROLE_".length()))
                    .toList();
            context.getClaims().claim("roles", roles);

            borrowerRepository.findByEmailIgnoreCase(principal.getName())
                    .ifPresent(borrower -> context.getClaims().claim("borrower_id", borrower.getId()));
        };
    }
}
