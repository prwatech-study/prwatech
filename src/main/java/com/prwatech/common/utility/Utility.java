package com.prwatech.common.utility;

import static com.prwatech.common.Constants.MAX_LIMIT;
import static com.prwatech.common.Constants.MIN_LIMIT;

import com.prwatech.common.Constants;
import com.prwatech.common.dto.PaginationDto;
import java.util.List;
import java.util.Random;

import com.prwatech.courses.model.Pricing;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Pageable;

public class Utility {

  private static final String CHARACTERS =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

  public static Integer createRandomOtp() {
    Random random = new Random();
    return random.nextInt(MIN_LIMIT, MAX_LIMIT);
  }

  public static PaginationDto getPaginatedResponse(
      List<Object> responseList,
      Pageable pageable,
      Integer totalPages,
      Boolean hasNext,
      Long totalElements) {
    return new PaginationDto(
        responseList.get(Constants.PAGINATION_UTIL_LIST_DEFAULT_INDEX),
        totalPages,
        pageable.hasPrevious(),
        hasNext,
        pageable.getPageSize(),
        pageable.getPageNumber(),
        totalElements);
  }

  public static PaginationDto getPaginatedResponse(
      Object response, Pageable pageable, Integer totalPages, Boolean hasNext, Long totalElements) {
    return new PaginationDto(
        response,
        totalPages,
        pageable.hasPrevious(),
        hasNext,
        pageable.getPageSize(),
        pageable.getPageNumber(),
        totalElements);
  }

  public static String generateRandomString(int length) {
    Random random = new Random();
    StringBuilder sb = new StringBuilder(length);
    for (int i = 0; i < length; i++) {
      sb.append(CHARACTERS.charAt(random.nextInt(CHARACTERS.length())));
    }
    return sb.toString();
  }

  public static Pricing zeroPricing(String courseId) {
    Pricing zeroPricing = new Pricing();
    zeroPricing.setCourse_Id(new ObjectId(courseId));
    zeroPricing.setCourse_Type("Online");
    zeroPricing.setActual_Price(0);
    zeroPricing.setDiscounted_Price(0);

    return zeroPricing;
  }
}
