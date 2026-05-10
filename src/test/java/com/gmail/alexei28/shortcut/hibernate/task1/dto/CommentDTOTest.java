package com.gmail.alexei28.shortcut.hibernate.task1.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


class CommentDTOTest {
    @Test
    void simpleEqualsContract() {
        EqualsVerifier.simple().forClass(CommentDTO.class).verify();
    }

}