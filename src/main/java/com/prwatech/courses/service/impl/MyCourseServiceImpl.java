package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.CourseTypeProjection;
import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.model.MyCourses;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseDetailsRepositoryTemplate;
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
  private final CourseDetailsRepositoryTemplate courseDetailsRepositoryTemplate;
  private final CourseDetailRepository courseDetailRepository;

  private final CourseTrackTemplate courseTrackTemplate;
  private final CourseTrackRepository courseTrackRepository;

  @Override
  public List<MyCourses> getAllCoursesOfUser(String userId) {
    return myCoursesRepository.findAll();
  }

  @Override
  public MyDashboardActivity getUserDashboardActivityByUserId(String userId) {

    List<CourseTrack> courseTrackList = courseTrackTemplate.getAllUsersCourseByUserId(new ObjectId(userId));


    List<String> courseIds = courseTrackList.stream().map(CourseTrack::getCourseId).map(String::valueOf).collect(Collectors.toList());
    Integer enrolledCourses = courseTrackList.stream().filter(courseTrack -> courseTrack.getIsAllCompleted().equals(Boolean.FALSE))
            .collect(Collectors.toList()).size();

    //Get course details and filter out classroom and online course.

    List<CourseTypeProjection> courseTypeProjections = courseDetailsRepositoryTemplate.getCourseTypeByCourseId(courseIds);

    List<String> allTypes=courseTypeProjections.stream().
            flatMap(courseTypeProjection -> courseTypeProjection.getGetCourse_Type().stream())
            .collect(Collectors.toList());

    Integer onlineCourses = allTypes.stream().filter(type-> type.equals("Online")).collect(Collectors.toList()).size();
    Integer classroomCourses = allTypes.stream().filter(type-> type.equals("Classroom")).collect(Collectors.toList()).size();


    Integer completedCourses =
             courseTrackList.stream()
                    .filter(courseTrack -> courseTrack.getIsAllCompleted()
                            .equals(Boolean.TRUE))
                     .collect(Collectors.toList()).size();

    return new MyDashboardActivity(
        enrolledCourses, onlineCourses, classroomCourses, completedCourses);
  }
}
