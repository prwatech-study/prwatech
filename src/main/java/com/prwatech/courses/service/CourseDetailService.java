package com.prwatech.courses.service;

import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.Pricing;
import java.util.List;
import org.bson.types.ObjectId;

public interface CourseDetailService {

  List<CourseCardDto> getMostPopularCourses();

  CourseDetails getCourseDescriptionById(String id);

  Pricing getPriceByCourseId(ObjectId courseId);

  List<CourseCardDto> getSelfPlacedCourses();

  List<CourseCardDto> getFreeCourses();
}
