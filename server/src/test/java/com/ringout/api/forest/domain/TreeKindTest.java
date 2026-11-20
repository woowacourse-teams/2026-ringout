package com.ringout.api.forest.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TreeKindTest {

    @ParameterizedTest
    @CsvSource({"pine, PINE", "oak, OAK", "big_tree, BIG_TREE"})
    void 나무_종류_값으로_종류를_찾는다(String value, TreeKind expected) {
        // when
        TreeKind kind = TreeKind.from(value);

        // then
        assertThat(kind).isEqualTo(expected);
        assertThat(kind.getValue()).isEqualTo(value);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "PINE", "big-tree", "maple"})
    void 정의되지_않은_나무_종류_값이면_종류가_없다(String value) {
        // when
        TreeKind kind = TreeKind.from(value);

        // then
        assertThat(kind).isNull();
    }
}
