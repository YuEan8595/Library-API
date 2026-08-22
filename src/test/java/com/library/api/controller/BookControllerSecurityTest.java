package com.library.api.controller;

import com.library.api.dto.BookResponse;
import com.library.api.dto.LoanResponse;
import com.library.api.security.AuthorizationHelper;
import com.library.api.security.SecurityConfig;
import com.library.api.service.BookService;
import com.library.api.service.LendingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Exercises the resource-server security rules on BookController, wired up exactly as
 * production does (real SecurityConfig + real AuthorizationHelper, and the real
 * JwtAuthenticationConverter so the "roles" claim -> ROLE_* mapping is genuinely tested) with a
 * stubbed JwtDecoder, since SecurityMockMvcRequestPostProcessors.jwt() injects an
 * already-authenticated token and never asks the decoder to verify anything.
 */
@WebMvcTest(BookController.class)
@Import({SecurityConfig.class, AuthorizationHelper.class})
class BookControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    @MockBean
    private BookService bookService;
    @MockBean
    private LendingService lendingService;
    @MockBean
    private JwtDecoder jwtDecoder;

    private static final String CREATE_BOOK_JSON =
            "{\"isbn\":\"9780132350884\",\"title\":\"Clean Code\",\"author\":\"Robert C. Martin\"}";

    private JwtRequestPostProcessor tokenWithRoleAndBorrowerId(String role, Integer borrowerId) {
        JwtRequestPostProcessor processor = jwt().jwt(j -> {
            j.claim("roles", List.of(role));
            if (borrowerId != null) {
                j.claim("borrower_id", borrowerId);
            }
        });
        return processor.authorities(jwtGrantedAuthoritiesConverter);
    }

    @Test
    @DisplayName("anonymous requests are rejected with 401")
    void anonymousRejected() throws Exception {
        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a MEMBER cannot register a book")
    void memberCannotRegisterBook() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .with(tokenWithRoleAndBorrowerId("MEMBER", 99))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BOOK_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a LIBRARIAN can register a book")
    void librarianCanRegisterBook() throws Exception {
        when(bookService.register(any()))
                .thenReturn(new BookResponse(1L, "9780132350884", "Clean Code", "Robert C. Martin", true));

        mockMvc.perform(post("/api/v1/books")
                        .with(tokenWithRoleAndBorrowerId("LIBRARIAN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CREATE_BOOK_JSON))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a MEMBER's borrow always uses their own token borrower id, never the request body")
    void memberBorrowIgnoresBodyBorrowerId() throws Exception {
        LoanResponse loan = new LoanResponse(1L, 7L, "9780132350884", "Clean Code",
                99L, "Ada Lovelace", Instant.now(), null);
        when(lendingService.borrow(eq(7L), eq(99L))).thenReturn(loan);

        mockMvc.perform(post("/api/v1/books/7/borrow")
                        .with(tokenWithRoleAndBorrowerId("MEMBER", 99))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"borrowerId\": 12345}"))
                .andExpect(status().isOk());

        verify(lendingService).borrow(7L, 99L);
        verify(lendingService, never()).borrow(eq(7L), eq(12345L));
    }

    @Test
    @DisplayName("a LIBRARIAN's borrow must supply a borrowerId in the body")
    void librarianBorrowRequiresBorrowerIdInBody() throws Exception {
        mockMvc.perform(post("/api/v1/books/7/borrow")
                        .with(tokenWithRoleAndBorrowerId("LIBRARIAN", null))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
