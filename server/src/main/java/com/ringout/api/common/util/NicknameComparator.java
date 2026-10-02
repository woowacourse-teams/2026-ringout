package com.ringout.api.common.util;

import java.util.Comparator;
import java.util.function.Function;

public final class NicknameComparator {

    private NicknameComparator() {
    }

    public static <T> Comparator<T> comparing(Function<T, String> nicknameExtractor) {
        return Comparator.comparing(nicknameExtractor, NicknameComparator::compare);
    }

    private static int compare(String left, String right) {
        int groupComparison = Integer.compare(nicknameGroup(left), nicknameGroup(right));
        if (groupComparison != 0) {
            return groupComparison;
        }
        return Comparator.nullsLast(String::compareTo).compare(left, right);
    }

    private static int nicknameGroup(String nickname) {
        if (nickname == null || nickname.isEmpty()) {
            return 2;
        }

        char firstCharacter = nickname.charAt(0);
        if (firstCharacter >= '\uAC00' && firstCharacter <= '\uD7A3') {
            return 0;
        }
        if ((firstCharacter >= 'A' && firstCharacter <= 'Z')
            || (firstCharacter >= 'a' && firstCharacter <= 'z')) {
            return 1;
        }
        return 2;
    }
}
