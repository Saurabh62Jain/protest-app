package com.civic.action;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.civic.action.repository.postgres")
@EnableMongoRepositories(basePackages = "com.civic.action.repository.mongo")
public class ProtestAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(ProtestAppApplication.class, args);
    }
}
