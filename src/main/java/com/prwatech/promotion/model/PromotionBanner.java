package com.prwatech.promotion.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class PromotionBanner {

  @Id private String id;

  @Field(name = "banner_link")
  private String bannerLink;

  @Field(name = "is_link_active")
  private Boolean isLinkActive = Boolean.TRUE;
}
