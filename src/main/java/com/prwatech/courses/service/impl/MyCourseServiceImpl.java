package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.model.MyCourses;
import com.prwatech.courses.repository.MyCoursesRepository;
import com.prwatech.courses.repository.MyCoursesTemplate;
import com.prwatech.courses.service.MyCourseService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyCourseServiceImpl implements MyCourseService {

  private final MyCoursesRepository myCoursesRepository;
  private final MyCoursesTemplate myCoursesTemplate;

  @Override
  public List<MyCourses> getAllCoursesOfUser(String userId) {
    return myCoursesRepository.findAll();
  }

  @Override
  public MyDashboardActivity getUserDashboardActivityByUserId(String userId) {
    List<MyCourses> myCoursesList = myCoursesTemplate.findCourseByUserId(new ObjectId(userId));

    Integer enrolledCourses = 0;
    Integer onlineCourses = 0;
    Integer classroomCourses = 0;
    Integer completedCourses = 0;

    for (MyCourses courses : myCoursesList) {
      switch (courses.getCourse_Type()) {
        case "Online":
          enrolledCourses++;
          break;
        case "Classroom":
          onlineCourses++;
          break;
        default:
          classroomCourses++;
          break;
      }
    }

    return new MyDashboardActivity(
        enrolledCourses, onlineCourses, classroomCourses, completedCourses);
  }
}
