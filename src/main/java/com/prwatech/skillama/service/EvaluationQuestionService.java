package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.EvaluationQuestionRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.EvaluationQuestion;
import com.prwatech.skillama.repository.EvaluationQuestionRepository;
import com.prwatech.skillama.util.IndiaTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@RequiredArgsConstructor
public class EvaluationQuestionService {

    private final EvaluationQuestionRepository questionRepository;

    public List<EvaluationQuestion> listActive() {
        return questionRepository.findByActiveTrueOrderByCategoryAscOrderAsc();
    }

    public List<EvaluationQuestion> listAll() {
        return questionRepository.findAllByOrderByCategoryAscOrderAsc();
    }

    public EvaluationQuestion create(EvaluationQuestionRequestDTO request, String actorId) {
        validate(request);
        EvaluationQuestion question = EvaluationQuestion.builder()
                .category(request.getCategory())
                .questionText(request.getQuestionText().trim())
                .order(request.getOrder() != null ? request.getOrder() : 0)
                .active(request.getActive() == null || request.getActive())
                .createdAt(IndiaTime.now())
                .updatedAt(IndiaTime.now())
                .createdBy(actorId)
                .updatedBy(actorId)
                .build();
        return questionRepository.save(question);
    }

    public EvaluationQuestion update(String id, EvaluationQuestionRequestDTO request, String actorId) {
        EvaluationQuestion question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Evaluation question not found"));
        if (request.getCategory() != null) {
            question.setCategory(request.getCategory());
        }
        if (StringUtils.hasText(request.getQuestionText())) {
            question.setQuestionText(request.getQuestionText().trim());
        }
        if (request.getOrder() != null) {
            question.setOrder(request.getOrder());
        }
        if (request.getActive() != null) {
            question.setActive(request.getActive());
        }
        question.setUpdatedAt(IndiaTime.now());
        question.setUpdatedBy(actorId);
        return questionRepository.save(question);
    }

    public void delete(String id) {
        if (!questionRepository.existsById(id)) {
            throw new ResourceNotFoundException("Evaluation question not found");
        }
        questionRepository.deleteById(id);
    }

    private void validate(EvaluationQuestionRequestDTO request) {
        if (request.getCategory() == null) {
            throw new IllegalArgumentException("category is required");
        }
        if (!StringUtils.hasText(request.getQuestionText())) {
            throw new IllegalArgumentException("questionText is required");
        }
    }
}
