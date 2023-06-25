package com.prwatech.courses.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.dto.PaginationDto;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.utility.Utility;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseDetailsDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.CourseReviewRequestDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.Pricing;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseDetailsRepositoryTemplate;
import com.prwatech.courses.repository.CoursePricingRepositoryTemplate;
import com.prwatech.courses.repository.CourseReviewRepository;
import com.prwatech.courses.repository.CourseReviewRepositoryTemplate;
import com.prwatech.courses.service.CourseDetailService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

import com.prwatech.user.model.User;
import com.prwatech.user.repository.UserRepository;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class CourseDetailServiceImpl implements CourseDetailService {

  private final CourseDetailsRepositoryTemplate courseDetailsRepositoryTemplate;
  private final CourseReviewRepositoryTemplate courseReviewRepositoryTemplate;
  private final CoursePricingRepositoryTemplate coursePricingRepositoryTemplate;
  private final CourseDetailRepository courseDetailRepository;
  private final UserRepository userRepository;
  private final CourseReviewRepository courseReviewRepository;

  private static final org.slf4j.Logger LOGGER =
          org.slf4j.LoggerFactory.getLogger(CourseDetailServiceImpl.class);


  @Override
  public List<CourseCardDto> getMostPopularCourses() {

    List<CourseDetails> courseDetailList = courseDetailsRepositoryTemplate.getMostPopularCourse();
    List<CourseCardDto> courseCardDtoList = new ArrayList<>();
    for (CourseDetails courseDetail : courseDetailList) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setCourseRatingDto(courseRatingDto);
      courseCardDto.setPrice(
          getPriceByCourseId(new ObjectId(courseDetail.getId()),"Classroom").getActual_Price());
      courseCardDto.setDiscountedPrice(
              getPriceByCourseId(new ObjectId(courseDetail.getId()),"Classroom").getDiscounted_Price());
      courseCardDto.setCourseLevelCategory(CourseLevelCategory.MOST_POPULAR);
      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public CourseDetailsDto getCourseDescriptionById(String id) {


    CourseDetails courseDetail =  courseDetailRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("No course found by this id :"));

    CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
    return new CourseDetailsDto(courseDetail, courseRatingDto);


  }

  @Override
  public CourseRatingDto getRatingOfCourse(String courseId) {
    List<CourseReview> courseReviewList =
        courseReviewRepositoryTemplate.getCourseReviewByCourseId(new ObjectId(courseId));
    Integer totalRating =
        courseReviewList.stream()
            .map(CourseReview::getReview_Number)
            .collect(Collectors.toList())
            .stream()
            .mapToInt(Integer::intValue)
            .sum();
    Map<Integer, Integer> ratingMap = new HashMap<>();
    List<String> messageList = new ArrayList<>();

    for(CourseReview courseReview : courseReviewList){
        if(Objects.nonNull(courseReview.getReview_Message())){
          messageList.add(courseReview.getReview_Message());
        }
      switch (courseReview.getReview_Number()){
        case 1->ratingMap.put(1, ratingMap.getOrDefault(1, 0)+1);
        case 2->ratingMap.put(2, ratingMap.getOrDefault(2, 0)+1);
        case 3->ratingMap.put(3, ratingMap.getOrDefault(3, 0)+1);
        case 4->ratingMap.put(4, ratingMap.getOrDefault(4, 0)+1);
        case 5->ratingMap.put(5, ratingMap.getOrDefault(5, 0)+1);
      }

    }
    return new CourseRatingDto(courseId, totalRating, courseReviewList.size(), ratingMap, messageList);
  }


  @Override
  public Pricing getPriceByCourseId(ObjectId courseId, String type) {
    return coursePricingRepositoryTemplate
        .getPricingOfCourseByCourseId(courseId, type)
        .orElse(new Pricing("", new ObjectId(), null, Constants.DEFAULT_PRICING, Constants.DEFAULT_PRICING));
  }

  @Override
  public List<CourseCardDto> getSelfPlacedCourses() {

    List<CourseDetails> courseDetailList = courseDetailsRepositoryTemplate.getSelfPlacedCourses();
    List<CourseCardDto> courseCardDtoList = new ArrayList<>();
    for (CourseDetails courseDetail : courseDetailList) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setCourseRatingDto(courseRatingDto);
      courseCardDto.setPrice(
          getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getActual_Price());
      courseCardDto.setDiscountedPrice(
              getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getDiscounted_Price());
      courseCardDto.setCourseLevelCategory(CourseLevelCategory.SELF_PLACED);
      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public List<CourseCardDto> getFreeCourses() {
    List<CourseDetails> courseDetailList = courseDetailsRepositoryTemplate.getFreeCourses();
    List<CourseCardDto> courseCardDtoList = new ArrayList<>();
    for (CourseDetails courseDetail : courseDetailList) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setCourseRatingDto(courseRatingDto);
      courseCardDto.setPrice(
          getPriceByCourseId(new ObjectId(courseDetail.getId()),"Webinar").getActual_Price());
      courseCardDto.setDiscountedPrice(
              getPriceByCourseId(new ObjectId(courseDetail.getId()),"Webinar").getDiscounted_Price());
      courseCardDto.setCourseLevelCategory(CourseLevelCategory.FREE_COURSES);
      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public PaginationDto getAllCoursesByCategory(
      CourseLevelCategory category, Integer pageNumber, Integer pageSize) {

    Page<CourseDetails> courseDetailsPage = null;
    switch (category) {
      case MOST_POPULAR:
        courseDetailsPage =
            courseDetailsRepositoryTemplate.getAllMostPopularCourses(pageNumber, pageSize);
        break;
      case FREE_COURSES:
        courseDetailsPage = courseDetailsRepositoryTemplate.getAllFreeCourses(pageNumber, pageSize);
        break;
      case SELF_PLACED:
        courseDetailsPage =
            courseDetailsRepositoryTemplate.getAllSelfPlacedCourses(pageNumber, pageSize);
        break;
      case ALL:
        courseDetailsPage = courseDetailRepository.findAll(PageRequest.of(pageNumber, pageSize));
        break;
      default:
        throw new UnProcessableEntityException("This category does not exist!");
    }

    List<CourseCardDto> courseCardDtoList = new ArrayList<>();
    for (CourseDetails courseDetail : courseDetailsPage.getContent()) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setCourseRatingDto(courseRatingDto);

      switch (category){
        case MOST_POPULAR -> courseCardDto.setPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Classroom").getActual_Price());
        case SELF_PLACED -> courseCardDto.setPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getActual_Price());
        case FREE_COURSES -> courseCardDto.setPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Webinar").getActual_Price());
      }
      switch (category){
        case MOST_POPULAR -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Classroom").getDiscounted_Price());
        case SELF_PLACED -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getDiscounted_Price());
        case FREE_COURSES -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Webinar").getDiscounted_Price());
      }

      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);

      courseCardDtoList.add(courseCardDto);
    }

    return Utility.getPaginatedResponse(
        Collections.singletonList(courseCardDtoList),
        courseDetailsPage.getPageable(),
        courseDetailsPage.getTotalPages(),
        courseDetailsPage.hasNext(),
        courseDetailsPage.getTotalElements());
  }

  @Override
  public List<ForumFilterListingDto> getCoursesTitleListing() {
    return courseDetailRepository.findAll().stream()
        .map(
            (courseDetails -> {
              return new ForumFilterListingDto(
                  courseDetails.getId(), courseDetails.getCourse_Title());
            }))
        .collect(Collectors.toList());
  }

  @Override
  public Pricing getCoursePriceByIdAndCategory(ObjectId id, CourseLevelCategory category) {
    String Course_Type="";

    switch (category){
      case MOST_POPULAR -> Course_Type="Classroom";
      case SELF_PLACED -> Course_Type="Online";
      case FREE_COURSES -> Course_Type="Webinar";
    }
    return getPriceByCourseId(id, Course_Type);
  }

  @Override
  public CourseReview rateACourse(CourseReviewRequestDto courseReviewRequestDto) {

    if(Objects.isNull(courseReviewRequestDto.getUserId())
            || Objects.isNull(courseReviewRequestDto.getCourseId())){
      LOGGER.error("User id is null or course id is null in review dto.");
      throw new UnProcessableEntityException("User id or Course id can not be null!");
    }

    CourseReview courseReview = new CourseReview();

    courseReview.setCourse_Id(new ObjectId(courseReviewRequestDto.getCourseId()));
    courseReview.setReviewer_Id(new ObjectId(courseReviewRequestDto.getUserId()));
    courseReview.setReview_Message((courseReviewRequestDto.getReviewMessage()!=null)?courseReviewRequestDto.getReviewMessage():null);
    courseReview.setReview_Status(Boolean.TRUE);
    courseReview.setReview_Number(courseReviewRequestDto.getRateNumber());
    courseReview.setReview_From("User");

    return courseReviewRepository.save(courseReview);
  }
}
