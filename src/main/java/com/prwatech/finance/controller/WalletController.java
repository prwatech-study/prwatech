package com.prwatech.finance.controller;

import com.prwatech.common.Constants;
import com.prwatech.common.exception.NotFoundException;
import com.prwatech.finance.dto.UserWalletResponseDto;
import com.prwatech.finance.model.Wallet;
import com.prwatech.finance.service.WalletService;
import com.prwatech.user.model.User;
import com.prwatech.user.repository.UserRepository;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiResponse;
import io.swagger.annotations.ApiResponses;
import javax.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/public")
@AllArgsConstructor
public class WalletController {

  private final WalletService walletService;
  private final UserRepository userRepository;

  @ApiOperation(
      value = "Get user wallet details by user id",
      notes = "Get user wallet details by user id")
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
  @ResponseStatus(value = HttpStatus.OK)
  @ApiImplicitParams({
    @ApiImplicitParam(
        name = Constants.AUTH,
        value = Constants.TOKEN_TYPE,
        required = true,
        dataType = Constants.AUTH_DATA_TYPE,
        paramType = Constants.AUTH_PARAM_TYPE)
  })
  @GetMapping("/wallet/{userId}")
  public UserWalletResponseDto getUserWalletDetails(
      @PathVariable(value = "userId") @NotNull String userId) {
    return walletService.getUserWalletByUserId(userId);
  }

  @ApiOperation(
          value = "Create new wallet for user",
          notes = "Create new wallet for user")
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
  @ResponseStatus(value = HttpStatus.OK)
  @PutMapping("/create/wallet/{userId}")
  public Wallet createNewWallet(
          @PathVariable(value = "userId") String userId
  ){
    User user = userRepository.findById(userId).orElseThrow(()->new NotFoundException("No user found by this id!"));
    return walletService.createNewWalletForUser(user);
  }
}
