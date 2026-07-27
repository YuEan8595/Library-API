package com.library.api.service;

import com.library.api.domain.BookCopy;
import com.library.api.domain.BookEdition;
import com.library.api.dto.BookResponse;
import com.library.api.dto.CreateBookRequest;
import com.library.api.dto.PageResponse;
import com.library.api.exception.IsbnMismatchException;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BookCopyRepository;
import com.library.api.repository.BookEditionRepository;
import com.library.api.repository.BorrowRecordRepository;
import com.library.api.validation.IsbnNormalizer;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

@Service
public class BookService {

    private final BookEditionRepository editionRepository;
    private final BookCopyRepository copyRepository;
    private final BorrowRecordRepository borrowRecordRepository;

    public BookService(BookEditionRepository editionRepository,
                       BookCopyRepository copyRepository,
                       BorrowRecordRepository borrowRecordRepository) {
        this.editionRepository = editionRepository;
        this.copyRepository = copyRepository;
        this.borrowRecordRepository = borrowRecordRepository;
    }

    /**
     * Registers one physical copy.
     * If the ISBN is new, its title/author are recorded. If the ISBN already exists,
     * the submitted title/author must match it, otherwise the request is rejected with
     * 409. Either way a brand-new copy row and therefore a brand-new book id - is
     * created, which is what makes multiple copies of the same ISBN possible.
     */
    @Transactional
    public BookResponse register(CreateBookRequest request) {
        String isbn = IsbnNormalizer.normalize(request.isbn());
        String title = request.title().trim();
        String author = request.author().trim();

        BookEdition edition = resolveEdition(isbn, title, author);
        BookCopy copy = copyRepository.save(new BookCopy(edition));

        // A freshly registered copy is on the shelf by definition.
        return BookResponse.from(copy, true);
    }

    /**
     * Returns the edition for this ISBN, creating it if this is the first copy.
     * Insert-if-absent first, then read back and compare. Whichever request wins a race
     * to create a new ISBN, both end up reading the same winning row and both validate
     * their title/author against it, so a concurrent conflicting registration is rejected
     * with the same 409 a sequential one would get.
     */
    private BookEdition resolveEdition(String isbn, String title, String author) {
        editionRepository.insertIfAbsent(isbn, title, author);

        BookEdition edition = editionRepository.findById(isbn)
                .orElseThrow(() -> new IllegalStateException(
                        "Edition " + isbn + " vanished immediately after being written"));

        if (!edition.matches(title, author)) {
            throw new IsbnMismatchException(isbn, edition.getTitle(), edition.getAuthor());
        }
        return edition;
    }

    /**
     * Lists every copy in the library, newest page first by id.
     * Availability is resolved with one extra query for the whole page rather than
     * one per row, which keeps the endpoint free of the N+1 problem.
     */
    @Transactional(readOnly = true)
    public PageResponse<BookResponse> findAll(String search, Pageable pageable) {
        Page<BookCopy> page = StringUtils.hasText(search)
                ? copyRepository.searchWithEdition(search.trim(), pageable)
                : copyRepository.findAllWithEdition(pageable);

        List<Long> ids = page.getContent().stream().map(BookCopy::getId).toList();
        Set<Long> borrowedIds = ids.isEmpty()
                ? Set.of()
                : Set.copyOf(borrowRecordRepository.findActiveBookCopyIds(ids));

        return PageResponse.of(page, copy -> BookResponse.from(copy, !borrowedIds.contains(copy.getId())));
    }

    @Transactional(readOnly = true)
    public BookResponse findById(Long id) {
        BookCopy copy = copyRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Book", id));
        boolean available = borrowRecordRepository.findActiveByBookCopyId(id).isEmpty();
        return BookResponse.from(copy, available);
    }
}
