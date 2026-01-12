package com.main.suwoninfo.domain;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

@Entity
@Getter @Setter
@Table(
        name = "comment"
)
public class Comment extends Time {

    //comment primary key값 auto increment로 작동
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Id
    @Column(name = "comment_id")
    private Long id;

    //댓글 깊이
    @ColumnDefault("0")
    private int depth;

    private boolean activated;

    //댓글 내용
    @Column(columnDefinition = "TEXT")
    @NotBlank
    private String content;

    //계층형 댓글의 부모
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent")
    private Comment parent;

    //대댓글
    @OneToMany(mappedBy = "parent", fetch = FetchType.EAGER)
    private List<Comment> children = new ArrayList<>();

    //포스트 외래키
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    //유저 외래키
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    //포스트 연관관계 지정
    public void setPost(Post post) {
        this.post = post;
        post.getComment().add(this);
    }

    //유저 연관관계 지정
    public void setUser(User user) {
        this.user = user;
        user.getCommentList().add(this);
    }

    public void addReplyComment(Comment parent) {
        this.parent = parent;
        this.depth = parent.getDepth() + 1;
        this.parent.children.add(this);
    }

    public boolean isActivated(Post post) {
        if(parent.activated) {
            return true;
        }
        return false;
    }
}
