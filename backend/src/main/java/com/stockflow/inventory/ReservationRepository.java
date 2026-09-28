package com.stockflow.inventory;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
    List<Reservation> findByStatusAndExpiresAtBefore(Reservation.Status status, LocalDateTime cutoff);
    List<Reservation> findByUserIdAndStatus(Long userId, Reservation.Status status);
}
