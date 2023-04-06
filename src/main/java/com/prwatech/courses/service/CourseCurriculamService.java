package com.prwatech.courses.service;

import com.prwatech.courses.model.CourseCurriculam;
import java.util.List;
import org.bson.types.ObjectId;

public interface CourseCurriculamService {

  List<CourseCurriculam> getAllCurriculambyCourseId(ObjectId courseId);
}
