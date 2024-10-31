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

package com.hedera.services.bdd.junit;

import static com.hedera.services.bdd.junit.TestTags.ONLY_EMBEDDED;

import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.extensions.SpecNamingExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Isolated;

/**
 * A variant of {@link HapiTest} that signals the {@link NetworkTargetingExtension} to create a separate
 * embedded network for the test to ensure the test sees the genesis transaction. Even though the embedded
 * network is not shared with any other test, we must run it as {@link Isolated} because we use JVM system
 * properties to configure each embedded network in the test process.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@TestFactory
@Tag(ONLY_EMBEDDED)
@ExtendWith({NetworkTargetingExtension.class, SpecNamingExtension.class})
@Isolated
public @interface GenesisHapiTest {}
