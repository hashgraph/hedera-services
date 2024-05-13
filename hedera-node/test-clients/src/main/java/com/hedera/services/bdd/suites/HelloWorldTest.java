package com.hedera.services.bdd.suites;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(HelloWorldExtension.class)
public class HelloWorldTest {
    @Test
    public void testHelloWorld() {
        System.out.println("Hello, World!");
    }

    @TestFactory
    public DynamicTest testDynamicHelloWorld() {
        return DynamicTest.dynamicTest("Dynamic Hello, World!", () -> {
            System.out.println("DYNAMIC EXECUTION");
        });
    }
}
