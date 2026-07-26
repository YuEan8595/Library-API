package com.library.api.repository;

import com.library.api.domain.Borrower;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BorrowerRepository extends JpaRepository<Borrower, Long> {

    boolean existsByEmailIgnoreCase(String email);
}
