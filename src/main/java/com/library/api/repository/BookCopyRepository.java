package com.library.api.repository;

import com.library.api.domain.BookCopy;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BookCopyRepository extends JpaRepository<BookCopy, Long> {

    /** {@code join fetch} keeps the listing endpoint to a single query (no N+1). */
    @Query(value = "select c from BookCopy c join fetch c.edition e",
           countQuery = "select count(c) from BookCopy c")
    Page<BookCopy> findAllWithEdition(Pageable pageable);

    @Query(value = "select c from BookCopy c join fetch c.edition e "
            + "where lower(e.title) like lower(concat('%', :q, '%')) "
            + "   or lower(e.author) like lower(concat('%', :q, '%')) "
            + "   or e.isbn = :q",
           countQuery = "select count(c) from BookCopy c join c.edition e "
            + "where lower(e.title) like lower(concat('%', :q, '%')) "
            + "   or lower(e.author) like lower(concat('%', :q, '%')) "
            + "   or e.isbn = :q")
    Page<BookCopy> searchWithEdition(@Param("q") String q, Pageable pageable);

    /**
     * Locks the copy row for the duration of the transaction. Two concurrent borrow
     * attempts on the same copy are serialised here, so the second one sees the first
     * one's borrow record instead of racing past the availability check.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BookCopy c where c.id = :id")
    Optional<BookCopy> findByIdForUpdate(@Param("id") Long id);
}
