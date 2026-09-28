package com.stockflow.catalog;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "sellers")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Seller {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, unique = true)
    private Long userId;

    @Column(name = "store_name", nullable = false)
    private String storeName;

    @Column(nullable = false)
    private String status;
}
