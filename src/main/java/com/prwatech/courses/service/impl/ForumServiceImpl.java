package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.model.CourseForum;
import com.prwatech.courses.repository.ForumRepository;
import com.prwatech.courses.service.ForumService;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class ForumServiceImpl implements ForumService {

  private final ForumRepository forumRepository;
  private final MongoTemplate mongoTemplate;

  @Override
  public List<ForumDto> getForumList(String courseId) {

    Query query = new Query();
    if (Objects.nonNull(courseId)) {
      query.addCriteria(Criteria.where("Course_Id").is(courseId));
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
}
