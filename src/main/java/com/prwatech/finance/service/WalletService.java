package com.prwatech.finance.service;

import com.prwatech.finance.dto.UserWalletResponseDto;
import com.prwatech.finance.model.Wallet;
import com.prwatech.user.model.User;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface WalletService {

  UserWalletResponseDto getUserWalletByUserId(String id);

  Boolean addIntoWalletByReferal(String referalCode);

  Wallet createNewWalletForUser(User user);
}
