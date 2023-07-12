package com.prwatech.courses.service.impl;

import com.prwatech.common.exception.AlreadyPresentException;
import com.prwatech.courses.dto.AddCartDto;
import com.prwatech.courses.dto.AddWishListDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.Cart;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.WishList;
import com.prwatech.courses.repository.CartRepository;
import com.prwatech.courses.repository.CartTemplate;
import com.prwatech.courses.repository.CourseDetailRepository;
import com.prwatech.courses.repository.WishListRepository;
import com.prwatech.courses.repository.WishListTemplate;
import com.prwatech.courses.service.CartAndWishListService;
import com.prwatech.courses.service.CourseDetailService;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Component
@AllArgsConstructor
public class CartAndWishListServiceImpl implements CartAndWishListService {

    private final CartTemplate cartTemplate;
    private final CartRepository cartRepository;
    private final WishListTemplate wishListTemplate;
    private final WishListRepository wishListRepository;
    private final CourseDetailService courseDetailService;
    private final CourseDetailRepository courseDetailRepository;

    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger(CartAndWishListServiceImpl.class);



    @Override
    public Set<CourseCardDto> getUserCartListByUserId(ObjectId User_Id) {


        List<Cart> cartList = cartTemplate.getByUserId(User_Id);

        Set<CourseCardDto> courseCardDtoList = new HashSet<>();
        for(Cart cart: cartList){
            List<Cart.Cart_Items> cart_items = cart.getCart_Items();
            for(com.prwatech.courses.model.Cart.Cart_Items cartItem:cart_items){
                CourseDetails courseDetail = courseDetailRepository.findById(cartItem.getCourse_Id().toString())
                        .orElse(null);
                if(Objects.nonNull(courseDetail)){
                    CourseRatingDto courseRatingDto = courseDetailService.getRatingOfCourse(courseDetail.getId());
                    CourseCardDto courseCardDto = new CourseCardDto();
                    courseCardDto.setCartId(cart.getId());
                    courseCardDto.setCourseId(courseDetail.getId());
                    courseCardDto.setTitle(courseDetail.getCourse_Title());
                    courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
                    courseCardDto.setImgUrl(courseDetail.getCourse_Image());
                    courseCardDto.setCourseRatingDto(courseRatingDto);
                    courseCardDto.setPrice( courseDetailService.
                            getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getActual_Price());
                    courseCardDto.setDiscountedPrice( courseDetailService.
                            getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getDiscounted_Price());
                    courseCardDto.setCourseLevelCategory(CourseLevelCategory.MOST_POPULAR);
                    courseCardDto.setCourseDurationHours(6);
                    courseCardDto.setCourseDurationMinute(30);

                    courseCardDtoList.add(courseCardDto);
                }
            }

        }
        return courseCardDtoList;
    }

    @Override
    public Set<CourseCardDto> getWishListByUserId(ObjectId User_Id) {


        List<WishList> wishLists = wishListTemplate.getWishListByUserId(User_Id);
        Set<CourseCardDto> courseCardDtoList = new HashSet<>();

        for (WishList wishList : wishLists){
            CourseDetails courseDetail = courseDetailRepository.findById(wishList.getCourseId().toString()).orElse(null);
            if(Objects.nonNull(courseDetail)){
                CourseRatingDto courseRatingDto = courseDetailService.getRatingOfCourse(courseDetail.getId());
                CourseCardDto courseCardDto = new CourseCardDto();
                courseCardDto.setWishListId(wishList.getId());
                courseCardDto.setCourseId(courseDetail.getId());
                courseCardDto.setTitle(courseDetail.getCourse_Title());
                courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
                courseCardDto.setImgUrl(courseDetail.getCourse_Image());
                courseCardDto.setCourseRatingDto(courseRatingDto);
                courseCardDto.setPrice( courseDetailService.
                        getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getActual_Price());
                courseCardDto.setDiscountedPrice( courseDetailService.
                        getPriceByCourseId(new ObjectId(courseDetail.getId()),courseDetail.getCourse_Category()).getDiscounted_Price());
                courseCardDto.setCourseLevelCategory(CourseLevelCategory.MOST_POPULAR);
                courseCardDto.setCourseDurationHours(6);
                courseCardDto.setCourseDurationMinute(30);

                courseCardDtoList.add(courseCardDto);
            }
        }

        return courseCardDtoList;
    }

    @Override
    public void AddToWishList(AddWishListDto addWishListDto) {

        WishList wishList = wishListTemplate.getByUserIdAndCourseId(new ObjectId(addWishListDto.getUserId()), new ObjectId(addWishListDto.getCourseId()));
        if(Objects.nonNull(wishList)){
            throw new AlreadyPresentException("This course is already in wish list.");
        }

        wishList.setUserId(new ObjectId(addWishListDto.getUserId()));
        wishList.setCourseId(new ObjectId(addWishListDto.getCourseId()));
        wishList.setCourseType(addWishListDto.getCourseLevelCategory());

        wishListRepository.save(wishList);
    }

    @Override
    public void removeFromWishListById(String id){
        wishListRepository.deleteById(id);
    }

    @Override
    public void addToCart(AddCartDto addCartDto) {

        Cart cart = cartTemplate.getByUserIdAndCourseId(
                new ObjectId(addCartDto.getUser_Id()),
                new ObjectId(addCartDto.getCourseId()));

        if(Objects.nonNull(cart)){
            throw new AlreadyPresentException("This course is already in cart.");
        }

            cart.setUser_Id(new ObjectId(addCartDto.getUser_Id()));
            com.prwatech.courses.model.Cart.Cart_Items cart_items = new Cart.Cart_Items();
            cart_items.setCourse_Id(new ObjectId(addCartDto.getCourseId()));
            cart_items.setCourse_Type(addCartDto.getCourse_Type().getValue());
            cart_items.setPurchase_Type("Course");
            List<Cart.Cart_Items> cart_itemsList = cart.getCart_Items();
            cart_itemsList.add(cart_items);
            cart.setCart_Items(cart_itemsList);

            cartRepository.save(cart);


    }

    @Override
    public void removeFromByCartId(String id) {
        cartRepository.deleteById(id);
    }

    @Override
    public List<Cart> getCartTest(String userId) {
        return cartTemplate.getByUserId(new ObjectId(userId));
    }

}
