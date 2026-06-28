package com.prwatech.skillama.config;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCodeOutputMode;
import com.prwatech.skillama.repository.CourseRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Set;

/**
 * One-time backfill for courses created before {@code codeOutputMode} existed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CourseCodeOutputModeMigration {

    private static final Set<String> LEGACY_COMPILER_COURSE_NAMES = Set.of(
            "python",
            "python course",
            "advanced python",
            "python testing",
            "data science");

    private final CourseRepository courseRepository;

    @EventListener(ApplicationReadyEvent.class)
    public void backfillMissingCodeOutputModes() {
        int updated = 0;
        for (Course course : courseRepository.findAll()) {
            if (StringUtils.hasText(course.getCodeOutputMode())) {
                continue;
            }
            String mode = LEGACY_COMPILER_COURSE_NAMES.contains(normalizeName(course.getName()))
                    ? CourseCodeOutputMode.COMPILER
                    : CourseCodeOutputMode.AI;
            course.setCodeOutputMode(mode);
            courseRepository.save(course);
            updated++;
        }
        if (updated > 0) {
            log.info("Backfilled codeOutputMode for {} course(s)", updated);
        }
    }

    private static String normalizeName(String name) {
        if (!StringUtils.hasText(name)) {
            return "";
        }
        return name.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
