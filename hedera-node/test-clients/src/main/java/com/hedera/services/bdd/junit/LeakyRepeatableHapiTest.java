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

import static com.hedera.services.bdd.junit.TestTags.ONLY_REPEATABLE;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;

import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.extensions.SpecNamingExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;

/**
 * Annotation for a {@link HapiTest} that can only be run in repeatable mode, and also changes network state
 * in a way that would "leak" into other tests if not cleaned up. The {@link RepeatableReason} annotation
 * enumerates common reasons a test has to run in repeatable mode.
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@TestFactory
@ExtendWith({NetworkTargetingExtension.class, SpecNamingExtension.class})
@ResourceLock(value = "NETWORK", mode = READ)
@Tag(ONLY_REPEATABLE)
public @interface LeakyRepeatableHapiTest {
    /**
     * The reasons the test has to run in repeatable mode.
     * @return the reasons the test has to run in repeatable mode
     */
    RepeatableReason[] value();

    /**
     * If set, the names of properties this test overrides and needs automatically
     * restored to their original values after the test completes.
     * @return the names of properties this test overrides
     */
    String[] overrides() default {};

    /**
     * If not blank, the path of a JSON file containing the throttles to apply to the test. The
     * original contents of the throttles system file will be restored after the test completes.
     * @return the name of a resource to load throttles from
     */
    String throttles() default "";

    /**
     * If not blank, the path of a JSON file containing the fee schedules to apply to the test. The
     * original contents of the fee schedules system file will be restored after the test completes.
     * @return the name of a resource to load fee schedules from
     */
    String fees() default "";
}
