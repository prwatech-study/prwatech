package com.prwatech.courses.service;

import com.prwatech.courses.dto.AddCartDto;
import com.prwatech.courses.dto.AddWishListDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.model.Cart;
import com.prwatech.courses.model.WishList;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public interface CartAndWishListService {

   Set<CourseCardDto> getUserCartListByUserId(ObjectId User_Id);

   Set<CourseCardDto> getWishListByUserId(ObjectId User_Id);

   void AddToWishList(AddWishListDto addWishListDto);

   void removeFromWishListById(String id);

   void addToCart(AddCartDto addCartDto);
   void removeFromByCartId(String id);

   List<Cart> getCartTest(String userId);
}
