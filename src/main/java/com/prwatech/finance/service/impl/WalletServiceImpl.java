package com.prwatech.finance.service.impl;

import com.prwatech.common.exception.UnProcessableEntityException;
import com.prwatech.finance.dto.UserWalletResponseDto;
import com.prwatech.finance.model.Wallet;
import com.prwatech.finance.repository.WalletRepository;
import com.prwatech.finance.repository.template.WalletTemplate;
import com.prwatech.finance.service.WalletService;
import com.prwatech.user.model.User;
import com.prwatech.user.service.UserService;
import com.prwatech.user.template.UserTemplate;
import lombok.AllArgsConstructor;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.prwatech.common.Constants.DEFAULT_AMOUNT;
import static com.prwatech.common.Constants.DEFAULT_REF_AMOUNT;

@Service
@AllArgsConstructor
public class WalletServiceImpl implements WalletService {

  private final WalletRepository walletRepository;
  private final WalletTemplate walletTemplate;
  private final UserService userService;
  private final UserTemplate userTemplate;

  private static final Logger LOGGER = LoggerFactory.getLogger(WalletServiceImpl.class);

  @Override
  public UserWalletResponseDto getUserWalletByUserId(String id) {

    Wallet wallet = walletTemplate.getByUserId(new ObjectId(id));
    if(Objects.isNull(wallet)){
        return new UserWalletResponseDto(
                null, id, 0, userService.getUserReferalCodeByUserId(id));
    }
    UserWalletResponseDto userWalletResponseDto = new UserWalletResponseDto();
    userWalletResponseDto.setId(wallet.getId());
    userWalletResponseDto.setUser_Id(id);
    userWalletResponseDto.setWalletAmount(wallet.getMy_Cash());
    userWalletResponseDto.setReferalCode(userService.getUserReferalCodeByUserId(id));

    return userWalletResponseDto;
  }

  @Override
  public Boolean addIntoWalletByReferal(String referalCode) {

     if(referalCode==null){
       return false;
     }
     User user = userTemplate.getByReferalCode(referalCode);
     if(Objects.isNull(user)){
       LOGGER.info("No user found by this referal Code!");
       return false;
     }

     Wallet wallet = walletTemplate.getByUserId(new ObjectId(user.getId()));
     if(Objects.isNull(wallet)){
       wallet = new Wallet();
       wallet.setUser_Id(new ObjectId(user.getId()));
       wallet.setMy_Cash(DEFAULT_REF_AMOUNT);
       wallet.setMy_Rewards(DEFAULT_REF_AMOUNT);
       walletRepository.save(wallet);
       return true;
     }
     wallet.setMy_Cash(wallet.getMy_Cash()+DEFAULT_REF_AMOUNT);
     walletRepository.save(wallet);
     return true;
  }

    @Override
    public Wallet createNewWalletForUser(User user) {

        if(Objects.isNull(user)){
            LOGGER.info("Unable to create wallet for user, user is null!");
            return null;
        }

        Wallet wallet = walletTemplate.getByUserId(new ObjectId(user.getId()));
        if(Objects.nonNull(wallet)){
            return wallet;
        }

        wallet = new Wallet();
        wallet.setUser_Id(new ObjectId(user.getId()));
        wallet.setMy_Cash(DEFAULT_AMOUNT);
        wallet.setMy_Rewards(0);
      return  walletRepository.save(wallet);
    }
}
