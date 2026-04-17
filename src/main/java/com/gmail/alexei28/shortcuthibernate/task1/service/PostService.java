package com.gmail.alexei28.shortcuthibernate.task1.service;

import com.gmail.alexei28.shortcuthibernate.task1.dto.CommentDTO;
import com.gmail.alexei28.shortcuthibernate.task1.dto.PostDTO;
import com.gmail.alexei28.shortcuthibernate.task1.entity.CommentEntity;
import com.gmail.alexei28.shortcuthibernate.task1.entity.PostEntity;
import com.gmail.alexei28.shortcuthibernate.task1.mapper.CommentEntityMapper;
import com.gmail.alexei28.shortcuthibernate.task1.mapper.PostEntityMapper;
import com.gmail.alexei28.shortcuthibernate.task1.repo.PostRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

@Service
public class PostService {
    private final PostEntityMapper postEntityMapper;
    private final CommentEntityMapper commentEntityMapper;
    private final PostRepository postRepository;
    private static final Logger logger = LoggerFactory.getLogger(PostService.class);

    public PostService(PostEntityMapper postEntityMapper, CommentEntityMapper commentEntityMapper, PostRepository postRepository) {
        this.postEntityMapper = postEntityMapper;
        this.commentEntityMapper = commentEntityMapper;
        this.postRepository = postRepository;
    }

    public PostEntity createPost(PostDTO postDTO) {
        PostEntity postEntity = postEntityMapper.toEntity(postDTO);
        for (CommentEntity commentEntity : postEntity.getComments()) {
            commentEntity.setPost(postEntity);
        }
        postRepository.save(postEntity);
        logger.debug("createPost, successfully saved to repo, postEntity = {}", postEntity);
        return postEntity;
    }

    /*
        Что даёт @Transactional

        1. Транзакция открывается на весь метод
        2. post остаётся managed

        Мы делаем:
            post.addComment(comment);

        4. Hibernate:
          - видит изменение коллекции
          - видит новый comment
          - благодаря CascadeType.ALL -> делает INSERT

        5. В конце транзакции:
             flush -> commit

        - Без postRepository.save(post) всё сохранится автоматически
    */
    @Transactional
    public void addComment(Long postId, CommentDTO commentDTO) {
        PostEntity post = getPostById(postId);
        CommentEntity comment = commentEntityMapper.toEntity(commentDTO);
        post.addComment(comment);
        logger.debug("addComment, postId = {}, comment = {}", postId, comment);
    }

    @Transactional
    public void deleteComment(Long postId, Long commentId) {
        PostEntity post = getPostById(postId);
        List<CommentEntity> commentEntityList = post.getComments();
        Optional<CommentEntity> comment = commentEntityList.stream()
                .filter(commentEntity -> commentEntity.getId().equals(commentId))
                .findFirst();
        comment.ifPresent(post::removeComment);
        logger.debug("deleteComment, postId = {}, commentId = {}", postId, commentId);
    }

    public PostEntity getPostById(Long id) {
        return postRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Post not found with id = " + id));
    }

    public List<PostEntity> getAllPosts() {
        return postRepository.findAll();
    }

    public void deletePost(Long id) {
        if (!postRepository.existsById(id)) {
            throw new RuntimeException("Post not found with id = " + id);
        }
        postRepository.deleteById(id);
    }


}
