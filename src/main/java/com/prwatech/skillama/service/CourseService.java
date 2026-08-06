package com.prwatech.skillama.service;

import com.prwatech.common.exception.NotFoundException;
import com.prwatech.skillama.model.Course;
import com.prwatech.skillama.model.CourseCurriculum;
import com.prwatech.skillama.repository.CourseCurriculumRepository;
import com.prwatech.skillama.repository.CourseRepository;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import com.prwatech.skillama.util.IndiaTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Service
public class CourseService {
    private final CourseRepository courseRepository;
    private final CourseCurriculumRepository curriculumRepository;
    private final MongoTemplate skillamaMongoTemplate;

    public CourseService(
            CourseRepository courseRepository,
            CourseCurriculumRepository curriculumRepo,
            @Qualifier("skillamaMongoTemplate") MongoTemplate skillamaMongoTemplate) {
        this.courseRepository = courseRepository;
        this.curriculumRepository = curriculumRepo;
        this.skillamaMongoTemplate = skillamaMongoTemplate;
    }

    public static boolean isActive(Course course) {
        return course != null && course.getDeletedAt() == null;
    }

    /** Visible to learners: not archived AND not deactivated by an admin. */
    public static boolean isAvailableToLearner(Course course) {
        return isActive(course) && !Boolean.FALSE.equals(course.getActive());
    }

    /**
     * A course can only be registrationEligible while it is active — registration
     * visibility is a subset of assignability, never the reverse. Called from every
     * create/update path so the invalid combination (active=false, registrationEligible=true)
     * can never be persisted, regardless of what the caller submitted.
     */
    private static void normalizeRegistrationEligibility(Course course) {
        if (Boolean.FALSE.equals(course.getActive())) {
            course.setRegistrationEligible(Boolean.FALSE);
        }
    }

    private static Criteria activeCourseCriteria() {
        return new Criteria().orOperator(
                Criteria.where("deletedAt").is(null),
                Criteria.where("deletedAt").exists(false));
    }

    private static Criteria deletedCourseCriteria() {
        return Criteria.where("deletedAt").ne(null);
    }

    public Course create(Course course) {
        course.setCreatedAt(IndiaTime.now());
        if (course.getActive() == null) {
            course.setActive(Boolean.TRUE);
        }
        if (course.getRegistrationEligible() == null) {
            course.setRegistrationEligible(Boolean.TRUE);
        }
        normalizeRegistrationEligibility(course);
        return courseRepository.save(course);
    }

    public Optional<Course> findById(String id) {
        return courseRepository.findById(id);
    }

    public Optional<Course> findActiveById(String id) {
        return courseRepository.findById(id).filter(CourseService::isActive);
    }

    public void assertCourseActive(Course course) {
        if (course == null) {
            throw new NotFoundException("Course not found");
        }
        if (!isActive(course)) {
            throw new NotFoundException("Course is archived");
        }
    }

    public Page<Course> findAll(int page, int size, String sortBy, boolean desc) {
        return findAllActive(page, size, sortBy, desc);
    }

    public Page<Course> findAllActive(int page, int size, String sortBy, boolean desc) {
        Pageable pageable = PageRequest.of(page, size, desc ? Sort.Direction.DESC : Sort.Direction.ASC, sortBy);
        Query query = new Query(activeCourseCriteria());
        long total = skillamaMongoTemplate.count(query, Course.class);
        query.with(pageable);
        List<Course> content = skillamaMongoTemplate.find(query, Course.class);
        return new PageImpl<>(content, pageable, total);
    }

    public List<Course> findAllDeleted() {
        Query query = new Query(deletedCourseCriteria()).with(Sort.by(Sort.Direction.DESC, "deletedAt"));
        return skillamaMongoTemplate.find(query, Course.class);
    }

    public List<Course> findAll() {
        return findAllActiveList();
    }

    public List<Course> findAllActiveList() {
        return skillamaMongoTemplate.find(new Query(activeCourseCriteria()), Course.class);
    }

