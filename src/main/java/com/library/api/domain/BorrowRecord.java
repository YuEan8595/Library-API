package com.library.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * One borrow event. A record with {@code returnedAt == null} is an *active* loan.
 *
 * <p>Keeping returned loans instead of deleting them gives the library a full
 * lending history for free. The "only one borrower per copy at a time" rule is
 * enforced by a partial unique index on {@code (book_copy_id) WHERE returned_at IS NULL}
 * (see {@code V1__init.sql}), backed by a pessimistic row lock in the service layer.
 */
@Entity
@Table(name = "borrow_record")
public class BorrowRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_copy_id", nullable = false)
    private BookCopy bookCopy;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "borrower_id", nullable = false)
    private Borrower borrower;

    @Column(name = "borrowed_at", nullable = false)
    private Instant borrowedAt;

    @Column(name = "returned_at")
    private Instant returnedAt;

    protected BorrowRecord() {
        // required by JPA
    }

    public BorrowRecord(BookCopy bookCopy, Borrower borrower, Instant borrowedAt) {
        this.bookCopy = bookCopy;
        this.borrower = borrower;
        this.borrowedAt = borrowedAt;
    }

    public Long getId() {
        return id;
    }

    public BookCopy getBookCopy() {
        return bookCopy;
    }

    public Borrower getBorrower() {
        return borrower;
    }

    public Instant getBorrowedAt() {
        return borrowedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    public boolean isActive() {
        return returnedAt == null;
    }

    public void markReturned(Instant when) {
        if (returnedAt != null) {
            throw new IllegalStateException("Borrow record " + id + " has already been returned");
        }
        this.returnedAt = when;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BorrowRecord other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
