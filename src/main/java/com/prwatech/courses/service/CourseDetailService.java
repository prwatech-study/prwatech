package com.prwatech.courses.service;

import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.model.CourseDetails;
import java.util.List;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses();

  CourseDetails getCourseDescriptionById(String id);
}
