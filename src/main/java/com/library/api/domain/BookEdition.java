package com.library.api.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;

/**
 * The bibliographic identity of a book, keyed by ISBN.
 *
 * <p>Making the ISBN the primary key is what structurally enforces the rule
 * "two books with the same ISBN must have the same title and author": the
 * title/author pair is stored exactly once per ISBN, so it is impossible for
 * two copies to disagree. Physical copies live in {@link BookCopy}.
 */
@Entity
@Table(name = "book_edition")
public class BookEdition {

    @Id
    @Column(name = "isbn", length = 13, nullable = false, updatable = false)
    private String isbn;

    @Column(name = "title", length = 255, nullable = false)
    private String title;

    @Column(name = "author", length = 255, nullable = false)
    private String author;

    protected BookEdition() {
        // required by JPA
    }

    public BookEdition(String isbn, String title, String author) {
        this.isbn = isbn;
        this.title = title;
        this.author = author;
    }

    public String getIsbn() {
        return isbn;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    /** True when the supplied title/author match this edition (case-insensitive, trimmed). */
    public boolean matches(String otherTitle, String otherAuthor) {
        return title.equalsIgnoreCase(otherTitle.trim()) && author.equalsIgnoreCase(otherAuthor.trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BookEdition other)) {
            return false;
        }
        return isbn != null && isbn.equals(other.isbn);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(isbn);
    }
}
