package com.prwatech.courses.service;

import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.model.Forum;

import java.util.List;

public interface ForumService {

    List<ForumDto> getForumList();

}
