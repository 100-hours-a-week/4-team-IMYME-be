package com.imyme.mine;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class MineApplication {

  public static void main(String[] args) {
    SpringApplication.run(MineApplication.class, args);
  }
}
