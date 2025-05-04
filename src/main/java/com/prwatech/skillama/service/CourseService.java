package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepo;
    private final MongoTemplate skillamaMongoTemplate;

    public CourseService(CourseRepository courseRepository, CourseCurriculumRepository curriculumRepo, @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.courseRepository = courseRepository;
        this.curriculumRepo = curriculumRepo;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public Course create(Course course) {
        course.setCreatedAt(LocalDateTime.now());
        return courseRepository.save(course);
    }

    public Optional<Course> findById(String id) {
        return courseRepository.findById(id);
    }

    public Page<Course> findAll(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        return courseRepository.findAll(pageable);
    }

    public List<Course> findAll() {
        return courseRepository.findAll();
    }

    public Course update(String id, Course updated) {
        return courseRepository.findById(id).map(existing -> {
            existing.setName(updated.getName());
            existing.setDescription(updated.getDescription());
            existing.setUpdatedBy(updated.getUpdatedBy());
            existing.setUpdatedAt(LocalDateTime.now());
            return courseRepository.save(existing);
        }).orElse(null);
    }

    public void delete(String id) {
        courseRepository.deleteById(id);
    }

    // Fetch course with curriculum
    public Optional<CourseWithCurriculum> findCourseWithCurriculum(String id) {
        Optional<Course> courseOpt = courseRepository.findById(id);
        if (courseOpt.isPresent()) {
            Course course = courseOpt.get();
            List<CourseCurriculum> curriculumList = getCurriculumByCourseIdOrdered(id);
            return Optional.of(new CourseWithCurriculum(course, curriculumList));
        }
        return Optional.empty();
    }

    // --- MODULE (CURRICULUM) MANAGEMENT ---
    public CourseCurriculum addModule(CourseCurriculum module) {
        module.setCreatedAt(LocalDateTime.now());
        module.setUpdatedAt(LocalDateTime.now());
        return curriculumRepo.save(module);
    }

    public CourseCurriculum updateModule(String moduleId, CourseCurriculum updated) {
        return curriculumRepo.findById(moduleId).map(existing -> {
            existing.setModuleName(updated.getModuleName());
            existing.setModuleAssetPath(updated.getModuleAssetPath());
            existing.setSubmodules(updated.getSubmodules());
            existing.setUpdatedBy(updated.getUpdatedBy());
            existing.setUpdatedAt(LocalDateTime.now());
            return curriculumRepo.save(existing);
        }).orElse(null);
    }

    public void removeModule(String moduleId) {
        curriculumRepo.deleteById(moduleId);
    }

    // When fetching modules for a course, sort by 'order' field
    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId) {
        return curriculumRepo.findByCourseIdOrderByOrderAsc(courseId);
    }

    // --- SUBMODULE MANAGEMENT ---
    public CourseCurriculum addSubmodule(String moduleId, CourseCurriculum.Submodule submodule) {
        return curriculumRepo.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list == null) list = new java.util.ArrayList<>();
            list.add(submodule);
            module.setSubmodules(list);
            module.setUpdatedAt(LocalDateTime.now());
            return curriculumRepo.save(module);
        }).orElse(null);
    }

    public CourseCurriculum updateSubmodule(String moduleId, int submoduleIdx, CourseCurriculum.Submodule updatedSubmodule) {
        return curriculumRepo.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                list.set(submoduleIdx, updatedSubmodule);
                module.setSubmodules(list);
                module.setUpdatedAt(LocalDateTime.now());
                return curriculumRepo.save(module);
            }
            return module;
        }).orElse(null);
    }

    public CourseCurriculum removeSubmodule(String moduleId, int submoduleIdx) {
        return curriculumRepo.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                list.remove(submoduleIdx);
                module.setSubmodules(list);
                module.setUpdatedAt(LocalDateTime.now());
                return curriculumRepo.save(module);
            }
            return module;
        }).orElse(null);
    }

    @Getter
    @AllArgsConstructor
    public static class CourseWithCurriculum {
        private Course course;
        private List<CourseCurriculum> curriculum;
    }
}
