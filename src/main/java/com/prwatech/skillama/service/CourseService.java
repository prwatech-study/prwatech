package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
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
    private final CourseCurriculumRepository curriculumRepository;
    private final MongoTemplate skillamaMongoTemplate;

    public CourseService(CourseRepository courseRepository, CourseCurriculumRepository curriculumRepo, @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.courseRepository = courseRepository;
        this.curriculumRepository = curriculumRepo;
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
            existing.setThumbnail(updated.getThumbnail());
            existing.setUpdatedBy(updated.getUpdatedBy());
            if (updated.getIsGuestCourse() != null) {
                existing.setIsGuestCourse(updated.getIsGuestCourse());
            }
            if (updated.getIsPublic() != null) {
                existing.setIsPublic(updated.getIsPublic());
            }
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
        return curriculumRepository.save(module);
    }

    public CourseCurriculum updateModule(String moduleId, CourseCurriculum updated) {
        return curriculumRepository.findById(moduleId).map(existing -> {
            existing.setModuleName(updated.getModuleName());
            existing.setModuleAssetPath(updated.getModuleAssetPath());
            existing.setSubmodules(updated.getSubmodules());
            existing.setUpdatedBy(updated.getUpdatedBy());
            existing.setUpdatedAt(LocalDateTime.now());
            return curriculumRepository.save(existing);
        }).orElse(null);
    }

    public void removeModule(String moduleId) {
        curriculumRepository.deleteById(moduleId);
    }

    // When fetching modules for a course, sort by 'order' field
    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId) {
        return curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
    }

    // Get curriculum for a course (returns full curriculum for all users including guests)
    // Note: Guest users see full curriculum but only first lecture is unlocked (handled by UserProfileService)
    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId, boolean guestAccess) {
        // Return full curriculum for all users - access control is handled by UserProfileService
        return curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
    }

    // --- GUEST COURSE MANAGEMENT ---
    /**
     * Finds the guest course. First tries to find a course marked as isGuestCourse,
     * then falls back to the first public course if no guest course is found.
     * @return Optional containing the guest course, or empty if none found
     */
    public Optional<Course> findGuestCourse() {
        Optional<Course> guestCourse = courseRepository.findByIsGuestCourseTrue();
        if (guestCourse.isPresent()) {
            return guestCourse;
        }
        // Fallback to first public course
        return courseRepository.findFirstByIsPublicTrue();
    }

    /**
     * Gets the guest course or throws exception if not found
     * @return Course - the guest course
     * @throws NotFoundException if no guest course is found
     */
    public Course getGuestCourseOrThrow() {
        return findGuestCourse()
                .orElseThrow(() -> new NotFoundException(
                        "No guest course found. Please configure a default guest course with isGuestCourse=true or isPublic=true."));
    }

    /**
     * Gets guest course curriculum (full curriculum - all modules visible but only first lecture unlocked)
     * @return List of CourseCurriculum - contains all modules
     * @throws NotFoundException if guest course not found or curriculum is empty
     */
    public List<CourseCurriculum> getGuestCourseCurriculum() {
        Course guestCourse = getGuestCourseOrThrow();
        List<CourseCurriculum> curriculum = getCurriculumByCourseIdOrdered(guestCourse.getId(), true);
        
        if (curriculum == null || curriculum.isEmpty()) {
            throw new NotFoundException(
                    "Guest course curriculum is empty. Please add at least one module to the guest course.");
        }
        
        return curriculum;
    }

    /**
     * Gets all public courses
     * @return List of public courses
     */
    public List<Course> findPublicCourses() {
        return courseRepository.findByIsPublicTrue();
    }

    /**
     * Gets the first public course (default for guest access)
     * @return Optional containing the first public course, or empty if none found
     */
    public Optional<Course> findFirstPublicCourse() {
        return courseRepository.findFirstByIsPublicTrue();
    }

    // --- SUBMODULE MANAGEMENT ---
    public CourseCurriculum addSubmodule(String moduleId, CourseCurriculum.Submodule submodule) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list == null) list = new java.util.ArrayList<>();
            list.add(submodule);
            module.setSubmodules(list);
            module.setUpdatedAt(LocalDateTime.now());
            return curriculumRepository.save(module);
        }).orElse(null);
    }

    public CourseCurriculum updateSubmodule(String moduleId, int submoduleIdx, CourseCurriculum.Submodule updatedSubmodule) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                list.set(submoduleIdx, updatedSubmodule);
                module.setSubmodules(list);
                module.setUpdatedAt(LocalDateTime.now());
                return curriculumRepository.save(module);
            }
            return module;
        }).orElse(null);
    }

    public CourseCurriculum removeSubmodule(String moduleId, int submoduleIdx) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                list.remove(submoduleIdx);
                module.setSubmodules(list);
                module.setUpdatedAt(LocalDateTime.now());
                return curriculumRepository.save(module);
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
