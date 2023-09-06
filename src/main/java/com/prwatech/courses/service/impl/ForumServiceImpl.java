package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.ForumCourseDto;
import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseForum;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.ForumRepository;
import com.prwatech.courses.service.ForumService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ForumServiceImpl implements ForumService {

  private final ForumRepository forumRepository;
  private final MongoTemplate mongoTemplate;
  private final CourseDetailRepository courseDetailRepository;

  private static final org.slf4j.Logger LOGGER =
          org.slf4j.LoggerFactory.getLogger(ForumServiceImpl.class);

  @Override
  public List<ForumDto> getForumList(String courseId) {

    Query query = new Query();
    if (Objects.nonNull(courseId)) {
      ObjectId course_id=new ObjectId(courseId);
      query.addCriteria(Criteria.where("Course_Id").is(course_id));
    }

    return mongoTemplate.find(query, CourseForum.class).stream()
        .map(
            courseForum ->
                new ForumDto(
                    courseForum.getId(),
                    courseForum.getQuestion(),
                    courseForum.getConversation(),
                    courseForum.getLastUpdated(),
                    courseForum.getQuestionBy()))
        .collect(Collectors.toList());
  }

  @Override
  public List<ForumCourseDto> getCoursesForFilterForum() {

    List<CourseForum> forumList = getAllForum();
    List<ForumCourseDto> forumCourseDtoList = new ArrayList<>();

    for(CourseForum courseForum: forumList){

      CourseDetails courseDetails = courseDetailRepository.findById(courseForum.getCourse_Id().toString()).orElse(null);

      String courseName = (Objects.nonNull(courseDetails))?courseDetails.getCourse_Title():null;
      if(courseName!=null){
      forumCourseDtoList.add(new ForumCourseDto(courseForum.getCourse_Id().toString(), courseName));
    }
    }
    return forumCourseDtoList;
  }

  private List<CourseForum> getAllForum(){
    return forumRepository.findAll();
  }
}
