package com.gmail.alexei28.shortcuthibernate.task1.dto;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;


class CommentDTOTest {
    @Test
    void simpleEqualsContract() {
        EqualsVerifier.simple().forClass(CommentDTO.class).verify();
    }

}