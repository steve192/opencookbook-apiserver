package com.sterul.opencookbookapiserver.entities.account;

import com.sterul.opencookbookapiserver.entities.ChangeInformationEmbeddable;
import lombok.Data;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;

@Entity
@Data
public class ActivationLink {
    @Id
    @GeneratedValue(generator = "UUID")
    @GenericGenerator(
            name = "UUID",
            strategy = "org.hibernate.id.UUIDGenerator"
    )
    private String id;

    @OneToOne
    private User user;


    @Embedded
    private ChangeInformationEmbeddable changeInformationEmbeddable;

}
