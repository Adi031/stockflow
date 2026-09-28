package com.stockflow.payment;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Payment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "provider_ref")
    private String providerRef;

    @Column(name = "attempted_at")
    private LocalDateTime attemptedAt;

    @PrePersist
    void prePersist() { attemptedAt = LocalDateTime.now(); }

    public enum Status { PENDING, SUCCESS, FAILED, TIMEOUT }
}
