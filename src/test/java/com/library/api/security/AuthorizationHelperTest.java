package com.library.api.security;

import com.library.api.exception.BorrowerAccessDeniedException;
import com.library.api.exception.BorrowerIdRequiredException;
import com.library.api.exception.MemberAccountNotLinkedException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AuthorizationHelper")
class AuthorizationHelperTest {

    private final AuthorizationHelper authorizationHelper = new AuthorizationHelper();

    @Test
    @DisplayName("resolveBorrowerId: a LIBRARIAN's body borrowerId is used as-is")
    void librarianBorrowerIdComesFromBody() {
        Jwt librarian = jwt("LIBRARIAN", null);

        assertThat(authorizationHelper.resolveBorrowerId(librarian, 42L)).isEqualTo(42L);
    }

    @Test
    @DisplayName("resolveBorrowerId: a LIBRARIAN omitting borrowerId is rejected")
    void librarianMustSupplyBorrowerId() {
        Jwt librarian = jwt("LIBRARIAN", null);

        assertThatThrownBy(() -> authorizationHelper.resolveBorrowerId(librarian, null))
                .isInstanceOf(BorrowerIdRequiredException.class);
    }

    @Test
    @DisplayName("resolveBorrowerId: a MEMBER's own token id is used, ignoring the request body")
    void memberBorrowerIdComesFromTokenNotBody() {
        Jwt member = jwt("MEMBER", 99L);

        // A body borrowerId belonging to someone else must have no effect.
        assertThat(authorizationHelper.resolveBorrowerId(member, 12345L)).isEqualTo(99L);
        assertThat(authorizationHelper.resolveBorrowerId(member, null)).isEqualTo(99L);
    }

    @Test
    @DisplayName("resolveBorrowerId: a MEMBER token with no matching borrower is rejected")
    void memberWithoutBorrowerIdClaimIsRejected() {
        Jwt unlinkedMember = jwt("MEMBER", null);

        assertThatThrownBy(() -> authorizationHelper.resolveBorrowerId(unlinkedMember, 1L))
                .isInstanceOf(MemberAccountNotLinkedException.class);
    }

    @Test
    @DisplayName("requireOwnerOrLibrarian: a LIBRARIAN may access any borrower")
    void librarianMayAccessAnyBorrower() {
        Jwt librarian = jwt("LIBRARIAN", null);

        authorizationHelper.requireOwnerOrLibrarian(librarian, 777L);
    }

    @Test
    @DisplayName("requireOwnerOrLibrarian: a MEMBER may access only their own record")
    void memberMayAccessOnlyOwnRecord() {
        Jwt member = jwt("MEMBER", 99L);

        authorizationHelper.requireOwnerOrLibrarian(member, 99L);
        assertThatThrownBy(() -> authorizationHelper.requireOwnerOrLibrarian(member, 100L))
                .isInstanceOf(BorrowerAccessDeniedException.class);
    }

    private static Jwt jwt(String role, Long borrowerId) {
        Map<String, Object> claims = new java.util.HashMap<>();
        claims.put("roles", List.of(role));
        if (borrowerId != null) {
            claims.put("borrower_id", borrowerId);
        }
        return Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
