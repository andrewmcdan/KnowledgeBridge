package com.capstone.knowledgebridge;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;

class KnowledgeBridgeApplicationMainTests {

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.main.web-application-type=none"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            KnowledgeBridgeApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(KnowledgeBridgeApplication.class, args));
        }
    }
}
