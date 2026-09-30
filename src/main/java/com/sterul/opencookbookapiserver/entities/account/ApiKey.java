package com.sterul.opencookbookapiserver.entities.account;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import com.sterul.opencookbookapiserver.entities.AuditableEntity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

/** A long-lived secret a headless client such as Home Assistant signs in with. */
@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = false, onlyExplicitlyIncluded = true)
public class ApiKey extends AuditableEntity {

    @Id
    @SequenceGenerator(name = "api_key_seq", sequenceName = "api_key_seq", allocationSize = 1)
    @GeneratedValue(generator = "api_key_seq")
    @EqualsAndHashCode.Include
    private Long id;

    @ManyToOne(optional = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private CookpalUser owner;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(nullable = false, unique = true, length = 64)
    private String secretHash;

    /** The secret's first characters, so its owner can tell keys apart. */
    @Column(nullable = false, length = 16)
    private String displayPrefix;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "api_key_scope", joinColumns = @JoinColumn(name = "api_key_id"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    @Enumerated(EnumType.STRING)
    @Column(name = "scope", nullable = false, length = 32)
    @Builder.Default
    private Set<ApiScope> scopes = EnumSet.noneOf(ApiScope.class);

    private Instant lastUsedAt;
}
