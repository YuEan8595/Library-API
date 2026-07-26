package com.library.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Payload for registering a new borrower")
public record CreateBorrowerRequest(

        @Schema(example = "Ada Lovelace")
        @NotBlank(message = "name must not be blank")
        @Size(max = 255, message = "name must not exceed 255 characters")
        String name,

        @Schema(example = "ada@example.com")
        @NotBlank(message = "email must not be blank")
        @Email(message = "email must be a well-formed email address")
        @Size(max = 320, message = "email must not exceed 320 characters")
        String email
) {
}
