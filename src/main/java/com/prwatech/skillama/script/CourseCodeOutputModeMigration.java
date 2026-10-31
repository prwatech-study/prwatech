package com.prwatech.skillama.script;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.repository.CourseRepository;
import com.prwatech.skillama.service.CompilerCatalog;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Backfill {@link Course#getCodeOutputMode()} and {@link Course#getCompilerLanguage()}.
 * A blank language is filled from the course name once. When that fill matches a real
 * sandbox, an existing {@code ai} mode is switched to {@code compiler} on that same pass.
 * Later admin edits stick, because the language is no longer blank.
 */
@Component
@Order(50)
@RequiredArgsConstructor
public class CourseCodeOutputModeMigration implements CommandLineRunner {

    private static final Logger LOGGER = LoggerFactory.getLogger(CourseCodeOutputModeMigration.class);

    private final CourseRepository courseRepository;

    @Override
    public void run(String... args) {
        int updated = 0;
        for (Course course : courseRepository.findAll()) {
            if (apply(course)) {
                courseRepository.save(course);
                updated++;
            }
        }
        if (updated > 0) {
            LOGGER.info("Backfilled codeOutputMode for {} course(s)", updated);
        }
    }

    static boolean apply(Course course) {
        CompilerCatalog.Profile profile = CompilerCatalog.resolve(course.getCompilerLanguage(), course.getName());
        boolean dirty = false;
        if ((course.getCompilerLanguage() == null || course.getCompilerLanguage().isBlank())
                && profile.kind() != CompilerCatalog.Kind.NONE
                && profile.languageId() != null) {
            course.setCompilerLanguage(profile.languageId());
            dirty = true;
            if (!"compiler".equalsIgnoreCase(course.getCodeOutputMode())) {
                course.setCodeOutputMode("compiler");
            }
        }
        if (course.getCodeOutputMode() == null || course.getCodeOutputMode().isBlank()) {
            course.setCodeOutputMode(profile.kind() == CompilerCatalog.Kind.NONE ? "ai" : "compiler");
            dirty = true;
        }
        return dirty;
    }
}
