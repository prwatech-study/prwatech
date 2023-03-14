package com.prwatech.user.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/iam")
public class IamController {

    @GetMapping("/")
    public String helloMethod(){
        return "Hello application";
    }
}
