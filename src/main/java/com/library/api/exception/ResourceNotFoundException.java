package com.library.api.exception;

import org.springframework.http.HttpStatus;

/** 404 - the referenced borrower or book does not exist. */
public class ResourceNotFoundException extends ApiException {

    public ResourceNotFoundException(String resource, Object id) {
        super(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", resource + " with id " + id + " was not found");
    }
}
