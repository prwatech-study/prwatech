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
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.model.Pricing;
import com.prwatech.courses.model.WishList;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseDetailsRepositoryTemplate;
import com.prwatech.courses.repository.CoursePricingRepositoryTemplate;
import com.prwatech.courses.repository.CourseReviewRepository;
import com.prwatech.courses.repository.CourseReviewRepositoryTemplate;
import com.prwatech.courses.repository.CourseTrackRepository;
import com.prwatech.courses.repository.CourseTrackTemplate;
import com.prwatech.courses.repository.WishListTemplate;
import com.prwatech.courses.service.CourseDetailService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.prwatech.finance.model.UserOrder;
import com.prwatech.finance.repository.template.UserOrderTemplate;
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
  private final WishListTemplate wishListTemplate;
  private final CourseReviewRepository courseReviewRepository;
  private final UserOrderTemplate userOrderTemplate;

  private final CourseTrackTemplate courseTrackTemplate;
  private final CourseTrackRepository courseTrackRepository;

  private static final org.slf4j.Logger LOGGER =
          org.slf4j.LoggerFactory.getLogger(CourseDetailServiceImpl.class);


  @Override
  public List<CourseCardDto> getMostPopularCourses(String userId) {

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
      courseCardDto.setIsWishListed(Boolean.FALSE);
      if(userId!=null){
        Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseDetail.getId()));
        if(wishList.isPresent()){
          courseCardDto.setIsWishListed(Boolean.TRUE);
          courseCardDto.setWishListId(wishList.get().getId());
        }
      }

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public CourseDetailsDto getCourseDescriptionById(String id, String userId) {


    CourseDetails courseDetail =  courseDetailRepository
        .findById(id)
        .orElseThrow(() -> new NotFoundException("No course found by this id :"));

    CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
    CourseDetailsDto courseDetailsDto = new CourseDetailsDto(courseDetail, courseRatingDto, false, null, false);
    if(userId!=null){
     Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(
              new ObjectId(userId), new ObjectId(courseDetail.getId()));
     UserOrder userOrder =
             userOrderTemplate.getByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseDetail.getId()));

     if(wishList.isPresent()){
       courseDetailsDto.setIsWishListed(Boolean.TRUE);
       courseDetailsDto.setWishListId(wishList.get().getId());
     }

     if(Objects.nonNull(userOrder)){
       courseDetailsDto.setIsEnrolled(userOrder.getIsCompleted());
     }
    }
    return courseDetailsDto;


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
  public List<CourseCardDto> getSelfPlacedCourses(String userId) {

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
      courseCardDto.setIsWishListed(Boolean.FALSE);
      if(userId!=null){
        Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseDetail.getId()));
        if(wishList.isPresent()){
          courseCardDto.setIsWishListed(Boolean.TRUE);
          courseCardDto.setWishListId(wishList.get().getId());
        }
      }

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public List<CourseCardDto> getFreeCourses(String userId) {
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
      courseCardDto.setIsWishListed(Boolean.FALSE);
      if(userId!=null){
        Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseDetail.getId()));
        if(wishList.isPresent()){
          courseCardDto.setIsWishListed(Boolean.TRUE);
          courseCardDto.setWishListId(wishList.get().getId());
        }
      }

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }

  @Override
  public PaginationDto getAllCoursesByCategory( String userId,
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
      courseCardDto.setIsWishListed(Boolean.FALSE);
      if(userId!=null){
        Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseDetail.getId()));
        if(wishList.isPresent()){
          courseCardDto.setIsWishListed(Boolean.TRUE);
          courseCardDto.setWishListId(wishList.get().getId());
        }
      }

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

  @Override
  public Set<CourseCardDto> getAllUserEnrolledCourses(ObjectId userId) {

    if(userId==null){
      throw new UnProcessableEntityException("User id can not be null!");
    }

    List<UserOrder> userOrderList = userOrderTemplate.getAllEnrolledCourses(userId);
    Set<CourseCardDto> courseCardList = new HashSet<>();
    for(UserOrder userOrder: userOrderList){
      CourseDetails courseDetail = courseDetailRepository.findById(userOrder.getCourseId().toString()).orElse(null);
      if(Objects.nonNull(courseDetail)){
        CourseCardDto courseCardDto = new CourseCardDto();
        courseCardDto.setCourseId(courseDetail.getId());
        courseCardDto.setTitle(courseDetail.getCourse_Title());
        courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
        courseCardDto.setImgUrl(courseDetail.getCourse_Image());
        courseCardDto.setPrice(
                getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getActual_Price());
        courseCardDto.setDiscountedPrice(
                getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getDiscounted_Price());
        courseCardDto.setCourseDurationHours(6);
        courseCardDto.setCourseDurationMinute(30);

        CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(userId, new ObjectId(courseDetail.getId()));
        if(Objects.nonNull(courseTrack) && courseTrack.getIsAllCompleted().equals(Boolean.FALSE)){
          courseCardDto.setIsCompleted(Boolean.FALSE);
          courseCardList.add(courseCardDto);
        }
      }
    }
    return courseCardList;
  }

  @Override
  public Set<CourseCardDto> getAllCompletedCourse(ObjectId userId) {

    if (userId == null) {
      throw new UnProcessableEntityException("User id can not be null!");
    }

    List<CourseTrack> courseTrackList = courseTrackTemplate.getCompletedCourseByUserId(userId);
    Set<CourseCardDto> courseCardList = new HashSet<>();
    for (CourseTrack courseTrack : courseTrackList) {
      CourseDetails courseDetail = courseDetailRepository.findById(courseTrack.getCourseId().toString()).orElse(null);
      if (Objects.nonNull(courseDetail)) {

        CourseCardDto courseCardDto = new CourseCardDto();
        courseCardDto.setCourseId(courseDetail.getId());
        courseCardDto.setTitle(courseDetail.getCourse_Title());
        courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
        courseCardDto.setImgUrl(courseDetail.getCourse_Image());
        courseCardDto.setPrice(
                getPriceByCourseId(new ObjectId(courseDetail.getId()), courseDetail.getCourse_Category()).getActual_Price());
        courseCardDto.setDiscountedPrice(
                getPriceByCourseId(new ObjectId(courseDetail.getId()), courseDetail.getCourse_Category()).getDiscounted_Price());
        courseCardDto.setCourseDurationHours(6);
        courseCardDto.setCourseDurationMinute(30);
        Optional<WishList> wishList1 = wishListTemplate.getByUserIdAndCourseId(userId, new ObjectId(courseDetail.getId()));
        if (wishList1.isPresent()) {
          courseCardDto.setIsWishListed(Boolean.FALSE);
          courseCardDto.setWishListId(wishList1.get().getId());
        }
        if (courseTrack.getIsAllCompleted().equals(Boolean.TRUE)) {
          courseCardDto.setIsCompleted(Boolean.TRUE);
          courseCardList.add(courseCardDto);
        }

      }
    }
    return courseCardList;
  }

  @Override
  public void updateCurrentItem(ObjectId userId, ObjectId courseId, Integer currentItem) {

    if(courseId==null || userId==null){
       throw new UnProcessableEntityException("User id or course Id can not be null!");
     }
    CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(userId, courseId);

    if(Objects.nonNull(courseTrack)){
      if(courseTrack.getCurrentItem()>currentItem){
        throw new UnProcessableEntityException("Current video list number can be updated as less than the user " +
                "completed as in past.");
      }
      courseTrack.setCurrentItem(currentItem);
      if(currentItem==courseTrack.getTotalSize()){
        courseTrack.setIsAllCompleted(Boolean.TRUE);
      }

      courseTrackRepository.save(courseTrack);
    }
    else {
      LOGGER.error("No course found by this userId and courseId !");
    }
  }

  @Override
  public Set<CourseCardDto> searchByName(String name) {


    List<CourseDetails> courseDetailsList = courseDetailsRepositoryTemplate.searchByName(name);
    if(courseDetailsList.isEmpty()){
      LOGGER.info("No course found by this name");
      throw new NotFoundException("No course available by this name.");
    }


    Set<CourseCardDto> courseCardDtoList = new HashSet<>();
    for (CourseDetails courseDetail : courseDetailsList) {

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
      courseCardDto.setCourseLevelCategory(CourseLevelCategory.FREE_COURSES);
      courseCardDto.setCourseDurationHours(6);
      courseCardDto.setCourseDurationMinute(30);
      courseCardDto.setIsWishListed(Boolean.FALSE);
      courseCardDto.setIsWishListed(Boolean.FALSE);

      courseCardDtoList.add(courseCardDto);
    }
    return courseCardDtoList;
  }
}
