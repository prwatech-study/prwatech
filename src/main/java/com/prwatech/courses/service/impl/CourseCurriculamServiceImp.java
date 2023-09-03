package com.prwatech.courses.service.impl;

import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.courses.dto.CourseCurriCulamDto;
import com.prwatech.courses.model.CourseCurriculam;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.repository.CourseCurriculamTemplate;
import com.prwatech.courses.repository.CourseTrackTemplate;
import com.prwatech.courses.service.CourseCurriculamService;
import java.util.List;
import java.util.Objects;

import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class CourseCurriculamServiceImp implements CourseCurriculamService {

  private final CourseCurriculamTemplate courseCurriculamTemplate;
  private final CourseTrackTemplate courseTrackTemplate;

  @Override
  public CourseCurriCulamDto getAllCurriculambyCourseId(ObjectId courseId, String userId) {
    if(courseId==null){
      throw new UnProcessableEntityException("Course Id can not be null!");
    }

    Boolean isCompleted=Boolean.FALSE;
    CourseCurriculam courseCurriculam = courseCurriculamTemplate.getAllCurriculamByCourseId(courseId);
    Integer currentVideo=1;
    if(userId!=null){
      CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(new ObjectId(userId), courseId);
      if(Objects.nonNull(courseTrack)){
        currentVideo=courseTrack.getCurrentItem();
        if(currentVideo.equals(courseCurriculam.getCourse_Curriculam().size())){
          isCompleted=Boolean.TRUE;
        }
      }
    }
    return CourseCurriCulamDto.builder().courseCurriculam(courseCurriculam).currentVideo(currentVideo)
            .isCompleted(isCompleted)
            .build();

  }
}
