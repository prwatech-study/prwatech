package com.prwatech.courses.controller;

import com.prwatech.courses.dto.AddCartDto;
import com.prwatech.courses.dto.AddWishListDto;
import com.prwatech.courses.dto.CourseCardDto;
import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.model.Cart;
import com.prwatech.courses.service.CartAndWishListService;
import com.prwatech.courses.service.CourseDetailService;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class WishListAndCartController {

    private final CartAndWishListService cartAndWishListService;

    @ApiOperation(value = "Get all Carts of user", notes = "Get all Cart of user")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping(value = "/cart/{user_id}")
    public List<CourseCardDto> getCartListByUserId(
            @PathVariable(value = "user_id") String user_id) {
        return cartAndWishListService.getUserCartListByUserId(new ObjectId(user_id));
    }

    @ApiOperation(value = "Get all Wishlist of user", notes = "Get all Wishlist of user")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @GetMapping(value = "/wish-list/{user_id}")
    public List<CourseCardDto> getWishListByUserId(
            @PathVariable(value = "user_id") String user_id) {
        return cartAndWishListService.getWishListByUserId(new ObjectId(user_id));
    }

    @ApiOperation(value = "Add Item to Cart", notes = "Add Item to Cart")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @PutMapping(value = "/add/cart")
    public void addItemToCart(
            @RequestBody AddCartDto addCartDto
    ) {
        cartAndWishListService.addToCart(addCartDto);
    }
    @ApiOperation(value = "Remove Item from Cart", notes = "Remove Item from Cart")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping(value = "/remove/{id}")
    public void removeFromCart(
            @PathVariable(value = "id") String id
    ) {
        cartAndWishListService.removeFromByCartId(id);
    }
    @ApiOperation(value = "Add Item to WishList", notes = "Add Item to WishList")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @PostMapping(value = "/add/wish-list")
    public void addToWishListByUserId(
            @RequestBody AddWishListDto addWishListDto
            ) {
           cartAndWishListService.AddToWishList(addWishListDto);
    }

    @ApiOperation(value = "Remove Item from WishList", notes = "Remove Item from WishList")
    @ApiResponses(
            value = {
                    @ApiResponse(code = 200, message = "Success"),
                    @ApiResponse(code = 400, message = "Not Available"),
                    @ApiResponse(code = 401, message = "UnAuthorized"),
                    @ApiResponse(code = 403, message = "Access Forbidden"),
                    @ApiResponse(code = 404, message = "Not found"),
                    @ApiResponse(code = 422, message = "UnProcessable entity"),
                    @ApiResponse(code = 500, message = "Internal server error"),
            })
    @ResponseStatus(HttpStatus.OK)
    @DeleteMapping(value = "/remove/wish-list/{id}")
    public void removeFromWishList(
            @PathVariable(value = "id") String id
    ) {
        cartAndWishListService.removeFromWishListById(id);
    }
}
