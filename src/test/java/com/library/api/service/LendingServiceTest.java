package com.library.api.service;

import com.library.api.domain.BookCopy;
import com.library.api.domain.BookEdition;
import com.library.api.domain.BorrowRecord;
import com.library.api.domain.Borrower;
import com.library.api.dto.LoanResponse;
import com.library.api.exception.BookAlreadyBorrowedException;
import com.library.api.exception.BookNotBorrowedException;
import com.library.api.exception.BorrowerMismatchException;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookCopyRepository;
import com.library.api.repository.BorrowRecordRepository;
import com.library.api.repository.BorrowerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LendingService")
class LendingServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:15:30Z");
    private static final Long BOOK_ID = 42L;
    private static final Long BORROWER_ID = 7L;

    @Mock private BookCopyRepository copyRepository;
    @Mock private BorrowerRepository borrowerRepository;
    @Mock private BorrowRecordRepository borrowRecordRepository;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private LendingService lendingService;

    private BookCopy copy;
    private Borrower borrower;

    @BeforeEach
    void setUp() {
        lendingService = new LendingService(copyRepository, borrowerRepository, borrowRecordRepository, clock);

        BookEdition edition = new BookEdition("9780132350884", "Clean Code", "Robert C. Martin");
        copy = new BookCopy(edition);
        ReflectionTestUtils.setField(copy, "id", BOOK_ID);

        borrower = new Borrower("Ada Lovelace", "ada@example.com");
        ReflectionTestUtils.setField(borrower, "id", BORROWER_ID);
    }

    @Nested
    @DisplayName("borrow")
    class Borrow {

        @Test
        @DisplayName("creates a loan when the copy is on the shelf")
        void createsLoanForAvailableCopy() {
            when(borrowerRepository.findById(BORROWER_ID)).thenReturn(Optional.of(borrower));
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID)).thenReturn(Optional.empty());
            when(borrowRecordRepository.saveAndFlush(any(BorrowRecord.class))).thenAnswer(i -> i.getArgument(0));

            LoanResponse response = lendingService.borrow(BOOK_ID, BORROWER_ID);

            ArgumentCaptor<BorrowRecord> captor = ArgumentCaptor.forClass(BorrowRecord.class);
            verify(borrowRecordRepository).saveAndFlush(captor.capture());

            assertThat(captor.getValue().getBorrowedAt()).isEqualTo(NOW);
            assertThat(captor.getValue().getReturnedAt()).isNull();
            assertThat(response.bookId()).isEqualTo(BOOK_ID);
            assertThat(response.borrowerId()).isEqualTo(BORROWER_ID);
            assertThat(response.returnedAt()).isNull();
        }

        @Test
        @DisplayName("locks the copy row before checking availability")
        void locksBeforeChecking() {
            when(borrowerRepository.findById(BORROWER_ID)).thenReturn(Optional.of(borrower));
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID)).thenReturn(Optional.empty());
            when(borrowRecordRepository.saveAndFlush(any(BorrowRecord.class))).thenAnswer(i -> i.getArgument(0));

            lendingService.borrow(BOOK_ID, BORROWER_ID);

            verify(copyRepository).findByIdForUpdate(BOOK_ID);
            verify(copyRepository, never()).findById(BOOK_ID);
        }

        @Test
        @DisplayName("rejects a copy that is already on loan")
        void rejectsAlreadyBorrowedCopy() {
            Borrower otherBorrower = new Borrower("Grace Hopper", "grace@example.com");
            ReflectionTestUtils.setField(otherBorrower, "id", 8L);

            when(borrowerRepository.findById(BORROWER_ID)).thenReturn(Optional.of(borrower));
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID))
                    .thenReturn(Optional.of(new BorrowRecord(copy, otherBorrower, NOW)));

            assertThatThrownBy(() -> lendingService.borrow(BOOK_ID, BORROWER_ID))
                    .isInstanceOf(BookAlreadyBorrowedException.class);

            verify(borrowRecordRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("404s on an unknown borrower")
        void rejectsUnknownBorrower() {
            when(borrowerRepository.findById(BORROWER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> lendingService.borrow(BOOK_ID, BORROWER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Borrower");
        }

        @Test
        @DisplayName("404s on an unknown book")
        void rejectsUnknownBook() {
            when(borrowerRepository.findById(BORROWER_ID)).thenReturn(Optional.of(borrower));
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> lendingService.borrow(BOOK_ID, BORROWER_ID))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("Book");
        }
    }

    @Nested
    @DisplayName("returnBook")
    class Return {

        @Test
        @DisplayName("closes the loan and stamps the return time")
        void closesLoan() {
            BorrowRecord active = new BorrowRecord(copy, borrower, NOW.minusSeconds(3600));
            when(borrowerRepository.existsById(BORROWER_ID)).thenReturn(true);
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID)).thenReturn(Optional.of(active));
            when(borrowRecordRepository.saveAndFlush(active)).thenReturn(active);

            LoanResponse response = lendingService.returnBook(BOOK_ID, BORROWER_ID);

            assertThat(active.getReturnedAt()).isEqualTo(NOW);
            assertThat(active.isActive()).isFalse();
            assertThat(response.returnedAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("rejects returning a copy that is not on loan")
        void rejectsUnborrowedCopy() {
            when(borrowerRepository.existsById(BORROWER_ID)).thenReturn(true);
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> lendingService.returnBook(BOOK_ID, BORROWER_ID))
                    .isInstanceOf(BookNotBorrowedException.class);
        }

        @Test
        @DisplayName("rejects a return by someone who does not hold the loan")
        void rejectsWrongBorrower() {
            Borrower holder = new Borrower("Grace Hopper", "grace@example.com");
            ReflectionTestUtils.setField(holder, "id", 99L);
            BorrowRecord active = new BorrowRecord(copy, holder, NOW.minusSeconds(60));

            when(borrowerRepository.existsById(BORROWER_ID)).thenReturn(true);
            when(copyRepository.findByIdForUpdate(BOOK_ID)).thenReturn(Optional.of(copy));
            when(borrowRecordRepository.findActiveByBookCopyId(BOOK_ID)).thenReturn(Optional.of(active));

            assertThatThrownBy(() -> lendingService.returnBook(BOOK_ID, BORROWER_ID))
                    .isInstanceOf(BorrowerMismatchException.class);

            assertThat(active.getReturnedAt()).isNull();
        }
    }
}
