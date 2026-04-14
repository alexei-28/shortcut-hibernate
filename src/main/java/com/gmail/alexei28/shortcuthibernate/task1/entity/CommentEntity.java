package com.gmail.alexei28.shortcuthibernate.task1.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.util.Objects;

/*
    В JPA/Hibernate за сохранение внешнего ключа (FK) отвечает только одна сторона — владелец связи (Owner side).
    Owner — это та сторона, которая управляет связью и хранит внешний ключ (@JoinColumn), т.е Это CommentEntity.post
    Владелец связи (owner-side). Именно owner-side управляет FK.
    FK лежит на этой стороне. Hibernate смотрит только на owner-side.
    Именно CommentEntity.post управляет колонкой post_id.
    Hibernate смотрит только на owner-side -> если здесь post = null -> значит связи нет -> FK не ставится.
*/
@Entity
@Table(name = "comments")
public class CommentEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "comments_seq")
    @SequenceGenerator(name = "comments_seq", sequenceName = "comments_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false, length = 2000)
    private String body;

    @ManyToOne(fetch = FetchType.LAZY)  // Иначе — EAGER по умолчанию
    @JoinColumn(name = "post_id") // owner-side, FK
    @JsonIgnore
    private PostEntity post;

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public PostEntity getPost() {
        return post;
    }

    public void setPost(PostEntity post) {
        this.post = post;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CommentEntity that)) return false;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }
}
