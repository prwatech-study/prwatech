package com.prwatech.courses.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;

@Service
@Transactional
public interface FileServices {


    ByteArrayInputStream generateCertificateByUserIdAndCourseId(String userId, String courseId);
}
