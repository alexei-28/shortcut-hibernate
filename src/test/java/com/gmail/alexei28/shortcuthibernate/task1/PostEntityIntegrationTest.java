package com.gmail.alexei28.shortcuthibernate.task1;

import com.gmail.alexei28.shortcuthibernate.task1.entity.CommentEntity;
import com.gmail.alexei28.shortcuthibernate.task1.entity.PostEntity;
import com.gmail.alexei28.shortcuthibernate.task1.repo.CommentRepository;
import com.gmail.alexei28.shortcuthibernate.task1.repo.PostRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class PostEntityIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgresContainer =
            new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private CommentRepository commentRepository;

    @PersistenceContext
    private EntityManager em;


    @Test
    @DisplayName("should save post without comments")
    @Transactional // Чтобы всё выполнялось в одной Hibernate Session
    void shouldSavePostWithoutComments() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("Post without comments");

        // Act
        PostEntity saved = postRepository.save(post);
        postRepository.flush();

        // Assert
        List<CommentEntity> commentsList = commentRepository.findByPostId(saved.getId());
        assertThat(commentsList).isEmpty();
    }

    /*
        Проверяет CascadeType.ALL при сохранении PostEntity с двумя комментариями.

        Steps:
        - создаём PostEntity
        - добавляем к нему 2 CommentEntity
        - сохраняем только Post
        - проверяем, что комментарии тоже сохранились
        - дополнительно проверяем обратную связь через post_id
     */
    @Test
    @DisplayName("Should save post with 2 comments via CascadeType.ALL")
    @Transactional
    void shouldSavePostWithCommentsCascadePersist() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("Post with 2 comments");

        CommentEntity comment1 = new CommentEntity();
        comment1.setBody("First comment");
        comment1.setPost(post);

        CommentEntity comment2 = new CommentEntity();
        comment2.setBody("Second comment");
        comment2.setPost(post);

        // важно: добавляем в коллекцию поста (owning side relationship setup)
        post.setComments(List.of(comment1, comment2));

        // Act
        PostEntity savedPost = postRepository.save(post);
        postRepository.flush(); // гарантируем запись в БД

        // Assert
        assertThat(savedPost.getId()).isNotNull();
        List<CommentEntity> comments =
                commentRepository.findByPostId(savedPost.getId());
        assertThat(comments)
                .hasSize(2)
                .extracting(CommentEntity::getBody)
                .containsExactlyInAnyOrder("First comment", "Second comment");
    }


    /*
        Проверяет  orphanRemoval = true.

        Steps:
        - создаёт Post + 2 Comments
        - сохраняет через cascade
        - делает flush
        - удаляет один комментарий из коллекции post.getComments()
     */
    @Test
    @DisplayName("Should delete orphan comment when removed from post.comments")
    @Transactional
    void shouldDeleteOrphanCommentWhenRemovedFromCollection() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("Post with orphan removal");

        CommentEntity comment1 = new CommentEntity();
        comment1.setBody("comment 1");
        comment1.setPost(post);

        CommentEntity comment2 = new CommentEntity();
        comment2.setBody("comment 2");
        comment2.setPost(post);

        post.setComments(new ArrayList<>(List.of(comment1, comment2)));

        PostEntity savedPost = postRepository.save(post);
        postRepository.flush();

        Long postId = savedPost.getId();
        List<CommentEntity> initialComments = commentRepository.findByPostId(postId);
        assertThat(initialComments).hasSize(2);

        // Act: удаляем один комментарий из коллекции
        List<CommentEntity> commentsList = savedPost.getComments();
        commentsList.removeFirst();
        postRepository.flush(); // Hibernate не просто разрывает связь, а делает: DELETE FROM comment WHERE id = ?
        em.clear(); // важно: сбрасываем persistence context

        // Assert: в БД должен остаться только 1 комментарий
        List<CommentEntity> remaining = commentRepository.findByPostId(postId);
        assertThat(remaining).hasSize(1);
    }


    /*
        Steps:
        -cascade = CascadeType.ALL включает REMOVE
        -при postRepository.deleteById(postId) Hibernate делает:
            DELETE FROM comments WHERE post_id = ?
            DELETE FROM posts WHERE id = ?

        -flush() — обязателен, иначе DELETE может не уйти в БД до assert
        -em.clear() — защищает от false-positive из 1st level cache
     */
    @Test
    @DisplayName("Should delete post and cascade delete all comments")
    @Transactional
    void shouldDeletePostAndCascadeRemoveComments() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("Post to delete");

        CommentEntity comment1 = new CommentEntity();
        comment1.setBody("comment 1");

        CommentEntity comment2 = new CommentEntity();
        comment2.setBody("comment 2");

        // ВАЖНО: используем helper → синхронизирует обе стороны
        post.addComment(comment1);
        post.addComment(comment2);

        PostEntity savedPost = postRepository.save(post);
        postRepository.flush(); // принудительная синхронизация Persistence Context с БД

        Long postId = savedPost.getId();

        // sanity-check
        List<CommentEntity> beforeDelete = commentRepository.findByPostId(postId);
        assertThat(beforeDelete).hasSize(2);

        // Act
        postRepository.deleteById(postId);
        postRepository.flush();          // принудительный flush → DELETE в БД
        em.clear();                      // очищаем persistence context

        // Assert
        assertThat(postRepository.findById(postId)).isEmpty();
        List<CommentEntity> afterDelete = commentRepository.findByPostId(postId);
        assertThat(afterDelete).isEmpty(); // ключевая проверка CascadeType.REMOVE
    }

    /*
        Steps:
        -работа owner-side (CommentEntity.post)
        -корректность addComment() (синхронизация двух сторон)
✅       -CascadeType.ALL → INSERT без явного save(comment)
✅       -поведение Hibernate в managed состоянии (@Transactional)
    */
    @Test
    @DisplayName("Should add new comment to existing post")
    @Transactional
    void shouldAddNewCommentToExistingPost() {
        // Arrange: создаём пост без комментариев
        PostEntity post = new PostEntity();
        post.setTitle("Post for adding comment");

        PostEntity savedPost = postRepository.save(post);
        postRepository.flush();

        Long postId = savedPost.getId();

        // убеждаемся, что комментариев нет
        List<CommentEntity> initialComments =
                commentRepository.findByPostId(postId);
        assertThat(initialComments).isEmpty();

        // Act: добавляем новый комментарий к существующему посту
        CommentEntity newComment = new CommentEntity();
        newComment.setBody("New comment");

        // КРИТИЧНО: используем helper → синхронизирует обе стороны
        savedPost.addComment(newComment);
        postRepository.flush(); // Hibernate сделает INSERT comment
        em.clear(); // сбрасываем persistence context, чтобы читать из БД

        // Assert: проверяем, что комментарий появился
        List<CommentEntity> comments =
                commentRepository.findByPostId(postId);

        assertThat(comments)
                .hasSize(1)
                .first()
                .extracting(CommentEntity::getBody)
                .isEqualTo("New comment");
    }


    /*
        Что делает Hibernate под капотом
        В момент flush():
            -Hibernate проверяет CommentEntity.post
            -Видит post == null
            -Значит:
                -либо INSERT с post_id = null
                -либо вообще не считает это связью

        В итоге:
         -findByPostId(postId) → пусто
     */
    @Test
    @DisplayName("Should NOT save comment if owning side (post) is not set")
    @Transactional
    void shouldNotSaveCommentWhenOwningSideNotSet() {
        // Arrange: создаём пост
        PostEntity post = new PostEntity();
        post.setTitle("Post without proper relationship sync");

        PostEntity savedPost = postRepository.save(post);
        postRepository.flush();

        Long postId = savedPost.getId();

        // Act: создаём комментарий, НО НЕ ставим post (owner-side)
        CommentEntity comment = new CommentEntity();
        comment.setBody("Broken comment");

        // Ошибка: добавляем только в коллекцию inverse-side
        savedPost.getComments().add(comment);
        postRepository.flush(); // Hibernate попробует синхронизировать
        em.clear(); // читаем из БД, а не из persistence context

        // Assert
        List<CommentEntity> comments = commentRepository.findByPostId(postId);
        // комментарий НЕ сохранится с этим post
        assertThat(comments).isEmpty();
    }

    @Test
    @DisplayName("Should delete comment from post")
    @Transactional
    void shouldDeleteCommentFromPost() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("Post for direct delete test");

        CommentEntity comment1 = new CommentEntity();
        comment1.setBody("comment 1");

        CommentEntity comment2 = new CommentEntity();
        comment2.setBody("comment 2");

        // синхронизируем обе стороны
        post.addComment(comment1);
        post.addComment(comment2);

        PostEntity savedPost = postRepository.save(post);
        postRepository.flush();

        Long postId = savedPost.getId();
        CommentEntity commentToDelete = savedPost.getComments().getFirst();
        Long commentToDeleteId = commentToDelete.getId();

        // sanity check
        List<CommentEntity> commentList = commentRepository.findByPostId(postId);
        assertThat(commentList).hasSize(2);

        // Act (без service)
        savedPost.removeComment(commentToDelete);
        postRepository.flush();
        em.clear();

        // Assert
        List<CommentEntity> remaining = commentRepository.findByPostId(postId);
        assertThat(remaining)
                .hasSize(1)
                .allMatch(c -> !c.getId().equals(commentToDeleteId));
    }

}

