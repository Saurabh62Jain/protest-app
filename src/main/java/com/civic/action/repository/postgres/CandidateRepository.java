package com.civic.action.repository.postgres;

import com.civic.action.model.postgres.Candidate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CandidateRepository extends JpaRepository<Candidate, Long> {
    Optional<Candidate> findByBoundaryCode(String boundaryCode);
    List<Candidate> findByCountry(String country);
    List<Candidate> findByNameContainingIgnoreCase(String name);
}
