package com.prwatech.common.utility;

import static com.prwatech.common.Constants.MAX_LIMIT;
import static com.prwatech.common.Constants.MIN_LIMIT;

import java.util.Random;

public class Utility {

  public static Integer createRandomOtp() {
    Random random = new Random();
    return random.nextInt(MAX_LIMIT, MIN_LIMIT);
  }
}
