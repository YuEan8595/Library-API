package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 403 - a MEMBER token whose email does not match any registered borrower. */
public class MemberAccountNotLinkedException extends ApiException {

    public MemberAccountNotLinkedException() {
        super(HttpStatus.FORBIDDEN, "MEMBER_NOT_LINKED",
                "This account is not linked to a registered borrower; ask a librarian to register you first");
    }
}
