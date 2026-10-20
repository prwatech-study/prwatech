package com.prwatech.skillama.script;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * Backfill {@link Course#getCodeOutputMode()} for existing courses.
 * Python / Data Science → compiler; everything else → ai.
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
            if (course.getCodeOutputMode() != null && !course.getCodeOutputMode().isBlank()) {
                continue;
            }
            String mode = inferMode(course.getName());
            course.setCodeOutputMode(mode);
            courseRepository.save(course);
            updated++;
        }
        if (updated > 0) {
            LOGGER.info("Backfilled codeOutputMode for {} course(s)", updated);
        }
    }

    private static String inferMode(String name) {
        if (name == null) return "ai";
        String n = name.toLowerCase(Locale.ROOT);
        if (n.contains("python") || n.contains("data science") || n.contains("data-science")) {
            return "compiler";
        }
        return "ai";
    }
}
