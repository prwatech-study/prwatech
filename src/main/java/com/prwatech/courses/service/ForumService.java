package com.prwatech.courses.service;

import com.prwatech.courses.dto.ForumCourseDto;
import com.prwatech.courses.dto.ForumDto;
import java.util.List;

public interface ForumService {

  List<ForumDto> getForumList(String courseId);

  List<ForumCourseDto> getCoursesForFilterForum();
}
