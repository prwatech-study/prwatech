package com.prwatech.user.dto;

import com.prwatech.user.model.UserEducationDetails;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileDto extends UserDetailsDto {

  private Integer enrolledCourse;
  private Integer onlineCourse;
  private Integer classRoomCourse;
  private Integer completedCourse;
  private Integer eventsAttended;
  private Integer enrollQuiz;
  private Integer certificate;
  private Integer wallet;
  private String Resume_URL;
  private UserEducationDetails educationDetails;
}
