package com.main.suwoninfo.domain;

import com.main.suwoninfo.dto.PostResponse;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;


import java.util.ArrayList;
import java.util.List;

@Entity
@Getter
@Table(
        name = "post", indexes = {
        @Index(name = "idx_post_type_created_at", columnList = "postType, createdTime DESC")
}
)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post extends Time {

    // post priamry key값
    @Id
    @Column(name = "post_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private PostType postType;

    private int price;

    private TradeStatus tradeStatus;

    // 글 제목
    @NotBlank
    private String title;

    // 글 내용
    @Column(columnDefinition = "TEXT")
    @NotEmpty
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @OneToMany(mappedBy = "post", cascade = CascadeType.ALL)
    private List<Comment> comment = new ArrayList<>();

    @OneToMany(mappedBy = "post", cascade = CascadeType.PERSIST, orphanRemoval = true)
    private List<Photo> photo = new ArrayList<>();

    public void setUser(User user) {
        this.user = user;
        user.getPostList().add(this);
    }

    public void addPhoto(List<Photo> added) {
        added.stream().forEach(i -> {
            photo.add(i);
            i.setPost(this);
        });
    }

    public void update(PostResponse postDto) {
        this.postType = postDto.postType();
        this.price = postDto.price();
        this.tradeStatus = postDto.tradeStatus();
        this.title = postDto.title();
        this.content = postDto.content();
    }

    public enum PostType {
        FREE,
        TRADE
    }

    public enum TradeStatus {
        READY,
        NOW_TRADING,
        DONE
    }
}
