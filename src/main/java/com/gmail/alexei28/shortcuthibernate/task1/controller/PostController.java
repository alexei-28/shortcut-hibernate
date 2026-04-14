package com.gmail.alexei28.shortcuthibernate.task1.controller;
import com.gmail.alexei28.shortcuthibernate.task1.dto.CommentDTO;
import com.gmail.alexei28.shortcuthibernate.task1.dto.PostDTO;
import com.gmail.alexei28.shortcuthibernate.task1.entity.PostEntity;
import com.gmail.alexei28.shortcuthibernate.task1.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/*
    Examples of requests: /restclient_task1.rc
*/
@RestController
@RequestMapping("/post")
public class PostController {
    private final PostService postService;
    private static final Logger logger = LoggerFactory.getLogger(PostController.class);

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping("/create")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create post")
    public PostEntity createPost(@RequestBody PostDTO postDTO) {
        return postService.createPost(postDTO);
    }

    @PostMapping("/{id}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Add comment to post")
    public void addComment(@PathVariable Long id, @RequestBody CommentDTO commentDTO) {
        postService.addComment(id, commentDTO);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get post by id")
    public PostEntity getPostById(@PathVariable Long id) {
        return postService.getPostById(id);
    }

    @GetMapping
    @Operation(summary = "Get all posts")
    public List<PostEntity> getAllPosts() {
        return postService.getAllPosts();
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete post by id")
    public void deletePost(@PathVariable Long id) {
        logger.info("deletePost, id = {}", id);
        postService.deletePost(id);
    }

    @DeleteMapping("/{postId}/comment/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete comment by id")
    public void deleteComment(@PathVariable Long postId, @PathVariable Long commentId) {
        logger.info("deleteComment, postId = {}, commentId = {}", postId, commentId);
        postService.deleteComment(postId, commentId);
    }
}
