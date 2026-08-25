package com.ringout.api.user.utils;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public final class NicknameGenerator {

  private static final List<String> ADJECTIVES = List.of(
      "귀여운", "용감한", "즐거운", "씩씩한", "상냥한",
      "신나는", "따뜻한", "빛나는", "느긋한", "재빠른"
  );

  private static final List<String> NOUNS = List.of(
      "토끼", "여우", "고양이", "강아지", "다람쥐",
      "수달", "판다", "참새", "펭귄", "알파카"
  );

  private static final int NUMBER_BOUND = 10_000;

  private NicknameGenerator() {
  }

  public static String generate() {
    ThreadLocalRandom random = ThreadLocalRandom.current();
    String adjective = ADJECTIVES.get(random.nextInt(ADJECTIVES.size()));
    String noun = NOUNS.get(random.nextInt(NOUNS.size()));
    String number = "%04d".formatted(random.nextInt(NUMBER_BOUND));

    return adjective + noun + number;
  }
}
