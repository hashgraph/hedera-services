/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.swirlds.logging.test.fixtures;

import com.swirlds.logging.test.fixtures.internal.LoggerMirrorExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * A JUnit 5 annotation that can be used to inject a {@link LoggingMirror} into a test method or test class. Tests that
 * are annotated with this annotation will never be executed in parallel. By doing so each test will have its isolated
 * access to a {@link LoggingMirror} instance that can be used to check the logging events that were generated during a
 * test.
 *
 * @see LoggingMirror
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Isolated
@ExtendWith(LoggerMirrorExtension.class)
public @interface WithLoggingMirror {}
