package com.prwatech.courses.repository;

import com.prwatech.courses.model.Webinar;
import com.prwatech.courses.model.WebinarRegister;
import lombok.AllArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import java.util.List;

@AllArgsConstructor
@Component
public class WebinarTemplate {

    private final MongoTemplate mongoTemplate;
    private final WebinarRepository webinarRepository;

    public Page<Webinar> getAllWebinar(Integer pageNumber, Integer pageSize){
        String sortField = "Webinar_Date_Time";
        Sort.Direction sortDirection = Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, sortField);
        Pageable pageable = PageRequest.of(pageNumber, pageSize,sort);
        Query query = new Query().with(pageable);
        query.fields().exclude("Webinar_Description");
        Long count = mongoTemplate.count(query, Webinar.class);
        query.with(pageable);
        List<Webinar>webinarList = mongoTemplate.find(query, Webinar.class);
        return new PageImpl<>(webinarList, pageable, count);
    }


    public List<WebinarRegister> getAllRegisteredWebinar(String email){
        String sortField = "Created_On";
        Sort.Direction sortDirection = Sort.Direction.DESC;
        Sort sort = Sort.by(sortDirection, sortField);
        Query query = new Query().with(sort);
        query.addCriteria(Criteria.where("Email").is(email));
        List<WebinarRegister>webinarRegisterList = mongoTemplate.find(query, WebinarRegister.class);
        return webinarRegisterList;


    }
}
