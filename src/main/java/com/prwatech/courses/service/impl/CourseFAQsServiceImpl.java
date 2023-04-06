package com.prwatech.courses.service.impl;

import com.prwatech.courses.model.CourseFAQs;
import com.prwatech.courses.repository.CourseFAQsTemplate;
import com.prwatech.courses.service.CourseFAQsService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class CourseFAQsServiceImpl implements CourseFAQsService {

  private final CourseFAQsTemplate courseFAQsTemplate;

  @Override
  public List<CourseFAQs> getAllCourseFAQsByCourseId(ObjectId courseId) {
    return courseFAQsTemplate.getCourseFAQsByCourseId(courseId);
  }
}
