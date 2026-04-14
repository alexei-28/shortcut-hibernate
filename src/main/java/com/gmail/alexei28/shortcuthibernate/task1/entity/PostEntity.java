package com.gmail.alexei28.shortcuthibernate.task1.entity;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


/*-
    По умолчанию @OneToMany имеет тип загрузки LAZY.
    mappedBy = "post" -> это НЕ владелец связи (обратная сторона), FK лежит на стороне CommentEntity.
    Тот факт, что "comments" находится в списке у поста, не означает автоматическую установку обратной ссылки в Java-объекте.
    В JPA сторона с mappedBy — это "зеркальная" сторона.
    Hibernate игнорирует изменения в коллекции PostEntity.comments при обновлении базы данных.
    Hibernate смотрит только на поле CommentEntity.post.
*/

@Entity
@Table(name = "posts")
public class PostEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "posts_seq")
    @SequenceGenerator(name = "posts_seq", sequenceName = "posts_seq", allocationSize = 50)
    private Long id;

    @Column(nullable = false)
    private String title;

    @OneToMany( // эта сторона не управляет связью.
            mappedBy = "post", // Обратная сторона — владеет Comment.post. Без mappedBy Hibernate создал бы лишнюю промежуточную таблицу
            cascade = CascadeType.ALL, // persist/remove каскадируются
            orphanRemoval = true, // Удаление из коллекции -> DELETE
            fetch = FetchType.LAZY // Не грузить при SELECT Post
    )
    private List<CommentEntity> comments = new ArrayList<>();

    public void setId(Long id) {
        this.id = id;
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    // Helper-методы: синхронизация обеих сторон критична,
    // иначе в 1-м кэше Hibernate будет неконсистентное состояние.
    // Без синхронизации обеих сторон в рамках одной транзакции Hibernate увидит пост без нового комментария в 1-м кэше
    public void addComment(CommentEntity commentEntity) {
        comments.add(commentEntity);
        commentEntity.setPost(this);
    }

    // Для поддержание консистентности объектной модели в памяти. Синхронизирует обе стороны.
    // В bidirectional связи нужно ВСЕГДА обновлять обе стороны.
    public void removeComment(CommentEntity commentEntity) {
        comments.remove(commentEntity);
        // Разрываем owning side. Hibernate понимает: "Комментарий больше ни к чему не привязан → это orphan → DELETE"
        commentEntity.setPost(null);
    }

    public List<CommentEntity> getComments() {
        return comments;
    }

    public void setComments(List<CommentEntity> comments) {
        this.comments = comments;
    }

    // equals/hashCode по id (с защитой от null для transient)
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PostEntity that)) return false;
        return Objects.equals(getId(), that.getId());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getId());
    }

}
