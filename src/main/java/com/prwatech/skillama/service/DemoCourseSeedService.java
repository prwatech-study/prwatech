package com.prwatech.skillama.service;

import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.model.CourseCurriculum.Submodule;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Seeds the public no-login DEMO course (a small Python tour with theory + practical
 * lectures). Idempotent — returns the existing demo course if one is already configured.
 */
@Service
@RequiredArgsConstructor
public class DemoCourseSeedService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DemoCourseSeedService.class);

    private final CourseService courseService;

    public Course seedDemoCourse() {
        return courseService.findDemoCourse().orElseGet(this::createDemoCourse);
    }

    private Course createDemoCourse() {
        Course course = Course.builder()
                .name("Python — Skillama Demo")
                .description("A guided Python tour showcasing Skillama's AI Tutor, Code Execution, "
                        + "Debug, and Chat — with both theory and hands-on practical lectures.")
                .isDemo(Boolean.TRUE)
                .isPublic(Boolean.TRUE)
                .active(Boolean.TRUE)
                .build();
        Course saved = courseService.create(course);
        LOGGER.info("Seeded demo course id={}", saved.getId());

        int order = 0;
        addModule(saved.getId(), order++, "Getting Started with Python", List.of(
                theory("What is Python?"),
                theory("Variables & Data Types"),
                practical("Your First Program",
                        "print(\"Hello, Skillama!\")\n\n"
                        + "name = \"Python\"\n"
                        + "print(\"Learning\", name, \"the fun way\")")
        ));
        addModule(saved.getId(), order++, "Control Flow", List.of(
                theory("Conditionals (if / elif / else)"),
                theory("Loops (for & while)"),
                practical("FizzBuzz",
                        "for n in range(1, 16):\n"
                        + "    if n % 15 == 0:\n"
                        + "        print(\"FizzBuzz\")\n"
                        + "    elif n % 3 == 0:\n"
                        + "        print(\"Fizz\")\n"
                        + "    elif n % 5 == 0:\n"
                        + "        print(\"Buzz\")\n"
                        + "    else:\n"
                        + "        print(n)")
        ));
        addModule(saved.getId(), order++, "Functions & Data Structures", List.of(
                theory("Functions & Arguments"),
                theory("Lists, Tuples & Dictionaries"),
                practical("Word Counter",
                        "text = \"python is fun and python is powerful\"\n"
                        + "counts = {}\n"
                        + "for word in text.split():\n"
                        + "    counts[word] = counts.get(word, 0) + 1\n"
                        + "print(counts)")
        ));
        return saved;
    }

    private void addModule(String courseId, int order, String moduleName, List<Submodule> subs) {
        for (int i = 0; i < subs.size(); i++) {
            subs.get(i).setOrder(i);
        }
        CourseCurriculum module = CourseCurriculum.builder()
                .courseId(courseId)
                .moduleName(moduleName)
                .order(order)
                .submodules(new ArrayList<>(subs))
                .build();
        courseService.addModule(module);
    }

    private Submodule theory(String label) {
        Submodule s = new Submodule();
        s.setLabel(label);
        s.setPracticalRequired(false);
        s.setEnabled(Boolean.TRUE);
        return s;
    }

    private Submodule practical(String label, String scriptText) {
        Submodule s = new Submodule();
        s.setLabel(label);
        s.setPracticalRequired(true);
        s.setScriptText(scriptText);
        s.setEnabled(Boolean.TRUE);
        return s;
    }
}
