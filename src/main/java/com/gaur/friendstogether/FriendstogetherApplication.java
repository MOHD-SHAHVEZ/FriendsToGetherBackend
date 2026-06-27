package com.gaur.friendstogether;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class FriendstogetherApplication {

    public static void main(String[] args) {
        SpringApplication.run(FriendstogetherApplication.class, args);
    }

}
