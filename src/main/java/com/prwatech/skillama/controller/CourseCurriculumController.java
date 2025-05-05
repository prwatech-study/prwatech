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
@RequestMapping("/skillama/curriculum")
@RequiredArgsConstructor
public class CourseCurriculumController {
    private final CourseCurriculumService curriculumService;
    private final CourseService courseService;
    private final CourseCurriculumRepository curriculumRepo;

    @PostMapping("/module")
    public ResponseEntity<CourseCurriculum> addModule(@RequestBody CourseCurriculum module) {
        return ResponseEntity.ok(courseService.addModule(module));
    }

    @GetMapping("/{moduleId}")
    public ResponseEntity<CourseCurriculum> getById(@PathVariable String moduleId) {
        return curriculumService.findById(moduleId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
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
}
