package com.prwatech.promotion.service;

import com.prwatech.promotion.model.PromotionBanner;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public interface PromotionBannerService {

  List<PromotionBanner> getAllActiveBanner();
}
