package com.prwatech.finance.service;

import com.prwatech.finance.dto.UserWalletResponseDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Transactional
public interface WalletService {

  UserWalletResponseDto getUserWalletByUserId(String id);
}
