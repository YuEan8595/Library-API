package com.library.api.service;

import com.library.api.domain.BookCopy;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/**
 * Borrowing and returning.
 *
 * <p>Requirement: no more than one borrower may hold a given book id at a time.
 * That is defended at three levels:
 * <ol>
 *   <li>a {@code SELECT ... FOR UPDATE} on the copy row, which serialises concurrent
 *       borrow attempts for that copy;</li>
 *   <li>an explicit check for an existing active loan inside the same transaction;</li>
 *   <li>a partial unique index in PostgreSQL on {@code (book_copy_id) WHERE returned_at IS NULL},
 *       which makes a duplicate active loan impossible even if the application layer is
 *       bypassed or a second instance is deployed.</li>
 * </ol>
 */
@Service
public class LendingService {

    private static final Logger log = LoggerFactory.getLogger(LendingService.class);

    private final BookCopyRepository copyRepository;
    private final BorrowerRepository borrowerRepository;
    private final BorrowRecordRepository borrowRecordRepository;
    private final Clock clock;

    public LendingService(BookCopyRepository copyRepository,
                          BorrowerRepository borrowerRepository,
                          BorrowRecordRepository borrowRecordRepository,
                          Clock clock) {
        this.copyRepository = copyRepository;
        this.borrowerRepository = borrowerRepository;
        this.borrowRecordRepository = borrowRecordRepository;
        this.clock = clock;
    }

    @Transactional
    public LoanResponse borrow(Long bookId, Long borrowerId) {
        Borrower borrower = borrowerRepository.findById(borrowerId)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower", borrowerId));

        // Lock first, then check. Doing it the other way round leaves a race window.
        BookCopy copy = copyRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));

        borrowRecordRepository.findActiveByBookCopyId(bookId).ifPresent(active -> {
            throw new BookAlreadyBorrowedException(bookId);
        });

        BorrowRecord record = new BorrowRecord(copy, borrower, Instant.now(clock));
        try {
            borrowRecordRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            // The partial unique index fired: someone else holds this copy.
            log.warn("Unique index rejected a duplicate active loan for book {}", bookId);
            throw new BookAlreadyBorrowedException(bookId);
        }

        log.info("Book {} borrowed by borrower {} (loan {})", bookId, borrowerId, record.getId());
        return LoanResponse.from(record);
    }

    @Transactional
    public LoanResponse returnBook(Long bookId, Long borrowerId) {
        if (!borrowerRepository.existsById(borrowerId)) {
            throw new ResourceNotFoundException("Borrower", borrowerId);
        }

        BookCopy copy = copyRepository.findByIdForUpdate(bookId)
                .orElseThrow(() -> new ResourceNotFoundException("Book", bookId));

        BorrowRecord record = borrowRecordRepository.findActiveByBookCopyId(copy.getId())
                .orElseThrow(() -> new BookNotBorrowedException(bookId));

        if (!record.getBorrower().getId().equals(borrowerId)) {
            throw new BorrowerMismatchException(bookId, borrowerId);
        }

        record.markReturned(Instant.now(clock));
        borrowRecordRepository.saveAndFlush(record);

        log.info("Book {} returned by borrower {} (loan {})", bookId, borrowerId, record.getId());
        return LoanResponse.from(record);
    }

    /** Everything this borrower currently has out. */
    @Transactional(readOnly = true)
    public List<LoanResponse> activeLoansOf(Long borrowerId) {
        if (!borrowerRepository.existsById(borrowerId)) {
            throw new ResourceNotFoundException("Borrower", borrowerId);
        }
        return borrowRecordRepository.findActiveByBorrowerId(borrowerId).stream()
                .map(LoanResponse::from)
                .toList();
    }
}
