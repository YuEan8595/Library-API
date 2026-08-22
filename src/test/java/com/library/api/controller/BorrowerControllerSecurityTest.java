package com.library.api.controller;

import com.library.api.dto.BorrowerResponse;
import com.library.api.dto.PageResponse;
import com.library.api.security.AuthorizationHelper;
import com.library.api.security.SecurityConfig;
import com.library.api.service.BorrowerService;
import com.library.api.service.LendingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Exercises the resource-server security rules on BorrowerController, same approach as
 * BookControllerSecurityTest: real SecurityConfig + real AuthorizationHelper + real
 * JwtAuthenticationConverter, stubbed JwtDecoder. */
@WebMvcTest(BorrowerController.class)
@Import({SecurityConfig.class, AuthorizationHelper.class})
class BorrowerControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtGrantedAuthoritiesConverter jwtGrantedAuthoritiesConverter;

    @MockBean
    private BorrowerService borrowerService;
    @MockBean
    private LendingService lendingService;
    @MockBean
    private JwtDecoder jwtDecoder;

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
    @DisplayName("a MEMBER cannot list all borrowers")
    void memberCannotListBorrowers() throws Exception {
        mockMvc.perform(get("/api/v1/borrowers")
                        .with(tokenWithRoleAndBorrowerId("MEMBER", 99)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("a LIBRARIAN can list all borrowers")
    void librarianCanListBorrowers() throws Exception {
        when(borrowerService.findAll(any()))
                .thenReturn(new PageResponse<>(List.of(), 0, 20, 0, 0, true));

        mockMvc.perform(get("/api/v1/borrowers")
                        .with(tokenWithRoleAndBorrowerId("LIBRARIAN", null)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a MEMBER can read their own borrower record")
    void memberCanReadOwnRecord() throws Exception {
        when(borrowerService.findById(99L)).thenReturn(new BorrowerResponse(99L, "Ada Lovelace", "ada@example.com"));

        mockMvc.perform(get("/api/v1/borrowers/99")
                        .with(tokenWithRoleAndBorrowerId("MEMBER", 99)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a MEMBER cannot read another borrower's record")
    void memberCannotReadOthersRecord() throws Exception {
        mockMvc.perform(get("/api/v1/borrowers/5")
                        .with(tokenWithRoleAndBorrowerId("MEMBER", 99)))
                .andExpect(status().isForbidden());
    }
}
