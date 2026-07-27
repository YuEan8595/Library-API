package com.library.api.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * A single physical copy on the shelf. This is the "Book" the API exposes:
 * it carries the unique id that borrow/return operate on.
 *
 * <p>Registering the same ISBN twice creates two BookCopy rows with
 * different ids pointing at one shared {@link BookEdition}.
 */
@Entity
@Table(name = "book_copy")
public class BookCopy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // LAZY by default; the listing query join-fetches the edition explicitly so the
    // catalogue endpoint stays at one query instead of one per row.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "isbn", nullable = false)
    private BookEdition edition;

    protected BookCopy() {
        // required by JPA
    }

    public BookCopy(BookEdition edition) {
        this.edition = edition;
    }

    public Long getId() {
        return id;
    }

    public BookEdition getEdition() {
        return edition;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookCopy other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
