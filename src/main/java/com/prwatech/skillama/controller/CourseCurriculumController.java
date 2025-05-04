package com.prwatech.skillama.controller;

import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.service.CourseCurriculumService;
import com.prwatech.skillama.service.CourseService;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/skillama/curricula")
@RequiredArgsConstructor
public class CourseCurriculumController {
    private final CourseCurriculumService curriculumService;
    private final CourseService courseService;
    private final CourseCurriculumRepository curriculumRepo;

    @PostMapping
    public ResponseEntity<CourseCurriculum> create(@RequestBody CourseCurriculum curriculum) {
        return ResponseEntity.ok(curriculumService.create(curriculum));
    }

    @GetMapping
    public ResponseEntity<Page<CourseCurriculum>> getAllCurricula(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String order
    ) {
        boolean desc = order.equalsIgnoreCase("desc");
        return ResponseEntity.ok(curriculumService.findAll(page, size, sortBy, desc));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CourseCurriculum> getById(@PathVariable String id) {
        return curriculumService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public ResponseEntity<CourseCurriculum> update(@PathVariable String id, @RequestBody CourseCurriculum curriculum) {
        CourseCurriculum updated = curriculumService.update(id, curriculum);
        if (updated == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        curriculumService.delete(id);
        return ResponseEntity.noContent().build();
    }

    // --- MODULE (CURRICULUM) MANAGEMENT ---
    @PostMapping("/module")
    public ResponseEntity<CourseCurriculum> addModule(@RequestBody CourseCurriculum module) {
        return ResponseEntity.ok(courseService.addModule(module));
    }

    @PutMapping("/module/{moduleId}")
    public ResponseEntity<CourseCurriculum> updateModule(@PathVariable String moduleId, @RequestBody CourseCurriculum updated) {
        CourseCurriculum result = courseService.updateModule(moduleId, updated);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/module/{moduleId}")
    public ResponseEntity<Void> removeModule(@PathVariable String moduleId) {
        courseService.removeModule(moduleId);
        return ResponseEntity.noContent().build();
    }

    // --- SUBMODULE MANAGEMENT ---
    @PostMapping("/module/{moduleId}/submodule")
    public ResponseEntity<CourseCurriculum> addSubmodule(@PathVariable String moduleId, @RequestBody CourseCurriculum.Submodule submodule) {
        CourseCurriculum result = courseService.addSubmodule(moduleId, submodule);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @PutMapping("/module/{moduleId}/submodule/{idx}")
    public ResponseEntity<CourseCurriculum> updateSubmodule(@PathVariable String moduleId, @PathVariable int idx, @RequestBody CourseCurriculum.Submodule submodule) {
        CourseCurriculum result = courseService.updateSubmodule(moduleId, idx, submodule);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/module/{moduleId}/submodule/{idx}")
    public ResponseEntity<CourseCurriculum> removeSubmodule(@PathVariable String moduleId, @PathVariable int idx) {
        CourseCurriculum result = courseService.removeSubmodule(moduleId, idx);
        if (result == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(result);
    }

    // Endpoint to get curriculum modules for a course in order
    @GetMapping("/{courseId}")
    public ResponseEntity<List<CourseCurriculum>> getCurriculumByCourseIdOrdered(@PathVariable String courseId) {
        return ResponseEntity.ok(courseService.getCurriculumByCourseIdOrdered(courseId));
    }
}
