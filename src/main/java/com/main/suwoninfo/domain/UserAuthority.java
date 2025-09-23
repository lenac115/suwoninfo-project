package com.main.suwoninfo.domain;

import lombok.Getter;
import lombok.Setter;

import jakarta.persistence.*;

@Entity
@Getter
@Setter
public class UserAuthority {

    @Id @Column(name = "user_authority_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "user_id")
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "authority_name")
    private Authority authority;
}
