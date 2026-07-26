package com.library.api.integration;

import com.library.api.domain.BookCopy;
import com.library.api.domain.BookEdition;
import com.library.api.domain.Borrower;
import com.library.api.exception.BookAlreadyBorrowedException;
import com.library.api.repository.BookCopyRepository;
import com.library.api.repository.BookEditionRepository;
import com.library.api.repository.BorrowRecordRepository;
import com.library.api.repository.BorrowerRepository;
import com.library.api.service.LendingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The requirement that matters most: no more than one member holds a given book id
 * at a time. Twenty threads race for the same copy; exactly one may win.
 */
@DisplayName("Concurrent borrowing")
class ConcurrentBorrowIntegrationTest extends AbstractIntegrationTest {

    private static final int THREADS = 20;

    @Autowired private LendingService lendingService;
    @Autowired private BookEditionRepository editionRepository;
    @Autowired private BookCopyRepository copyRepository;
    @Autowired private BorrowerRepository borrowerRepository;
    @Autowired private BorrowRecordRepository borrowRecordRepository;

    private Long bookId;
    private List<Long> borrowerIds;

    @BeforeEach
    void seed() {
        borrowRecordRepository.deleteAll();
        copyRepository.deleteAll();
        editionRepository.deleteAll();
        borrowerRepository.deleteAll();

        BookEdition edition = editionRepository.save(
                new BookEdition("9780132350884", "Clean Code", "Robert C. Martin"));
        bookId = copyRepository.save(new BookCopy(edition)).getId();

        borrowerIds = new ArrayList<>();
        for (int i = 0; i < THREADS; i++) {
            borrowerIds.add(borrowerRepository.save(new Borrower("Member " + i, "member" + i + "@example.com")).getId());
        }
    }

    @Test
    @DisplayName("exactly one of 20 simultaneous borrowers wins the copy")
    void onlyOneBorrowerWins() throws Exception {
        CountDownLatch startGun = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger rejections = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Callable<Void>> tasks = borrowerIds.stream()
                    .map(borrowerId -> (Callable<Void>) () -> {
                        startGun.await();
                        try {
                            lendingService.borrow(bookId, borrowerId);
                            successes.incrementAndGet();
                        } catch (BookAlreadyBorrowedException expected) {
                            rejections.incrementAndGet();
                        } catch (RuntimeException other) {
                            // A lock-timeout or serialisation failure is also a valid rejection,
                            // as long as it did not create a second loan.
                            rejections.incrementAndGet();
                        }
                        return null;
                    })
                    .toList();

            List<Future<Void>> futures = new ArrayList<>();
            for (Callable<Void> task : tasks) {
                futures.add(pool.submit(task));
            }
            startGun.countDown();
            for (Future<Void> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).as("exactly one borrow may succeed").isEqualTo(1);
        assertThat(rejections.get()).isEqualTo(THREADS - 1);
        assertThat(borrowRecordRepository.findAll())
                .as("exactly one loan row exists for the copy")
                .hasSize(1);
        assertThat(borrowRecordRepository.findActiveByBookCopyId(bookId)).isPresent();
    }
}
