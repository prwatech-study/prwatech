package com.prwatech.courses.controller;

import com.prwatech.courses.dto.ForumDto;
import com.prwatech.courses.service.ForumService;
import lombok.AllArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping(value = "/api/public")
@AllArgsConstructor
public class ForumController {

     private final ForumService forumService;
     @GetMapping(value = "/forums")
     public List<ForumDto> getForumsList(){
      return  forumService.getForumList();
     }


}
