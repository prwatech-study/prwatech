package com.prwatech.finance.service.impl;

import com.prwatech.finance.dto.UserWalletResponseDto;
import com.prwatech.finance.model.Wallet;
import com.prwatech.finance.repository.WalletRepository;
import com.prwatech.finance.repository.template.WalletTemplate;
import com.prwatech.finance.service.WalletService;
import com.prwatech.user.service.UserService;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class WalletServiceImpl implements WalletService {

  private final WalletRepository walletRepository;
  private final WalletTemplate walletTemplate;
  private final UserService userService;

  @Override
  public UserWalletResponseDto getUserWalletByUserId(String id) {

    Wallet wallet = walletTemplate.getByUserId(new ObjectId(id));

    UserWalletResponseDto userWalletResponseDto = new UserWalletResponseDto();
    userWalletResponseDto.setId(wallet.getId());
    userWalletResponseDto.setUser_Id(id);
    userWalletResponseDto.setWalletAmount(wallet.getMy_Cash());
    userWalletResponseDto.setReferalCode(userService.getUserReferalCodeByUserId(id));

    return userWalletResponseDto;
  }
}
