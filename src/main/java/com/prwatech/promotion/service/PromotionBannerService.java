package com.prwatech.promotion.service;

import com.prwatech.promotion.model.PromotionBanner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public interface PromotionBannerService {

    List<PromotionBanner> getAllActiveBanner();


}
