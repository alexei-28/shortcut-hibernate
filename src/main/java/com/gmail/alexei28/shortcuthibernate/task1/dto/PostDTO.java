package com.gmail.alexei28.shortcuthibernate.task1.dto;

import java.util.List;

public record PostDTO(String title, List<CommentDTO> comments) {
}