    public Course update(String id, Course updated) {
        return courseRepository.findById(id).map(existing -> {
            if (!isActive(existing)) {
                throw new IllegalStateException("Cannot update an archived course. Restore it first.");
            }
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
            if (updated.getActive() != null) {
                existing.setActive(updated.getActive());
            }
            if (updated.getRegistrationEligible() != null) {
                existing.setRegistrationEligible(updated.getRegistrationEligible());
            }
            normalizeRegistrationEligibility(existing);
            existing.setUpdatedAt(IndiaTime.now());
            return courseRepository.save(existing);
        }).orElse(null);
    }

    /** Soft-delete only — curriculum, enrollments, and progress are retained. */
    @Transactional
    public Course softDelete(String id, String deletedByUserId) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found"));
        if (!isActive(course)) {
            return course;
        }
        course.setDeletedAt(IndiaTime.now());
        course.setDeletedBy(deletedByUserId);
        course.setRestoredAt(null);
        course.setRestoredBy(null);
        course.setUpdatedAt(IndiaTime.now());
        course.setUpdatedBy(deletedByUserId);
        return courseRepository.save(course);
    }

    @Transactional
    public Course restore(String id, String restoredByUserId) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Course not found"));
        if (isActive(course)) {
            return course;
        }
        course.setDeletedAt(null);
        course.setDeletedBy(null);
        course.setRestoredAt(IndiaTime.now());
        course.setRestoredBy(restoredByUserId);
        course.setUpdatedAt(IndiaTime.now());
        course.setUpdatedBy(restoredByUserId);
        return courseRepository.save(course);
    }

    /** @deprecated Use {@link #softDelete(String, String)} — hard delete is not permitted. */
    @Deprecated
    @Transactional
    public void delete(String id) {
        softDelete(id, null);
    }

    // Fetch course with curriculum
    public Optional<CourseWithCurriculum> findCourseWithCurriculum(String id) {
        Optional<Course> courseOpt = findActiveById(id);
        if (courseOpt.isPresent()) {
            Course course = courseOpt.get();
            List<CourseCurriculum> curriculumList = getCurriculumByCourseIdOrdered(id);
            return Optional.of(new CourseWithCurriculum(course, curriculumList));
        }
        return Optional.empty();
    }

    // --- MODULE (CURRICULUM) MANAGEMENT ---
    public CourseCurriculum addModule(CourseCurriculum module) {
        if (module.getSubmodules() != null) {
            for (CourseCurriculum.Submodule s : module.getSubmodules()) {
                validatePracticalSubmoduleScript(s);
            }
        }
        module.setCreatedAt(IndiaTime.now());
        module.setUpdatedAt(IndiaTime.now());
        return curriculumRepository.save(module);
    }

    public CourseCurriculum updateModule(String moduleId, CourseCurriculum updated) {
        return curriculumRepository.findById(moduleId).map(existing -> {
            // A caller that omits submodules entirely (e.g. the admin "edit module name/order"
            // form, which only ever sends {moduleName, title, order}) means "I'm not touching
            // these," not "delete them all" — only overwrite when submodules were actually
            // provided. Deleting a submodule is a deliberate act with its own endpoint
            // (DELETE .../submodule/{idx}); it should never happen as a side effect of renaming
            // or reordering the module itself.
            if (updated.getSubmodules() != null) {
                for (CourseCurriculum.Submodule s : updated.getSubmodules()) {
                    validatePracticalSubmoduleScript(s);
                }
                existing.setSubmodules(updated.getSubmodules());
            }
            existing.setModuleName(updated.getModuleName());
            existing.setModuleAssetPath(updated.getModuleAssetPath());
            existing.setUpdatedBy(updated.getUpdatedBy());
            existing.setUpdatedAt(IndiaTime.now());
            return curriculumRepository.save(existing);
        }).orElse(null);
    }

    public void removeModule(String moduleId) {
        curriculumRepository.deleteById(moduleId);
    }

    // When fetching modules for a course, sort by 'order' field
    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId) {
        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        backfillSubmoduleIds(curriculum);
        applyPracticalScriptIntegrityWarnings(curriculum);
        return curriculum;
    }

    // Get curriculum for a course (learner/guest view filters disabled submodules)
    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId, boolean guestAccess) {
        return getCurriculumByCourseIdOrdered(courseId, guestAccess, false);
    }

    public List<CourseCurriculum> getCurriculumByCourseIdOrdered(String courseId, boolean guestAccess, boolean forAdmin) {
        List<CourseCurriculum> curriculum = curriculumRepository.findByCourseIdOrderByOrderAsc(courseId);
        backfillSubmoduleIds(curriculum);
        if (forAdmin) {
            applyPracticalScriptIntegrityWarnings(curriculum);
            return curriculum;
        }
        List<CourseCurriculum> learner = filterCurriculumForLearner(curriculum);
        if (guestAccess) {
            learner = applyGuestScriptRestrictions(learner);
        }
        applyPracticalScriptIntegrityWarnings(learner);
        return learner;
    }

    /**
     * Guest/teaser view: structure visible for all modules, but narration script only on the first enabled lecture.
     */
    static List<CourseCurriculum> applyGuestScriptRestrictions(List<CourseCurriculum> modules) {
        if (modules == null || modules.isEmpty()) {
            return List.of();
        }
        boolean firstLectureScriptRetained = false;
        List<CourseCurriculum> result = new ArrayList<>();
        for (CourseCurriculum module : modules) {
            if (module.getSubmodules() == null || module.getSubmodules().isEmpty()) {
                continue;
            }
            CourseCurriculum moduleCopy = new CourseCurriculum();
            moduleCopy.setId(module.getId());
            moduleCopy.setCourseId(module.getCourseId());
            moduleCopy.setModuleName(module.getModuleName());
            moduleCopy.setModuleAssetPath(module.getModuleAssetPath());
            moduleCopy.setTitle(module.getTitle());
            moduleCopy.setContent(module.getContent());
            moduleCopy.setOrder(module.getOrder());
            List<CourseCurriculum.Submodule> subs = new ArrayList<>();
            for (CourseCurriculum.Submodule sub : module.getSubmodules()) {
                if (!isSubmoduleEnabled(sub)) {
                    continue;
                }
                CourseCurriculum.Submodule copy = new CourseCurriculum.Submodule();
                copy.setId(sub.getId());
                copy.setLabel(sub.getLabel());
                copy.setImagePath(sub.getImagePath());
                copy.setPracticalRequired(sub.isPracticalRequired());
                copy.setOrder(sub.getOrder());
                copy.setEnabled(sub.getEnabled());
                copy.setDatasetId(sub.getDatasetId());
                if (!firstLectureScriptRetained) {
                    copy.setScriptText(sub.getScriptText());
                    firstLectureScriptRetained = true;
                }
                subs.add(copy);
            }
            if (!subs.isEmpty()) {
                moduleCopy.setSubmodules(subs);
                result.add(moduleCopy);
            }
        }
        return result;
    }

    public static void validatePracticalSubmoduleScript(CourseCurriculum.Submodule submodule) {
        if (submodule == null) {
            return;
        }
        if (submodule.isPracticalRequired() && !StringUtils.hasText(submodule.getScriptText())) {
            throw new IllegalArgumentException(
                    "Practical topics must include non-empty scriptText (required for narration / text-to-audio).");
        }
    }

    public static boolean isSubmoduleEnabled(CourseCurriculum.Submodule submodule) {
        return submodule == null || submodule.getEnabled() == null || Boolean.TRUE.equals(submodule.getEnabled());
    }

    public static int countEnabledLectures(List<CourseCurriculum> curriculum) {
        if (curriculum == null) {
            return 0;
        }
        return curriculum.stream()
                .mapToInt(m -> m.getSubmodules() == null ? 0
                        : (int) m.getSubmodules().stream().filter(CourseService::isSubmoduleEnabled).count())
                .sum();
    }

    /** Progress percent capped at 100; completed count capped at total enabled lectures. */
    public static int calculateProgressPercent(int completedLectures, int totalLectures) {
        if (totalLectures <= 0) {
            return 0;
        }
        int cappedCompleted = Math.min(Math.max(completedLectures, 0), totalLectures);
        return Math.min(100, (int) Math.round(cappedCompleted * 100.0 / totalLectures));
    }

    public static int clampStoredProgressPercent(Integer progress) {
        if (progress == null) {
            return 0;
        }
        return Math.min(100, Math.max(0, progress));
    }

    private static CourseCurriculum.Submodule copySubmoduleForLearner(CourseCurriculum.Submodule submodule) {
        CourseCurriculum.Submodule copy = new CourseCurriculum.Submodule();
        copy.setId(submodule.getId());
        copy.setLabel(submodule.getLabel());
        copy.setImagePath(submodule.getImagePath());
        copy.setPracticalRequired(submodule.isPracticalRequired());
        copy.setOrder(submodule.getOrder());
        // Narration / TTS (e.g. text_to_audio) needs the script; learners must receive the same text admins store.
        copy.setScriptText(submodule.getScriptText());
        copy.setEnabled(submodule.getEnabled());
        copy.setDatasetId(submodule.getDatasetId());
        return copy;
    }

    /**
     * Annotates submodules in-place with {@link CourseCurriculum.Submodule#setContentIntegrityIssueCode} when a
     * practical topic is missing scriptText (API-only; {@code @Transient} in Mongo).
     */
    public static void applyPracticalScriptIntegrityWarnings(List<CourseCurriculum> modules) {
        if (modules == null) {
            return;
        }
        for (CourseCurriculum module : modules) {
            if (module.getSubmodules() == null) {
                continue;
            }
            for (CourseCurriculum.Submodule s : module.getSubmodules()) {
                applyPracticalScriptIssueMetadata(s);
            }
        }
    }

    private static void applyPracticalScriptIssueMetadata(CourseCurriculum.Submodule s) {
        if (s == null) {
            return;
        }
        // Disabled submodules vanish from the learner-facing curriculum with no trace (see
        // filterCurriculumForLearner) — this only ever surfaces here for the admin (unfiltered)
        // view, so an admin reviewing a module can see why a topic silently doesn't play.
        if (!isSubmoduleEnabled(s)) {
            s.setContentIntegrityIssueCode("SUBMODULE_DISABLED");
            s.setContentIntegrityIssueMessage(
                    "This lecture is disabled and hidden from learners — it will not appear in the "
                            + "course player. If this wasn't intentional, re-enable it in the curriculum editor.");
        } else if (s.isPracticalRequired() && !StringUtils.hasText(s.getScriptText())) {
            s.setContentIntegrityIssueCode("PRACTICAL_SCRIPT_MISSING");
            s.setContentIntegrityIssueMessage(
                    "This practical session is missing required narration text (scriptText). "
                            + "Audio cannot be generated until an administrator adds the script for this topic. "
                            + "Please use “Report an issue” so our team can fix it — your feedback helps everyone.");
        } else if (!StringUtils.hasText(s.getImagePath())) {
            s.setContentIntegrityIssueCode("IMAGE_MISSING");
            s.setContentIntegrityIssueMessage(
                    "This lecture has no generated slide image yet. An administrator needs to generate "
                            + "and commit an image for this topic (curriculum image editor) before it displays correctly.");
        } else {
            s.setContentIntegrityIssueCode(null);
            s.setContentIntegrityIssueMessage(null);
        }
    }

    List<CourseCurriculum> filterCurriculumForLearner(List<CourseCurriculum> modules) {
        if (modules == null) {
            return List.of();
        }
        return modules.stream()
                .map(module -> {
                    List<CourseCurriculum.Submodule> enabledSubmodules = module.getSubmodules() == null
                            ? List.of()
                            : module.getSubmodules().stream()
                                    .filter(CourseService::isSubmoduleEnabled)
                                    .map(CourseService::copySubmoduleForLearner)
                                    .collect(Collectors.toList());
                    if (enabledSubmodules.isEmpty()) {
                        return null;
                    }
                    CourseCurriculum copy = new CourseCurriculum();
                    copy.setId(module.getId());
                    copy.setCourseId(module.getCourseId());
                    copy.setModuleName(module.getModuleName());
                    copy.setModuleAssetPath(module.getModuleAssetPath());
                    copy.setSubmodules(new ArrayList<>(enabledSubmodules));
                    copy.setTitle(module.getTitle());
                    copy.setContent(module.getContent());
                    copy.setOrder(module.getOrder());
                    return copy;
                })
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toList());
    }

    // --- DEMO COURSE (public, fully unlocked) ---
    /** Whether this course is the public demo (fully unlocked + daily-budget capped). */
    public static boolean isDemo(Course course) {
        return course != null && Boolean.TRUE.equals(course.getIsDemo());
    }

    /** Finds the public demo course (isDemo=true), if one is configured and active. */
    public Optional<Course> findDemoCourse() {
        return courseRepository.findByIsDemoTrue().filter(CourseService::isActive);
    }

    public Course getDemoCourseOrThrow() {
        return findDemoCourse()
                .orElseThrow(() -> new NotFoundException(
                        "No demo course configured. Set one via PUT /skillama/api/admin/courses/{courseId}/set-demo."));
    }

    /**
     * Marks an EXISTING course as the public demo (reusing its real curriculum,
     * images and practical scripts). Only one demo at a time; the course is made
     * public + active so anonymous visitors can reach it.
     */
    @Transactional
    public Course setCourseAsDemo(String courseId) {
        Course target = courseRepository.findById(courseId)
                .orElseThrow(() -> new NotFoundException("Course not found: " + courseId));
        courseRepository.findByIsDemoTrue().ifPresent(prev -> {
            if (!prev.getId().equals(courseId)) {
                prev.setIsDemo(Boolean.FALSE);
                courseRepository.save(prev);
            }
        });
        target.setIsDemo(Boolean.TRUE);
        target.setIsPublic(Boolean.TRUE);
        if (target.getActive() == null) {
            target.setActive(Boolean.TRUE);
        }
        target.setUpdatedAt(IndiaTime.now());
        return courseRepository.save(target);
    }

    // --- GUEST COURSE MANAGEMENT ---
    /**
     * Finds the guest course. First tries to find a course marked as isGuestCourse,
     * then falls back to the first public course if no guest course is found.
     * @return Optional containing the guest course, or empty if none found
     */
    public Optional<Course> findGuestCourse() {
        Optional<Course> guestCourse = courseRepository.findByIsGuestCourseTrue();
        if (guestCourse.isPresent() && isActive(guestCourse.get())) {
            return guestCourse;
        }
        return courseRepository.findByIsPublicTrue().stream()
                .filter(CourseService::isActive)
                .findFirst();
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
        List<CourseCurriculum> curriculum = getCurriculumByCourseIdOrdered(guestCourse.getId(), true, false);

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
        return courseRepository.findByIsPublicTrue().stream()
                // Exclude archived AND admin-deactivated courses from the public catalog.
                .filter(CourseService::isAvailableToLearner)
                .collect(Collectors.toList());
    }

    /**
     * Gets the first public course (default for guest access)
     * @return Optional containing the first public course, or empty if none found
     */
    public Optional<Course> findFirstPublicCourse() {
        return findPublicCourses().stream().findFirst();
    }

    /** Mints a stable id for any submodule that predates the id field, persisting the backfill. */
    private void backfillSubmoduleIds(List<CourseCurriculum> curriculum) {
        if (curriculum == null) {
            return;
        }
        for (CourseCurriculum module : curriculum) {
            List<CourseCurriculum.Submodule> subs = module.getSubmodules();
            if (subs == null || subs.isEmpty()) {
                continue;
            }
            boolean changed = false;
            for (CourseCurriculum.Submodule sub : subs) {
                if (!org.springframework.util.StringUtils.hasText(sub.getId())) {
                    sub.setId(java.util.UUID.randomUUID().toString());
                    changed = true;
                }
            }
            if (changed) {
                curriculumRepository.save(module);
            }
        }
    }

    // --- SUBMODULE MANAGEMENT ---
    public CourseCurriculum addSubmodule(String moduleId, CourseCurriculum.Submodule submodule) {
        return curriculumRepository.findById(moduleId).map(module -> {
            validatePracticalSubmoduleScript(submodule);
            if (submodule.getEnabled() == null) {
                submodule.setEnabled(true);
            }
            if (!org.springframework.util.StringUtils.hasText(submodule.getId())) {
                submodule.setId(java.util.UUID.randomUUID().toString());
            }
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list == null) list = new java.util.ArrayList<>();
            list.add(submodule);
            module.setSubmodules(list);
            module.setUpdatedAt(IndiaTime.now());
            return curriculumRepository.save(module);
        }).orElse(null);
    }

    public CourseCurriculum updateSubmodule(String moduleId, int submoduleIdx, CourseCurriculum.Submodule updatedSubmodule) {
        return curriculumRepository.findById(moduleId).map(module -> {
            validatePracticalSubmoduleScript(updatedSubmodule);
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                CourseCurriculum.Submodule existing = list.get(submoduleIdx);
                // Id is server-owned and immutable — always carry forward the existing one (or mint it if this
                // submodule predates the id field), regardless of what the update request body contains.
                String existingId = existing.getId();
                updatedSubmodule.setId(org.springframework.util.StringUtils.hasText(existingId)
                        ? existingId
                        : java.util.UUID.randomUUID().toString());
                // This endpoint's request body only ever carries editable content fields (label/scriptText/
                // imagePath/isPracticalRequired/enabled) — datasetId and the image-gen counters are managed
                // exclusively by their own endpoints (AdminCurriculumDatasetController / image generation), so
                // they must always be carried forward here rather than wiped to whatever Jackson defaulted them to.
                updatedSubmodule.setDatasetId(existing.getDatasetId());
                updatedSubmodule.setImageGenCountToday(existing.getImageGenCountToday());
                updatedSubmodule.setImageGenCountDate(existing.getImageGenCountDate());
                list.set(submoduleIdx, updatedSubmodule);
                module.setSubmodules(list);
                module.setUpdatedAt(IndiaTime.now());
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
                module.setUpdatedAt(IndiaTime.now());
                return curriculumRepository.save(module);
            }
            return module;
        }).orElse(null);
    }
    
    /**
     * Finds a submodule by moduleId and index.
     * @param moduleId The module ID
     * @param submoduleIdx The submodule index
     * @return Optional containing the submodule if found
     */
    public Optional<CourseCurriculum.Submodule> findSubmodule(String moduleId, int submoduleIdx) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                return Optional.of(list.get(submoduleIdx));
            }
            return Optional.<CourseCurriculum.Submodule>empty();
        }).orElse(Optional.empty());
    }
    
    /**
     * Updates a submodule's image path.
     * @param moduleId The module ID
     * @param submoduleIdx The submodule index
     * @param imagePath The new image path (can be null to remove image)
     * @return The updated module, or null if not found
     */
    public CourseCurriculum updateSubmoduleImagePath(String moduleId, int submoduleIdx, String imagePath) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                CourseCurriculum.Submodule submodule = list.get(submoduleIdx);
                submodule.setImagePath(imagePath);
                list.set(submoduleIdx, submodule);
                module.setSubmodules(list);
                module.setUpdatedAt(IndiaTime.now());
                return curriculumRepository.save(module);
            }
            return module;
        }).orElse(null);
    }

    /**
     * Links a submodule to its practical-exercise CSV dataset (or clears it when {@code datasetId} is null).
     * @param moduleId The module ID
     * @param submoduleIdx The submodule index
     * @param datasetId The dataset ID to link, or null to unlink
     * @return The updated module, or null if not found
     */
    public CourseCurriculum updateSubmoduleDatasetId(String moduleId, int submoduleIdx, String datasetId) {
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                CourseCurriculum.Submodule submodule = list.get(submoduleIdx);
                submodule.setDatasetId(datasetId);
                list.set(submoduleIdx, submodule);
                module.setSubmodules(list);
                module.setUpdatedAt(IndiaTime.now());
                return curriculumRepository.save(module);
            }
            return module;
        }).orElse(null);
    }

    /**
     * How many AI image generations this submodule has used TODAY (India time).
     * Returns 0 when the stored date is not today (the daily counter has rolled over).
     */
    public int imageGenUsedToday(CourseCurriculum.Submodule submodule) {
        if (submodule == null) {
            return 0;
        }
        String today = IndiaTime.now().toLocalDate().toString();
        if (today.equals(submodule.getImageGenCountDate()) && submodule.getImageGenCountToday() != null) {
            return Math.max(0, submodule.getImageGenCountToday());
        }
        return 0;
    }

    /**
     * Records one AI image generation against the submodule's daily counter,
     * resetting first if the stored date has rolled over. Persisted to Mongo so
     * the cap cannot be bypassed from the client. Returns the new today-count.
     */
    public int incrementImageGenCount(String moduleId, int submoduleIdx) {
        String today = IndiaTime.now().toLocalDate().toString();
        return curriculumRepository.findById(moduleId).map(module -> {
            List<CourseCurriculum.Submodule> list = module.getSubmodules();
            if (list != null && submoduleIdx >= 0 && submoduleIdx < list.size()) {
                CourseCurriculum.Submodule submodule = list.get(submoduleIdx);
                int used = today.equals(submodule.getImageGenCountDate()) && submodule.getImageGenCountToday() != null
                        ? Math.max(0, submodule.getImageGenCountToday())
                        : 0;
                int next = used + 1;
                submodule.setImageGenCountDate(today);
                submodule.setImageGenCountToday(next);
                list.set(submoduleIdx, submodule);
                module.setSubmodules(list);
                module.setUpdatedAt(IndiaTime.now());
                curriculumRepository.save(module);
                return next;
            }
            return 0;
        }).orElse(0);
    }

    /**
     * How many AI thumbnail generations this course has used TODAY (India time).
     * Returns 0 when the stored date is not today (the daily counter has rolled over).
     * Mirrors {@link #imageGenUsedToday}.
     */
    public int thumbnailGenUsedToday(Course course) {
        if (course == null) {
            return 0;
        }
        String today = IndiaTime.now().toLocalDate().toString();
        if (today.equals(course.getThumbnailGenCountDate()) && course.getThumbnailGenCountToday() != null) {
            return Math.max(0, course.getThumbnailGenCountToday());
        }
        return 0;
    }

    /**
     * Records one AI thumbnail generation against the course's daily counter,
     * resetting first if the stored date has rolled over. Persisted to Mongo so
     * the cap cannot be bypassed from the client. Returns the new today-count.
     * Mirrors {@link #incrementImageGenCount}.
     */
    public int incrementThumbnailGenCount(String courseId) {
        String today = IndiaTime.now().toLocalDate().toString();
        return courseRepository.findById(courseId).map(course -> {
            int used = today.equals(course.getThumbnailGenCountDate()) && course.getThumbnailGenCountToday() != null
                    ? Math.max(0, course.getThumbnailGenCountToday())
                    : 0;
            int next = used + 1;
            course.setThumbnailGenCountDate(today);
            course.setThumbnailGenCountToday(next);
            course.setUpdatedAt(IndiaTime.now());
            courseRepository.save(course);
            return next;
        }).orElse(0);
    }

    @Getter
    @AllArgsConstructor
    public static class CourseWithCurriculum {
        private Course course;
        private List<CourseCurriculum> curriculum;
    }
}
