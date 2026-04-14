package com.gmail.alexei28.shortcuthibernate.task1.entity;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static nl.jqno.equalsverifier.Warning.SURROGATE_KEY;


class CommentEntityTest {
    @Test
    void simpleEqualsContract() {
        EqualsVerifier.forClass(CommentEntity.class)
                .suppress(SURROGATE_KEY)
                .verify();
    }

}