package com.prwatech.common.utility;

import static com.prwatech.common.Constants.MAX_LIMIT;
import static com.prwatech.common.Constants.MIN_LIMIT;

import com.prwatech.common.Constants;
import com.prwatech.common.dto.PaginationDto;
import java.util.List;
import java.util.Random;
import org.springframework.data.domain.Pageable;

public class Utility {

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
}
