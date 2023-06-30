package com.prwatech.courses.service;

import com.prwatech.common.dto.PaginationDto;
import com.prwatech.courses.dto.RegisterWebinarRequestDto;
import com.prwatech.courses.model.Webinar;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Transactional
public interface WebinarService {

    PaginationDto getAllWebinar(Integer pageNumber, Integer pageSize);

    Webinar getWebinarById(String id);

    void registerWebinar(RegisterWebinarRequestDto webinarRequestDto);

    List<Webinar> getAllRegisteredWebinar(String email);
}
