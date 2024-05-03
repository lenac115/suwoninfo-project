package com.main.suwoninfo.domain;

import lombok.Getter;
import lombok.Setter;

import javax.persistence.*;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
public class Post extends Time {

    // post priamry key값
    @Id @Column(name = "post_id")
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

    public boolean inspectTitle(Post post, String title) {
        if(post.getTitle() == title) {
            return true;
        }
        return false;
    }
}
