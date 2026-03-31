package com.gaur.friendstogether.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TestController {

    @GetMapping("/backend-health")
    public String test() {
        return "[FriendsTogether] Backend Service Running ✅";
    }
}