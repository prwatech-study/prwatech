package com.prwatech.promotion.service.impl;

import com.prwatech.promotion.model.PromotionBanner;
import com.prwatech.promotion.repository.PromotionBannerRepository;
import com.prwatech.promotion.service.PromotionBannerService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@AllArgsConstructor
@Service
@Transactional
public class PromotionBannerServiceImpl  implements PromotionBannerService {

    private final PromotionBannerRepository promotionBannerRepository;

    @Override
    public List<PromotionBanner> getAllActiveBanner() {
        return promotionBannerRepository.findAll();
    }
}
