package com.prwatech.courses.service;

import com.prwatech.courses.model.CourseFAQs;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface CourseFAQsService {

  public List<CourseFAQs> getAllCourseFAQsByCourseId(ObjectId courseId);
}
