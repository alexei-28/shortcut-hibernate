package com.gmail.alexei28.shortcut.hibernate.task1.mapper;

import com.gmail.alexei28.shortcut.hibernate.task1.dto.CommentDTO;
import com.gmail.alexei28.shortcut.hibernate.task1.entity.CommentEntity;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface CommentEntityMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "post", ignore = true)
    CommentEntity toEntity(CommentDTO dto);
}