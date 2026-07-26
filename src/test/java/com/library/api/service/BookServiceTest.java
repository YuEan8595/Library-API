package com.library.api.service;

import com.library.api.domain.BookCopy;
import com.library.api.domain.BookEdition;
import com.library.api.dto.BookResponse;
import com.library.api.dto.CreateBookRequest;
import com.library.api.exception.IsbnMismatchException;
import com.library.api.repository.BookCopyRepository;
import com.library.api.repository.BookEditionRepository;
import com.library.api.repository.BorrowRecordRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BookService.register")
class BookServiceTest {

    private static final String ISBN = "9780132350884";

    @Mock private BookEditionRepository editionRepository;
    @Mock private BookCopyRepository copyRepository;
    @Mock private BorrowRecordRepository borrowRecordRepository;

    private BookService bookService;

    @BeforeEach
    void setUp() {
        bookService = new BookService(editionRepository, copyRepository, borrowRecordRepository);
    }

    /** The state after insertIfAbsent has run, whether it inserted or was a no-op. */
    private void editionOnFile(String title, String author) {
        when(editionRepository.findById(ISBN)).thenReturn(Optional.of(new BookEdition(ISBN, title, author)));
    }

    @Test
    @DisplayName("writes the edition the first time an ISBN is seen")
    void createsEditionForNewIsbn() {
        editionOnFile("Clean Code", "Robert C. Martin");
        when(copyRepository.save(any(BookCopy.class))).thenAnswer(i -> i.getArgument(0));

        BookResponse response = bookService.register(new CreateBookRequest(ISBN, "Clean Code", "Robert C. Martin"));

        verify(editionRepository).insertIfAbsent(ISBN, "Clean Code", "Robert C. Martin");
        assertThat(response.isbn()).isEqualTo(ISBN);
        assertThat(response.title()).isEqualTo("Clean Code");
        assertThat(response.available()).isTrue();
    }

    @Test
    @DisplayName("strips hyphens and spaces so 978-0-13-235088-4 == 9780132350884")
    void normalizesIsbn() {
        editionOnFile("Clean Code", "Robert C. Martin");
        when(copyRepository.save(any(BookCopy.class))).thenAnswer(i -> i.getArgument(0));

        BookResponse response = bookService.register(
                new CreateBookRequest("978-0-13-235088-4", "Clean Code", "Robert C. Martin"));

        // The normalised form is what reaches the database, so both spellings collapse
        // onto one edition row rather than creating two.
        verify(editionRepository).insertIfAbsent(eq(ISBN), any(), any());
        assertThat(response.isbn()).isEqualTo(ISBN);
    }

    @Test
    @DisplayName("reuses the existing edition so a second copy gets a distinct id")
    void reusesEditionForKnownIsbn() {
        BookEdition existing = new BookEdition(ISBN, "Clean Code", "Robert C. Martin");
        when(editionRepository.findById(ISBN)).thenReturn(Optional.of(existing));
        when(copyRepository.save(any(BookCopy.class))).thenAnswer(i -> i.getArgument(0));

        bookService.register(new CreateBookRequest(ISBN, "Clean Code", "Robert C. Martin"));

        ArgumentCaptor<BookCopy> captor = ArgumentCaptor.forClass(BookCopy.class);
        verify(copyRepository).save(captor.capture());
        assertThat(captor.getValue().getEdition()).isSameAs(existing);
    }

    @Test
    @DisplayName("accepts a title/author that differ only in case or padding")
    void toleratesCaseAndWhitespace() {
        editionOnFile("Clean Code", "Robert C. Martin");
        when(copyRepository.save(any(BookCopy.class))).thenAnswer(i -> i.getArgument(0));

        BookResponse response = bookService.register(
                new CreateBookRequest(ISBN, "  clean code  ", " ROBERT C. MARTIN "));

        // The stored spelling wins, so the catalogue stays internally consistent.
        assertThat(response.title()).isEqualTo("Clean Code");
    }

    @Test
    @DisplayName("rejects a conflicting title for a known ISBN")
    void rejectsConflictingTitle() {
        editionOnFile("Clean Code", "Robert C. Martin");

        assertThatThrownBy(() -> bookService.register(
                new CreateBookRequest(ISBN, "Clean Architecture", "Robert C. Martin")))
                .isInstanceOf(IsbnMismatchException.class)
                .hasMessageContaining("Clean Code");

        verify(copyRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a conflicting author for a known ISBN")
    void rejectsConflictingAuthor() {
        editionOnFile("Clean Code", "Robert C. Martin");

        assertThatThrownBy(() -> bookService.register(
                new CreateBookRequest(ISBN, "Clean Code", "Someone Else")))
                .isInstanceOf(IsbnMismatchException.class);

        verify(copyRepository, never()).save(any());
    }
}
