package com.prwatech.courses.service;

import com.prwatech.courses.dto.CourseCurriCulamDto;
import com.prwatech.courses.model.CourseCurriculam;
import java.util.List;
import org.bson.types.ObjectId;

public interface CourseCurriculamService {

  CourseCurriCulamDto getAllCurriculambyCourseId(ObjectId courseId, String userId);
}
