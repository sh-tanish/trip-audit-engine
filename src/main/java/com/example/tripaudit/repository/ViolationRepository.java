package com.example.tripaudit.repository;

import com.example.tripaudit.domain.Violation;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

/**
 * Append-only repository for violations.
 * Extends the bare Spring Data Repository marker interface instead of JpaRepository or CrudRepository.
 * Declares only insert and query methods; exposes NO update or delete methods.
 */
public interface ViolationRepository extends Repository<Violation, Long> {

    Violation save(Violation violation);

    Optional<Violation> findById(Long id);

    List<Violation> findByTripIdOrderByDetectedAtAsc(Long tripId);

    boolean existsByDedupKey(String dedupKey);

    Optional<Violation> findByDedupKey(String dedupKey);
}
