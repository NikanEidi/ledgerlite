package dev.nikan.ledgerlite;

import org.springframework.boot.SpringApplication;

public class TestLedgerliteApplication {

    public static void main(String[] args) {
        SpringApplication.from(LedgerliteApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
