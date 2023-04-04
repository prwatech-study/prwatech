package com.prwatech.courses.service.impl;

import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.model.Forum;
import com.prwatech.courses.repository.ForumRepository;
import com.prwatech.courses.service.ForumService;
import com.prwatech.project.dto.ProjectDto;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class ForumServiceImpl implements ForumService {

    private final ForumRepository forumRepository;

    @Override
    public List<ForumDto> getForumList() {
        return forumRepository.findAll().stream().map(forum -> new ForumDto(forum.getId(), forum.getQuestion(), forum.getConversation(), forum.getLastUpdated(), forum.getQuestionBy())).collect(Collectors.toList());
    }
}
