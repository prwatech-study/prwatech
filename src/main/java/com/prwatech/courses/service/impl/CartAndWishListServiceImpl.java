package com.prwatech.courses.service.impl;

import com.prwatech.common.exception.AlreadyPresentException;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.courses.dto.AddCartDto;
import com.prwatech.courses.dto.AddWishListDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.CourseRatingDto;
import com.prwatech.courses.enums.CourseLevelCategory;
import com.prwatech.courses.model.Cart;
import com.prwatech.courses.model.CourseDetails;
import com.prwatech.courses.model.Pricing;
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
import java.util.stream.Collectors;

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
                    Pricing coursePricing = courseDetailService.
                            getPriceByCourseId(new ObjectId(courseDetail.getId()), CourseLevelCategory.fromStringValue(cartItem.getCourse_Type()));

                    CourseRatingDto courseRatingDto = courseDetailService.getRatingOfCourse(courseDetail.getId());
                    CourseCardDto courseCardDto = new CourseCardDto();
                    courseCardDto.setCartId(cart.getId());
                    courseCardDto.setCourseId(courseDetail.getId());
                    courseCardDto.setTitle(courseDetail.getCourse_Title());
                    courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
                    courseCardDto.setImgUrl(courseDetail.getCourse_Image());
                    courseCardDto.setCourseRatingDto(courseRatingDto);
                    courseCardDto.setPrice( coursePricing.getDiscounted_Price());
                    courseCardDto.setDiscountedPrice( coursePricing.getDiscounted_Price());
                    courseCardDto.setCourseLevelCategory(CourseLevelCategory.fromStringValue(cartItem.getCourse_Type()));
                    courseCardDto.setCourseDurationHours(6);
                    courseCardDto.setCourseDurationMinute(30);
                    courseCardDto.setProductId( coursePricing.getProduct_Id());
                    if(User_Id!=null){
                        Optional<WishList> wishList = wishListTemplate.getByUserIdAndCourseId(User_Id, new ObjectId(courseDetail.getId()));
                        if(wishList.isPresent()){
                            courseCardDto.setIsWishListed(Boolean.TRUE);
                            courseCardDto.setWishListId(wishList.get().getId());
                        }
                    }

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
                Pricing coursePricing = courseDetailService.
                        getPriceByCourseId(new ObjectId(courseDetail.getId()), wishList.getCourseType());

                CourseRatingDto courseRatingDto = courseDetailService.getRatingOfCourse(courseDetail.getId());
                CourseCardDto courseCardDto = new CourseCardDto();
                courseCardDto.setWishListId(wishList.getId());
                courseCardDto.setCourseId(courseDetail.getId());
                courseCardDto.setTitle(courseDetail.getCourse_Title());
                courseCardDto.setIsImgPresent(Objects.nonNull(courseDetail.getCourse_Image()));
                courseCardDto.setImgUrl(courseDetail.getCourse_Image());
                courseCardDto.setCourseRatingDto(courseRatingDto);
                courseCardDto.setPrice( coursePricing.getDiscounted_Price());
                courseCardDto.setDiscountedPrice( coursePricing.getDiscounted_Price());
                courseCardDto.setCourseLevelCategory(CourseLevelCategory.MOST_POPULAR);
                courseCardDto.setCourseDurationHours(6);
                courseCardDto.setCourseDurationMinute(30);
                courseCardDto.setProductId( coursePricing.getProduct_Id());
                if(User_Id!=null){
                    Optional<WishList> wishList1 = wishListTemplate.getByUserIdAndCourseId(User_Id, new ObjectId(courseDetail.getId()));
                    if(wishList1.isPresent()){
                        courseCardDto.setIsWishListed(Boolean.TRUE);
                    }
                }

                courseCardDtoList.add(courseCardDto);
            }
        }

        return courseCardDtoList;
    }

    @Override
    public void AddToWishList(AddWishListDto addWishListDto) {

        Optional<WishList> wishListOp = wishListTemplate.getByUserIdAndCourseId(new ObjectId(addWishListDto.getUserId()), new ObjectId(addWishListDto.getCourseId()));
        if(wishListOp.isPresent()){
            throw new AlreadyPresentException("This course is already in wish list.");
        }

        WishList  wishList = new WishList();
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
            cart = new Cart();
            cart.setUser_Id(new ObjectId(addCartDto.getUser_Id()));
            com.prwatech.courses.model.Cart.Cart_Items cart_items = new Cart.Cart_Items();
            cart_items.setCourse_Id(new ObjectId(addCartDto.getCourseId()));
            cart_items.setCourse_Type((addCartDto.getCourse_Type()==null)?CourseLevelCategory.MOST_POPULAR.getValue(): addCartDto.getCourse_Type().getValue());
            cart_items.setPurchase_Type("Course");
            List<Cart.Cart_Items> cart_itemsList = new ArrayList<>();
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

    @Override
    public void removeACourseFromCart(String cartId, String courseId) {
        Optional<Cart> optionalCart = cartRepository.findById(cartId);

        if(!optionalCart.isPresent()){
            throw new NotFoundException("No cart found by this cart Id.");
        }

        Cart cart = optionalCart.get();
        List<Cart.Cart_Items> cart_items =
                cart.getCart_Items().stream().filter(cart_item -> !cart_item.getCourse_Id().toString().equals(courseId)).collect(Collectors.toList());

        cart.setCart_Items(cart_items);

        cartRepository.save(cart);
    }

}
