package com.library.api.service;

import com.library.api.domain.Borrower;
import com.library.api.dto.BorrowerResponse;
import com.library.api.dto.CreateBorrowerRequest;
import com.library.api.dto.PageResponse;
import com.library.api.exception.EmailAlreadyRegisteredException;
import com.library.api.exception.ResourceNotFoundException;
import com.library.api.repository.BorrowerRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BorrowerService {

    private final BorrowerRepository borrowerRepository;

    public BorrowerService(BorrowerRepository borrowerRepository) {
        this.borrowerRepository = borrowerRepository;
    }

    /**
     * Registers a new member. Email is treated as the natural business key: the
     * pre-check gives a friendly 409, the DB unique index guarantees correctness
     * if two registrations race.
     */
    @Transactional
    public BorrowerResponse register(CreateBorrowerRequest request) {
        String name = request.name().trim();
        String email = request.email().trim().toLowerCase();

        if (borrowerRepository.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyRegisteredException(email);
        }

        try {
            Borrower saved = borrowerRepository.saveAndFlush(new Borrower(name, email));
            return BorrowerResponse.from(saved);
        } catch (DataIntegrityViolationException ex) {
            // Lost the race against a concurrent registration with the same email.
            throw new EmailAlreadyRegisteredException(email);
        }
    }

    @Transactional(readOnly = true)
    public BorrowerResponse findById(Long id) {
        return borrowerRepository.findById(id)
                .map(BorrowerResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Borrower", id));
    }

    /** Mapping to DTOs here, not in the controller, keeps entities inside the transaction. */
    @Transactional(readOnly = true)
    public PageResponse<BorrowerResponse> findAll(Pageable pageable) {
        return PageResponse.of(borrowerRepository.findAll(pageable), BorrowerResponse::from);
    }
}
