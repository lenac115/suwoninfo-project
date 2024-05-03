package com.main.suwoninfo.domain;

import lombok.*;

import javax.persistence.*;

@Getter @AllArgsConstructor
@Entity @NoArgsConstructor
@Builder
public class Photo {

    @Id @Column(name = "photo_id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String origFileName;

    private String filePath;

    private Long fileSize;

    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;

    public void setPost(Post post) {
        this.post = post;
        post.getPhoto().add(this);
    }
}
