package com.prwatech.courses.service;

import com.prwatech.courses.dto.MyDashboardActivity;
import com.prwatech.courses.model.MyCourses;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface MyCourseService {
  List<MyCourses> getAllCoursesOfUser(String userId);

  MyDashboardActivity getUserDashboardActivityByUserId(String userId);
}
