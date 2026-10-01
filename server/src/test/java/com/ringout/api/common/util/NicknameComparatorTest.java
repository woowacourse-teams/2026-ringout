package com.ringout.api.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NicknameComparatorTest {

    @Nested
    class 닉네임_그룹_정렬_규칙 {

        @Test
        void 한글과_영문_및_그_외_문자를_지정된_그룹순으로_정렬한다() {
            // given
            List<String> nicknames = List.of("@runner", "Alice", "방장", "bob", "가나다");

            // when
            List<String> sortedNicknames = nicknames.stream()
                .sorted(NicknameComparator.comparing(nickname -> nickname))
                .toList();

            // then
            assertThat(sortedNicknames).containsExactly("가나다", "방장", "Alice", "bob", "@runner");
        }

        @Test
        void 같은_첫_글자인_닉네임은_전체_문자열로_정렬한다() {
            // given
            List<String> nicknames = List.of("가다", "Alice", "가나", "Adam");

            // when
            List<String> sortedNicknames = nicknames.stream()
                .sorted(NicknameComparator.comparing(nickname -> nickname))
                .toList();

            // then
            assertThat(sortedNicknames).containsExactly("가나", "가다", "Adam", "Alice");
        }
    }
}
