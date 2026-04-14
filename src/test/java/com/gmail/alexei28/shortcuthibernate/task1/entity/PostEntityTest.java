package com.gmail.alexei28.shortcuthibernate.task1.entity;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.jupiter.api.Test;

import static nl.jqno.equalsverifier.Warning.SURROGATE_KEY;

class PostEntityTest {

    @Test
    void simpleEqualsContract() {
        EqualsVerifier.forClass(PostEntity.class)
                .suppress(SURROGATE_KEY)
                .verify();
    }
}