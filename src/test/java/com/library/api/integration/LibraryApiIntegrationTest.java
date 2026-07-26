package com.library.api.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.library.api.repository.BorrowRecordRepository;
import com.library.api.repository.BookCopyRepository;
import com.library.api.repository.BookEditionRepository;
import com.library.api.repository.BorrowerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Library API (end to end)")
class LibraryApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private BorrowRecordRepository borrowRecordRepository;
    @Autowired private BookCopyRepository bookCopyRepository;
    @Autowired private BookEditionRepository bookEditionRepository;
    @Autowired private BorrowerRepository borrowerRepository;

    @BeforeEach
    void clean() {
        borrowRecordRepository.deleteAll();
        bookCopyRepository.deleteAll();
        bookEditionRepository.deleteAll();
        borrowerRepository.deleteAll();
    }

    // ------------------------------------------------------------------ borrowers

    @Test
    @DisplayName("registers a borrower and returns 201 with a Location header")
    void registersBorrower() throws Exception {
        mockMvc.perform(post("/api/v1/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ada Lovelace", "email": "ada@example.com"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name", is("Ada Lovelace")))
                .andExpect(jsonPath("$.email", is("ada@example.com")));
    }

    @Test
    @DisplayName("rejects a duplicate email with 409")
    void rejectsDuplicateEmail() throws Exception {
        registerBorrower("Ada Lovelace", "ada@example.com");

        mockMvc.perform(post("/api/v1/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "Ada L.", "email": "ADA@example.com"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("EMAIL_ALREADY_REGISTERED")));
    }

    @Test
    @DisplayName("rejects an invalid email with 400 and a field-level breakdown")
    void rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post("/api/v1/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name": "", "email": "not-an-email"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")))
                .andExpect(jsonPath("$.violations", hasSize(2)));
    }

    // ---------------------------------------------------------------------- books

    @Test
    @DisplayName("two copies of one ISBN get different ids")
    void sameIsbnProducesDistinctIds() throws Exception {
        long first = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long second = registerBook("978-0-13-235088-4", "Clean Code", "Robert C. Martin");

        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)))
                .andExpect(jsonPath("$.content[0].isbn", is("9780132350884")))
                .andExpect(jsonPath("$.content[1].isbn", is("9780132350884")));

        org.assertj.core.api.Assertions.assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("same title and author under a different ISBN is a different book")
    void differentIsbnIsADifferentBook() throws Exception {
        registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        registerBook("9780134494166", "Clean Code", "Robert C. Martin");

        mockMvc.perform(get("/api/v1/books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(2)));
    }

    @Test
    @DisplayName("rejects a conflicting title for an existing ISBN with 409")
    void rejectsIsbnMismatch() throws Exception {
        registerBook("9780132350884", "Clean Code", "Robert C. Martin");

        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "9780132350884", "title": "The Pragmatic Programmer", "author": "Andy Hunt"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("ISBN_MISMATCH")))
                .andExpect(jsonPath("$.detail", containsString("Clean Code")));
    }

    @Test
    @DisplayName("rejects a malformed ISBN with 400")
    void rejectsMalformedIsbn() throws Exception {
        mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"isbn": "abc", "title": "Clean Code", "author": "Robert C. Martin"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode", is("VALIDATION_FAILED")));
    }

    @Test
    @DisplayName("supports search and pagination on the catalogue")
    void searchesAndPaginates() throws Exception {
        registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        registerBook("9780201616224", "The Pragmatic Programmer", "Andrew Hunt");

        mockMvc.perform(get("/api/v1/books").param("search", "pragmatic"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements", is(1)))
                .andExpect(jsonPath("$.content[0].author", is("Andrew Hunt")));

        mockMvc.perform(get("/api/v1/books").param("size", "1").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.last", is(false)));
    }

    // ------------------------------------------------------------------- lending

    @Test
    @DisplayName("borrow then return round-trips and frees the copy")
    void borrowAndReturn() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long borrowerId = registerBorrower("Ada Lovelace", "ada@example.com");

        mockMvc.perform(post("/api/v1/books/{id}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(borrowerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookId", is((int) bookId)))
                .andExpect(jsonPath("$.borrowerId", is((int) borrowerId)))
                .andExpect(jsonPath("$.returnedAt").doesNotExist());

        mockMvc.perform(get("/api/v1/books/{id}", bookId))
                .andExpect(jsonPath("$.available", is(false)));

        mockMvc.perform(post("/api/v1/books/{id}/return", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(borrowerId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.returnedAt").exists());

        mockMvc.perform(get("/api/v1/books/{id}", bookId))
                .andExpect(jsonPath("$.available", is(true)));
    }

    @Test
    @DisplayName("a copy already on loan cannot be borrowed by a second member")
    void cannotDoubleBorrowSameCopy() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");
        long grace = registerBorrower("Grace Hopper", "grace@example.com");

        borrow(bookId, ada);

        mockMvc.perform(post("/api/v1/books/{id}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(grace)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("BOOK_ALREADY_BORROWED")));
    }

    @Test
    @DisplayName("the other copy of the same ISBN is still borrowable")
    void otherCopyOfSameIsbnRemainsAvailable() throws Exception {
        long copyA = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long copyB = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");
        long grace = registerBorrower("Grace Hopper", "grace@example.com");

        borrow(copyA, ada);

        mockMvc.perform(post("/api/v1/books/{id}/borrow", copyB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(grace)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a returned copy can be borrowed again")
    void copyIsReborrowableAfterReturn() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");
        long grace = registerBorrower("Grace Hopper", "grace@example.com");

        borrow(bookId, ada);
        mockMvc.perform(post("/api/v1/books/{id}/return", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(ada)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/books/{id}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(grace)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("only the holder may return the loan")
    void onlyHolderMayReturn() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");
        long grace = registerBorrower("Grace Hopper", "grace@example.com");

        borrow(bookId, ada);

        mockMvc.perform(post("/api/v1/books/{id}/return", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(grace)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode", is("BORROWER_MISMATCH")));
    }

    @Test
    @DisplayName("returning a shelved copy is a 409")
    void returningShelvedCopyFails() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");

        mockMvc.perform(post("/api/v1/books/{id}/return", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(ada)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode", is("BOOK_NOT_BORROWED")));
    }

    @Test
    @DisplayName("borrowing an unknown book is a 404 and leaks no internals")
    void unknownBookIs404() throws Exception {
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");

        mockMvc.perform(post("/api/v1/books/{id}/borrow", 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(ada)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode", is("RESOURCE_NOT_FOUND")))
                .andExpect(jsonPath("$.detail", not(containsString("Exception"))));
    }

    @Test
    @DisplayName("a borrower can list what they currently have out")
    void listsActiveLoans() throws Exception {
        long bookId = registerBook("9780132350884", "Clean Code", "Robert C. Martin");
        long ada = registerBorrower("Ada Lovelace", "ada@example.com");
        borrow(bookId, ada);

        mockMvc.perform(get("/api/v1/borrowers/{id}/loans", ada))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].bookId", is((int) bookId)));
    }

    // ------------------------------------------------------------------- helpers

    private long registerBorrower(String name, String email) throws Exception {
        String body = mockMvc.perform(post("/api/v1/borrowers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("name", name, "email", email))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private long registerBook(String isbn, String title, String author) throws Exception {
        String body = mockMvc.perform(post("/api/v1/books")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("isbn", isbn, "title", title, "author", author))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asLong();
    }

    private void borrow(long bookId, long borrowerId) throws Exception {
        mockMvc.perform(post("/api/v1/books/{id}/borrow", bookId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(borrowerPayload(borrowerId)))
                .andExpect(status().isOk());
    }

    private String borrowerPayload(long borrowerId) {
        return "{\"borrowerId\": " + borrowerId + "}";
    }
}
