package com.gmail.alexei28.shortcuthibernate.task1.repo;

import com.gmail.alexei28.shortcuthibernate.task1.entity.PostEntity;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PostRepository extends JpaRepository<PostEntity, Long> {
    /*
        Одно из решений проблемы N+1.
        Hibernate сам решает:
        - join fetch или
        - оптимальную стратегию загрузки
        В результате:
         - меньше SQL ручного кода
         - безопаснее чем join fetch в сложных случаях
     */
    @EntityGraph(attributePaths = "comments")
    List<PostEntity> findAll();
}
