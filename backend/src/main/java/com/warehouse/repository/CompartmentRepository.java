package com.warehouse.repository;

import com.warehouse.entity.Compartment;
import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface CompartmentRepository
        extends JpaRepository<Compartment, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT c FROM Compartment c WHERE c.id = :id")
    Optional<Compartment> findByIdForUpdate(@Param("id") Long id);
}