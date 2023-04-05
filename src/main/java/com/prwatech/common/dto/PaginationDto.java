package com.prwatech.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PaginationDto {
  private Object object;

  private Integer totalPages;

  private boolean hasPrevious;

  private boolean hasNext;

  private Integer pageSize;

  private Integer pageNumber;

  private Long totalElements;
}
