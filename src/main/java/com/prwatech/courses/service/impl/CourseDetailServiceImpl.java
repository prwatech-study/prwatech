package com.prwatech.courses.service.impl;

import com.prwatech.common.Constants;
import com.prwatech.common.dto.PaginationDto;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.common.utility.Utility;
import com.prwatech.courses.dto.CertificateDetailsDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseDetailsDto;
import com.prwatech.courses.dto.CourseDetailsProjection;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.dto.CourseReviewRequestDto;
import com.prwatech.courses.dto.ForumFilterListingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.CourseCurriculam;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.CourseReview;
import com.prwatech.courses.model.CourseTrack;
import com.prwatech.courses.model.MyCourses;
import com.prwatech.courses.model.Pricing;
import com.prwatech.courses.model.WishList;
import com.prwatech.courses.repository.CourseCurriculamTemplate;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.CourseDetailsRepositoryTemplate;
import com.prwatech.courses.repository.CoursePricingRepositoryTemplate;
import com.prwatech.courses.repository.CourseReviewRepository;
import com.prwatech.courses.repository.CourseReviewRepositoryTemplate;
import com.prwatech.courses.repository.CourseTrackRepository;
import com.prwatech.courses.repository.CourseTrackTemplate;
import com.prwatech.courses.repository.MyCoursesRepository;
import com.prwatech.courses.repository.MyCoursesTemplate;
import com.prwatech.courses.repository.WishListTemplate;
import com.prwatech.courses.service.CourseDetailService;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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
  private final CourseTrackTemplate courseTrackTemplate;
  private final CourseTrackRepository courseTrackRepository;
  private final UserRepository userRepository;
  private final CourseCurriculamTemplate courseCurriculamTemplate;
  private final MyCoursesTemplate myCoursesTemplate;
  private final MyCoursesRepository myCoursesRepository;
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
      if(Objects.nonNull(userId)){
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

     CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(new ObjectId(userId), new ObjectId(courseDetail.getId()));
     //get course track here .
     if(wishList.isPresent()){
       courseDetailsDto.setIsWishListed(Boolean.TRUE);
       courseDetailsDto.setWishListId(wishList.get().getId());
     }

     if(Objects.nonNull(courseTrack)){
       courseDetailsDto.setIsEnrolled(Boolean.TRUE);
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
  public PaginationDto getFreeCourses(String userId, Integer pageNumber, Integer pageSize) {
    Page<CourseDetails> courseDetailsPage = courseDetailsRepositoryTemplate.getFreeCourseByBit(pageNumber, pageSize);
    List<CourseCardDto> courseCardDtoList = new ArrayList<>();

    for (CourseDetails courseDetail : courseDetailsPage.getContent()) {

      CourseRatingDto courseRatingDto = getRatingOfCourse(courseDetail.getId());
      CourseCardDto courseCardDto = new CourseCardDto();

      courseCardDto.setCourseId(courseDetail.getId());
      courseCardDto.setTitle(courseDetail.getCourse_Title());
      courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
      courseCardDto.setImgUrl(courseDetail.getCourse_Image());
      courseCardDto.setCourseRatingDto(courseRatingDto);
      courseCardDto.setPrice(0);
      courseCardDto.setDiscountedPrice(0);
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
    return Utility.getPaginatedResponse(
            Collections.singletonList(courseCardDtoList),
            courseDetailsPage.getPageable(),
            courseDetailsPage.getTotalPages(),
            courseDetailsPage.hasNext(),
            courseDetailsPage.getTotalElements());
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
        case ALL -> courseCardDto.setPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getActual_Price());
      }
      switch (category){
        case MOST_POPULAR -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Classroom").getDiscounted_Price());
        case SELF_PLACED -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getDiscounted_Price());
        case FREE_COURSES -> courseCardDto.setDiscountedPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Webinar").getDiscounted_Price());
        case ALL -> courseCardDto.setPrice(getPriceByCourseId(new ObjectId(courseDetail.getId()), "Online").getActual_Price());
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

    List<CourseTrack> courseTrackList = courseTrackTemplate.getAllEnrolledCoursesOfUser(userId);
    Set<CourseCardDto> courseCardList = new HashSet<>();
    for(CourseTrack courseTrack: courseTrackList){
      CourseDetails courseDetail = courseDetailRepository.findById(courseTrack.getCourseId().toString()).orElse(null);
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
        courseCardList.add(courseCardDto);
      }
    }
    List<MyCourses> myCourses = myCoursesTemplate.findCourseByUserId(userId);
    for(MyCourses myCourse: myCourses){
      CourseDetails courseDetail = courseDetailRepository.findById(myCourse.getCourse_Id().toString()).orElse(null);
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
        courseCardList.add(courseCardDto);
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
      if(courseTrack.getTotalSize()<=currentItem){
        courseTrack.setCurrentItem(courseTrack.getTotalSize());
        courseTrack.setIsAllCompleted(Boolean.TRUE);
        courseTrack.setUpdatedAt(LocalDateTime.now());
      }
      else {
        courseTrack.setCurrentItem(currentItem);
      }
      courseTrackRepository.save(courseTrack);
    }
    else {
      LOGGER.error("No course found by this userId and courseId !");
    }
  }

  @Override
  public Map<String, String> searchByName(String name) {


    List<CourseDetailsProjection> courseDetailsList = courseDetailsRepositoryTemplate.searchByName(name);

   return courseDetailsList.stream().collect(Collectors.toMap(CourseDetailsProjection::getId, CourseDetailsProjection::getCourse_Title));

  }

  @Override
  public CertificateDetailsDto getCertificateDetails(String userId, String courseId) {
    User user = userRepository.findById(userId).orElseThrow(()-> new NotFoundException("User not found by given id."));

    if(user.getName()==null){
      throw new UnProcessableEntityException("Please complete your profile details.");
    }

    CourseDetails courseDetails = courseDetailRepository.findById(courseId).orElseThrow(()-> new NotFoundException("No course found by this course id"));

    CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(new ObjectId(userId), new ObjectId(courseId));

    if (Objects.isNull(courseTrack)){
      throw new NotFoundException("You have not bought this course.");
    }

    if(!courseTrack.getIsAllCompleted()){
      throw new UnProcessableEntityException("You must complete the course to get certificate.");
    }

    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd, MMMM, yyyy", Locale.ENGLISH);
    String date= courseTrack.getUpdatedAt().format(formatter);

    return new CertificateDetailsDto(user.getName(), courseDetails.getCourse_Title(), date);
  }

  @Override
  public Boolean makeItFreeAndNonFree(String courseId, Boolean isFree) {

   CourseDetails courseDetails = courseDetailRepository.findById(courseId).orElseThrow(
            ()->new NotFoundException("No course find by this id."));

   courseDetails.setIsFree(isFree);
   courseDetailRepository.save(courseDetails);
    return true;
  }

  @Override
  public CourseTrack enrollAFreeCourse(String userId, String courseId) {

    MyCourses myCourses= myCoursesTemplate.findByUserIdAndCourseId(new ObjectId(userId), new ObjectId(courseId));
    CourseTrack courseTrack = courseTrackTemplate.getByCourseIdAndUserId(new ObjectId(userId), new ObjectId(courseId));

    if(Objects.isNull(myCourses)){
      CourseCurriculam courseCurriculam = courseCurriculamTemplate.getAllCurriculamByCourseId(new ObjectId(courseId));
      LOGGER.info("The given course assigning to user.");
      myCourses= MyCourses.builder().Course_Id(new ObjectId(courseId)).User_Id(new ObjectId(userId)).build();

      if(Objects.isNull(courseTrack)){
        courseTrack = CourseTrack.builder()
                .userId(new ObjectId(userId))
                .courseId(new ObjectId(courseId))
                .currentItem(1)
                .isAllCompleted(Boolean.FALSE)
                .totalSize((Objects.nonNull(courseCurriculam))?courseCurriculam.getCourse_Curriculam().size():1)
                .build();
      }
      myCoursesRepository.save(myCourses);
      return courseTrackRepository.save(courseTrack);
    }
    return courseTrack;
  }
}
