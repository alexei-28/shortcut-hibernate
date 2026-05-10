package com.gmail.alexei28.shortcut.hibernate.task1.dto;

import java.util.List;

public record PostDTO(String title, List<CommentDTO> comments) {
}
