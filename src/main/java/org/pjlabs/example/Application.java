package org.pjlabs.example;

import org.pjlabs.blockless.Blockless;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class Application {

  public static void main(final String[] args) {
    Blockless.roar();
    SpringApplication.run(Application.class, args);
  }
}
