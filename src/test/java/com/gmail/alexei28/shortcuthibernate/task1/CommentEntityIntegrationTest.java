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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@ActiveProfiles("test")
@SpringBootTest
@Testcontainers
class CommentEntityIntegrationTest {
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
    @DisplayName("should fail when saving comment with non-existing post")
    void shouldFail_whenSavingCommentWithNonExistingPost() {
        // Arrange
        PostEntity fakePost = new PostEntity();
        fakePost.setId(999L); // такого поста нет в БД

        CommentEntity comment = new CommentEntity();
        comment.setBody("test");
        comment.setPost(fakePost);

        // Act and Assert
        assertThrows(DataIntegrityViolationException.class, () -> {
            commentRepository.saveAndFlush(comment);
        });
    }

    @Test
    @DisplayName("should save comment when post exists")
    void shouldSaveComment_whenPostExists() {
        // Arrange
        PostEntity post = new PostEntity();
        post.setTitle("post title 900");
        post = postRepository.saveAndFlush(post);

        CommentEntity comment = new CommentEntity();
        comment.setBody("comment in post 900");
        comment.setPost(post);

        // Act
        CommentEntity savedComment = commentRepository.saveAndFlush(comment);

        // Assert
        assertThat(savedComment.getId()).isNotNull();
    }

}