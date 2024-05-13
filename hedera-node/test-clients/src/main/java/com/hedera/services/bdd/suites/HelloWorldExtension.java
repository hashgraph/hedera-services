/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

        log.info(
                "(EACH) We have annotated element {} and method {} and test instance {}",
                extensionContext.getElement(),
                extensionContext.getTestMethod(),
                extensionContext.getTestInstance());
    }

    @Override
    public void beforeAll(ExtensionContext extensionContext) throws Exception {
        log.info(
                "We have annotated element {} and method {} and test instance {}",
                extensionContext.getElement(),
                extensionContext.getTestMethod(),
                extensionContext.getTestInstance());
    }
}
