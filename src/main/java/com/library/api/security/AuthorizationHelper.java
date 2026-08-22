package com.library.api.security;

import com.library.api.exception.BorrowerAccessDeniedException;
import com.library.api.exception.BorrowerIdRequiredException;
import com.library.api.exception.MemberAccountNotLinkedException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Bridges the two roles onto the borrower id the service layer works with.
 * A LIBRARIAN acts "on behalf of" whichever borrowerId the request body names; a MEMBER
 * can only ever act as themselves, identified by the borrower_id claim the authorization
 * server stamped onto their token (see AuthorizationServerConfig's token customizer).
 */
@Component
public class AuthorizationHelper {

    private static final String ROLES_CLAIM = "roles";
    private static final String BORROWER_ID_CLAIM = "borrower_id";

    /** Resolves the borrowerId a borrow/return should act on, ignoring a MEMBER's request body. */
    public Long resolveBorrowerId(Jwt jwt, Long bodyBorrowerId) {
        if (isLibrarian(jwt)) {
            if (bodyBorrowerId == null) {
                throw new BorrowerIdRequiredException();
            }
            return bodyBorrowerId;
        }
        return ownBorrowerId(jwt);
    }

    /** Guards a read of a specific borrower's data: librarians may read any, members only their own. */
    public void requireOwnerOrLibrarian(Jwt jwt, Long borrowerId) {
        if (isLibrarian(jwt)) {
            return;
        }
        if (!ownBorrowerId(jwt).equals(borrowerId)) {
            throw new BorrowerAccessDeniedException(borrowerId);
        }
    }

    private Long ownBorrowerId(Jwt jwt) {
        // Fetched as Number, not Long: the JWT claim is decoded from JSON and different
        // decoders may hand back an Integer or a Long for the same numeric claim.
        Number borrowerId = jwt.getClaim(BORROWER_ID_CLAIM);
        if (borrowerId == null) {
            throw new MemberAccountNotLinkedException();
        }
        return borrowerId.longValue();
    }

    private boolean isLibrarian(Jwt jwt) {
        return jwt.getClaimAsStringList(ROLES_CLAIM) != null
                && jwt.getClaimAsStringList(ROLES_CLAIM).contains("LIBRARIAN");
    }
}
