package com.prwatech.promotion.service.impl;

import com.prwatech.promotion.model.Testimonials;
import com.prwatech.promotion.repository.TestimonialsRepository;
import com.prwatech.promotion.service.TestimonialService;
import java.util.List;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
@AllArgsConstructor
public class TestimonialServiceImpl implements TestimonialService {

  private final TestimonialsRepository testimonialsRepository;

  @Override
  public List<Testimonials> getAllTestimonials() {
    return testimonialsRepository.findAll();
  }
}
