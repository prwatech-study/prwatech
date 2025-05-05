package com.prwatech.skillama.service;

import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class CourseCurriculumService {
    private final CourseCurriculumRepository curriculumRepository;
    private final MongoTemplate skillamaMongoTemplate;

    public CourseCurriculumService(CourseCurriculumRepository curriculumRepository, @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.curriculumRepository = curriculumRepository;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public CourseCurriculum create(CourseCurriculum curriculum) {
        curriculum.setCreatedAt(LocalDateTime.now());
        return curriculumRepository.save(curriculum);
    }

    public Optional<CourseCurriculum> findById(String id) {
        return curriculumRepository.findById(id);
    }

}
