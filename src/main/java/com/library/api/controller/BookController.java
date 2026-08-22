package com.library.api.controller;

import com.library.api.dto.BookResponse;
import com.library.api.dto.BorrowRequest;
import com.library.api.dto.CreateBookRequest;
import com.library.api.dto.LoanResponse;
import com.library.api.dto.PageResponse;
import com.library.api.security.AuthorizationHelper;
import com.library.api.service.BookService;
import com.library.api.service.LendingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/api/v1/books")
@Tag(name = "Books", description = "Register books and lend them out")
public class BookController {

    private final BookService bookService;
    private final LendingService lendingService;
    private final AuthorizationHelper authorizationHelper;

    public BookController(BookService bookService, LendingService lendingService,
                          AuthorizationHelper authorizationHelper) {
        this.bookService = bookService;
        this.lendingService = lendingService;
        this.authorizationHelper = authorizationHelper;
    }

    @PostMapping
    @Operation(summary = "Register a new book copy",
            description = "Posting an ISBN that already exists adds another copy with a new id. "
                    + "The title and author must match what is already on file for that ISBN.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Copy registered"),
            @ApiResponse(responseCode = "400", description = "Validation failed", content = @Content),
            @ApiResponse(responseCode = "409", description = "ISBN registered with a different title/author", content = @Content)
    })
    public ResponseEntity<BookResponse> register(@Valid @RequestBody CreateBookRequest request,
                                                 UriComponentsBuilder uriBuilder) {
        BookResponse created = bookService.register(request);
        return ResponseEntity
                .created(uriBuilder.path("/api/v1/books/{id}").buildAndExpand(created.id()).toUri())
                .body(created);
    }

    @GetMapping
    @Operation(summary = "List all books in the library",
            description = "Every physical copy is returned, each with its own id and availability flag.")
    public PageResponse<BookResponse> list(
            @Parameter(description = "Optional free-text filter over title, author or exact ISBN")
            @RequestParam(required = false) String search,
            @ParameterObject @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        return bookService.findAll(search, pageable);
    }

    @GetMapping("/{bookId}")
    @Operation(summary = "Fetch a single book copy")
    public BookResponse getOne(@PathVariable Long bookId) {
        return bookService.findById(bookId);
    }

    @PostMapping("/{bookId}/borrow")
    @Operation(summary = "Borrow a book on behalf of a borrower",
            description = "Fails with 409 if this copy is already on loan to anyone.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan created"),
            @ApiResponse(responseCode = "404", description = "Book or borrower not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Copy is already borrowed", content = @Content)
    })
    public LoanResponse borrow(@PathVariable Long bookId, @Valid @RequestBody BorrowRequest request,
                               @AuthenticationPrincipal Jwt jwt) {
        Long borrowerId = authorizationHelper.resolveBorrowerId(jwt, request.borrowerId());
        return lendingService.borrow(bookId, borrowerId);
    }

    @PostMapping("/{bookId}/return")
    @Operation(summary = "Return a borrowed book on behalf of a borrower",
            description = "Only the borrower holding the active loan may return it.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Loan closed"),
            @ApiResponse(responseCode = "403", description = "Loan is held by a different borrower", content = @Content),
            @ApiResponse(responseCode = "404", description = "Book or borrower not found", content = @Content),
            @ApiResponse(responseCode = "409", description = "Copy is not currently borrowed", content = @Content)
    })
    public LoanResponse returnBook(@PathVariable Long bookId, @Valid @RequestBody BorrowRequest request,
                                   @AuthenticationPrincipal Jwt jwt) {
        Long borrowerId = authorizationHelper.resolveBorrowerId(jwt, request.borrowerId());
        return lendingService.returnBook(bookId, borrowerId);
    }
}
