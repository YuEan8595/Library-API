package com.library.api.repository;

import com.library.api.domain.BookEdition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookEditionRepository extends JpaRepository<BookEdition, String> {

    /**
     * Inserts the edition only if that ISBN is not on file yet, and reports how many rows
     * were written.
     *
     * The obvious alternative - "look it up, and save it if absent" - has a race: two
     * requests registering the same new ISBN can both see nothing and both insert, and the
     * loser gets a constraint violation. Catching that violation is not a fix either,
     * because a failed flush leaves the Hibernate session unusable and the transaction
     * marked rollback-only, so the recovery read would fail too.
     *
     * ON CONFLICT DO NOTHING pushes the check and the insert into one atomic
     * statement, so there is no window to lose and no exception to recover from. The bare
     * form (no conflict target) is used deliberately: isbn is the only unique
     * constraint on the table, so it behaves identically to naming the target explicitly.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            INSERT INTO book_edition (isbn, title, author)
            VALUES (:isbn, :title, :author)
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("isbn") String isbn,
                       @Param("title") String title,
                       @Param("author") String author);
}
