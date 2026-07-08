package com.prwatech.skillama.service;

import com.prwatech.skillama.model.User;
import com.prwatech.skillama.repository.SkillamaUserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserContactServiceTest {

    @Mock private SkillamaUserRepository userRepository;

    @InjectMocks private UserContactService userContactService;

    @Test
    void assertContactUnique_allowsPhoneWhenOnlyMatchesExcludedUser() {
        User current = User.builder().id("u1").email("learner@example.com").phone("+916366111178").build();
        when(userRepository.findAllByPhone("+916366111178")).thenReturn(List.of(current));
        when(userRepository.findAllByPhoneEndingWith("6366111178")).thenReturn(List.of(current));
        when(userRepository.findByEmail("learner@example.com")).thenReturn(Optional.of(current));
        when(userRepository.findAllByEmailAndPhone("learner@example.com", "+916366111178"))
                .thenReturn(List.of(current));

        assertDoesNotThrow(() ->
                userContactService.assertContactUnique("learner@example.com", "6366111178", "u1"));
    }

    @Test
    void assertContactUnique_rejectsPhoneLinkedToAnotherAccount() {
        User other = User.builder().id("u2").email("other@example.com").phone("+916366111178").build();
        when(userRepository.findAllByPhone("+916366111178")).thenReturn(List.of(other));
        when(userRepository.findAllByPhoneEndingWith("6366111178")).thenReturn(List.of(other));
        when(userRepository.findByEmail("learner@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("learner@example.com")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> userContactService.assertContactUnique("learner@example.com", "6366111178", "u1"));

        org.junit.jupiter.api.Assertions.assertTrue(
                ex.getMessage().contains("mobile number is already linked"));
    }

    @Test
    void assertContactUnique_handlesDuplicatePhoneRowsWithoutMongoError() {
        User first = User.builder().id("u1").email("a@example.com").phone("+916366111178").build();
        User duplicate = User.builder().id("u1").email("a@example.com").phone("6366111178").build();
        when(userRepository.findAllByPhone("+916366111178")).thenReturn(List.of(first, duplicate));
        when(userRepository.findAllByPhoneEndingWith("6366111178")).thenReturn(List.of(first, duplicate));
        when(userRepository.findByEmail("a@example.com")).thenReturn(Optional.of(first));
        when(userRepository.findAllByEmailAndPhone("a@example.com", "+916366111178"))
                .thenReturn(List.of(first));

        assertDoesNotThrow(() ->
                userContactService.assertContactUnique("a@example.com", "6366111178", "u1"));
    }
}
