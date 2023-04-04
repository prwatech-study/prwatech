package com.prwatech.courses.service;

import com.prwatech.courses.dto.CourseCardDto;
import java.util.List;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses();

  List<CourseCardDto> getSelfPlacedCourses();
}
