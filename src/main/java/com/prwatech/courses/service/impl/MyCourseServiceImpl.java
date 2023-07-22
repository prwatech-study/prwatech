package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.model.MyCourses;
import com.prwatech.courses.repository.CourseTrackRepository;
import com.prwatech.courses.repository.CourseTrackTemplate;
import com.prwatech.courses.repository.MyCoursesRepository;
import com.prwatech.courses.repository.MyCoursesTemplate;
import com.prwatech.courses.service.MyCourseService;
import java.util.List;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class MyCourseServiceImpl implements MyCourseService {

  private final MyCoursesRepository myCoursesRepository;
  private final MyCoursesTemplate myCoursesTemplate;

  private final CourseTrackTemplate courseTrackTemplate;
  private final CourseTrackRepository courseTrackRepository;

  @Override
  public List<MyCourses> getAllCoursesOfUser(String userId) {
    return myCoursesRepository.findAll();
  }

  @Override
  public MyDashboardActivity getUserDashboardActivityByUserId(String userId) {

    List<CourseTrack> courseTrackList = courseTrackTemplate.getByUserId(new ObjectId(userId));

    Integer enrolledCourses = courseTrackList.size();
    Integer onlineCourses = 0;
    Integer classroomCourses = 0;
    Integer completedCourses =
             courseTrackList.stream()
                    .filter(courseTrack -> courseTrack.getIsAllCompleted()
                            .equals(Boolean.TRUE))
                     .collect(Collectors.toList()).size();

    return new MyDashboardActivity(
        enrolledCourses, onlineCourses, classroomCourses, completedCourses);
  }
}
