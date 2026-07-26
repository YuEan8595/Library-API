package com.library.api.repository;

import com.library.api.domain.BorrowRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface BorrowRecordRepository extends JpaRepository<BorrowRecord, Long> {

    @Query("select r from BorrowRecord r where r.bookCopy.id = :bookCopyId and r.returnedAt is null")
    Optional<BorrowRecord> findActiveByBookCopyId(@Param("bookCopyId") Long bookCopyId);

    /**
     * Id projection rather than whole entities: the catalogue endpoint only needs to know
     * *which* copies on the current page are out, so one scalar query answers the whole page.
     */
    @Query("select r.bookCopy.id from BorrowRecord r "
            + "where r.bookCopy.id in :bookCopyIds and r.returnedAt is null")
    List<Long> findActiveBookCopyIds(@Param("bookCopyIds") Collection<Long> bookCopyIds);

    @Query("select r from BorrowRecord r "
            + "join fetch r.borrower "
            + "join fetch r.bookCopy c "
            + "join fetch c.edition "
            + "where r.borrower.id = :borrowerId and r.returnedAt is null")
    List<BorrowRecord> findActiveByBorrowerId(@Param("borrowerId") Long borrowerId);
}
