package com.main.suwoninfo.domain;
import lombok.*;

import jakarta.persistence.*;


@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Authority {

    @Id @GeneratedValue
    @Column(name = "authority_id")
    private Long id;

    @Column(name = "authority_name")
    private String name;
}