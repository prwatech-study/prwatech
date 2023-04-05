package com.prwatech.promotion.service;

import com.prwatech.promotion.model.Testimonials;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public interface TestimonialService {

  List<Testimonials> getAllTestimonials();
}
