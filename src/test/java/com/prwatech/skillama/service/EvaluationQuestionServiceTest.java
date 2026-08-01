package com.prwatech.skillama.service;

import com.prwatech.skillama.dto.EvaluationQuestionRequestDTO;
import com.prwatech.skillama.exception.ResourceNotFoundException;
import com.prwatech.skillama.model.EvaluationQuestion;
import com.prwatech.skillama.repository.EvaluationQuestionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvaluationQuestionServiceTest {

    @Mock private EvaluationQuestionRepository questionRepository;

    private EvaluationQuestionService service;

    @BeforeEach
    void setUp() {
        service = new EvaluationQuestionService(questionRepository);
        when(questionRepository.save(any(EvaluationQuestion.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private EvaluationQuestionRequestDTO request(EvaluationQuestion.Category category, String text) {
        EvaluationQuestionRequestDTO dto = new EvaluationQuestionRequestDTO();
        dto.setCategory(category);
        dto.setQuestionText(text);
        return dto;
    }

    @Test
    void createRejectsMissingCategory() {
        EvaluationQuestionRequestDTO dto = request(null, "Some question?");
        assertThrows(IllegalArgumentException.class, () -> service.create(dto, "admin"));
    }

    @Test
    void createRejectsBlankQuestionText() {
        EvaluationQuestionRequestDTO dto = request(EvaluationQuestion.Category.CONTENT, "   ");
        assertThrows(IllegalArgumentException.class, () -> service.create(dto, "admin"));
    }

    @Test
    void createDefaultsActiveTrueAndTrimsText() {
        EvaluationQuestionRequestDTO dto = request(EvaluationQuestion.Category.IMAGE, "  Is the image relevant?  ");
        EvaluationQuestion saved = service.create(dto, "admin");

        assertTrue(saved.isActive());
        assertEquals("Is the image relevant?", saved.getQuestionText());
        assertEquals("admin", saved.getCreatedBy());
    }

    @Test
    void createRespectsExplicitInactiveFlag() {
        EvaluationQuestionRequestDTO dto = request(EvaluationQuestion.Category.PRACTICAL, "Draft question");
        dto.setActive(false);
        EvaluationQuestion saved = service.create(dto, "admin");
        assertFalse(saved.isActive());
    }

    @Test
    void updateThrowsWhenQuestionNotFound() {
        when(questionRepository.findById("ghost")).thenReturn(Optional.empty());
        EvaluationQuestionRequestDTO dto = request(EvaluationQuestion.Category.CONTENT, "New text");
        assertThrows(ResourceNotFoundException.class, () -> service.update("ghost", dto, "admin"));
    }

    @Test
    void updateOnlyAppliesProvidedFields() {
        EvaluationQuestion existing = EvaluationQuestion.builder()
                .id("q1").category(EvaluationQuestion.Category.CONTENT)
                .questionText("Original text").order(1).active(true).build();
        when(questionRepository.findById("q1")).thenReturn(Optional.of(existing));

        EvaluationQuestionRequestDTO dto = new EvaluationQuestionRequestDTO();
        dto.setActive(false); // only toggling active; everything else omitted

        EvaluationQuestion updated = service.update("q1", dto, "admin2");

        assertEquals("Original text", updated.getQuestionText()); // unchanged
        assertEquals(EvaluationQuestion.Category.CONTENT, updated.getCategory()); // unchanged
        assertFalse(updated.isActive()); // changed
        assertEquals("admin2", updated.getUpdatedBy());
    }

    @Test
    void deleteThrowsWhenNotFound() {
        when(questionRepository.existsById("ghost")).thenReturn(false);
        assertThrows(ResourceNotFoundException.class, () -> service.delete("ghost"));
    }

    @Test
    void deleteRemovesExistingQuestion() {
        when(questionRepository.existsById("q1")).thenReturn(true);
        service.delete("q1"); // no throw
    }
}
