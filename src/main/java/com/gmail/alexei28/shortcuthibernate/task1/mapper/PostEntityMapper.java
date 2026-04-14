package com.gmail.alexei28.shortcuthibernate.task1.mapper;

import com.gmail.alexei28.shortcuthibernate.task1.dto.PostDTO;
import com.gmail.alexei28.shortcuthibernate.task1.entity.PostEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(
        componentModel = "spring",
        uses = CommentEntityMapper.class
)
public interface PostEntityMapper {
    @Mapping(target = "id", ignore = true)
    PostEntity toEntity(PostDTO postDTO);
}
