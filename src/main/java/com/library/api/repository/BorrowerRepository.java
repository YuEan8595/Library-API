package com.library.api.repository;

import com.library.api.domain.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<Borrower> findByEmailIgnoreCase(String email);
}
