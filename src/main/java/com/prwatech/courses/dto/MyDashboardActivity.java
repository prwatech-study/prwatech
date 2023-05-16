package com.prwatech.courses.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class MyDashboardActivity {

  private Integer enrolledCourses;
  private Integer onlineCourses;
  private Integer classroomCourses;
  private Integer completedCourses;
}
