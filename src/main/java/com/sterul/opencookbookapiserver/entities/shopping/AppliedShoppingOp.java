package com.sterul.opencookbookapiserver.entities.shopping;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "shopping_applied_op")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AppliedShoppingOp {

    @Id
    private String opId;

    @Column(nullable = false)
    private Long listId;

    @Column(nullable = false)
    private Instant appliedOn;
}
