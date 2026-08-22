package com.library.api.controller;

import com.library.api.dto.BorrowerResponse;
import com.library.api.dto.CreateBorrowerRequest;
import com.library.api.dto.LoanResponse;
import com.library.api.dto.PageResponse;
import com.library.api.security.AuthorizationHelper;
import com.library.api.service.BorrowerService;
import com.library.api.service.LendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@RestController
@RequestMapping("/api/v1/borrowers")
@Tag(name = "Borrowers", description = "Register and inspect library members")
public class BorrowerController {

    private final BorrowerService borrowerService;
    private final LendingService lendingService;
    private final AuthorizationHelper authorizationHelper;

    public BorrowerController(BorrowerService borrowerService, LendingService lendingService,
                              AuthorizationHelper authorizationHelper) {
        this.borrowerService = borrowerService;
        this.lendingService = lendingService;
        this.authorizationHelper = authorizationHelper;
    }

    @PostMapping
    @Operation(summary = "Register a new borrower")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Borrower registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @io.swagger.v3.oas.annotations.media.Content),
            @ApiResponse(responseCode = "409", description = "Email already registered", content = @io.swagger.v3.oas.annotations.media.Content)
    })
    public ResponseEntity<BorrowerResponse> register(@Valid @RequestBody CreateBorrowerRequest request,
                                                     UriComponentsBuilder uriBuilder) {
        BorrowerResponse created = borrowerService.register(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/borrowers/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping("/{borrowerId}")
    @Operation(summary = "Fetch a single borrower")
    public BorrowerResponse getOne(@PathVariable Long borrowerId, @AuthenticationPrincipal Jwt jwt) {
        authorizationHelper.requireOwnerOrLibrarian(jwt, borrowerId);
        return borrowerService.findById(borrowerId);
    }

    @GetMapping
    @Operation(summary = "List borrowers")
    public PageResponse<BorrowerResponse> list(@ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return borrowerService.findAll(pageable);
    }

    @GetMapping("/{borrowerId}/loans")
    @Operation(summary = "List the books this borrower currently has out")
    public List<LoanResponse> activeLoans(@PathVariable Long borrowerId, @AuthenticationPrincipal Jwt jwt) {
        authorizationHelper.requireOwnerOrLibrarian(jwt, borrowerId);
        return lendingService.activeLoansOf(borrowerId);
    }
}
