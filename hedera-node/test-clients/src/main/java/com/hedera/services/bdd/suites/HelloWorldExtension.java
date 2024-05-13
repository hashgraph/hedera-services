package com.hedera.services.bdd.suites;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

public class HelloWorldExtension implements BeforeAllCallback, BeforeEachCallback {
    private static final Logger log = LogManager.getLogger(HelloWorldExtension.class);

    @Override
    public void beforeEach(ExtensionContext extensionContext) throws Exception {

        log.info("(EACH) We have annotated element {} and method {} and test instance {}",
                extensionContext.getElement(),
                extensionContext.getTestMethod(),
                extensionContext.getTestInstance());
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        log.info("We have annotated element {} and method {} and test instance {}",
                extensionContext.getElement(),
                extensionContext.getTestMethod(),
                extensionContext.getTestInstance());
    }
}
