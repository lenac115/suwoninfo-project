package com.main.suwoninfo.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class PostStatistics {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull @ColumnDefault("0")
    private int count;

    @NotNull
    @Enumerated(EnumType.STRING)
    private Post.PostType postType;


    public void addCount() {
        this.count++;
    }

    public void minusCount() {
        this.count--;
    }
}
