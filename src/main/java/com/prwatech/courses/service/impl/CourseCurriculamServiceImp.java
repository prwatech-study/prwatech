package com.prwatech.courses.service.impl;

import com.prwatech.courses.model.CourseCurriculam;
import com.prwatech.courses.repository.CourseCurriculamTemplate;
import com.prwatech.courses.service.CourseCurriculamService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class CourseCurriculamServiceImp implements CourseCurriculamService {

  private final CourseCurriculamTemplate courseCurriculamTemplate;

  @Override
  public List<CourseCurriculam> getAllCurriculambyCourseId(ObjectId courseId) {
    return courseCurriculamTemplate.getAllCurriculamByCourseId(courseId);
  }
}
