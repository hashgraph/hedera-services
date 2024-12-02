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

package com.hedera.services.bdd.junit.restart;

import static com.hedera.services.bdd.junit.TestTags.ONLY_REPEATABLE;
import static org.junit.jupiter.api.parallel.ExecutionMode.SAME_THREAD;

import com.hedera.hapi.node.base.ServicesConfigurationList;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.services.bdd.junit.ConfigOverride;
import com.hedera.services.bdd.junit.HapiTest;
import com.hedera.services.bdd.junit.extensions.NetworkTargetingExtension;
import com.hedera.services.bdd.junit.extensions.SpecNamingExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.Execution;

/**
 * Annotation for a repeatable {@link HapiTest} that exercises the restart phase by creating a new embedded network
 * separate from the shared network. The type of restart scenario is distinguished by the presence or absence of two
 * on-disk assets: A saved state and an override network roster.
 * <p>
 * If a saved state is present, it may be from the previous software version or the current version; and as of release
 * {@code 0.57}, it may or may not have a TSS ledger id already in state. (But note that after the production deployment
 * of TSS, every saved state must already have a TSS ledger id.)
 * <p>
 * If an override network roster is present, it similarly may or may not come have a pre-generated TSS ledger id. (Even
 * after all production networks have TSS enabled, it may be occasionally useful to be able to transplant just roster
 * information and have the target network generate a new ledger id.)
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@TestFactory
@ExtendWith({NetworkTargetingExtension.class, SpecNamingExtension.class})
@Execution(SAME_THREAD)
@Tag(ONLY_REPEATABLE)
public @interface RestartHapiTest {
    /**
     * Any overrides that should be present at restart. At genesis, these will be appended to the embedded
     * node's {@link com.hedera.services.bdd.junit.hedera.ExternalPath#APPLICATION_PROPERTIES} file. With a
     * saved state, these overrides will be the initial contents of the {@link ServicesConfigurationList} message
     * serialized as contents of the {@link FilesConfig#networkProperties()} system file.
     */
    ConfigOverride[] bootstrapOverrides() default {};

    /**
     * The type of restart being tested.
     */
    RestartType restartType() default RestartType.GENESIS;

    /**
     * The type of startup assets that should be present on disk for the test.
     */
    StartupAssets startupAssets() default StartupAssets.NONE;

    /**
     * The type of saved state spec that should be used to customize the state of the {@link FakeState} when
     * using {@link RestartType#SAME_VERSION} or {@link RestartType#UPGRADE_BOUNDARY}.
     */
    Class<? extends SavedStateSpec> savedStateSpec() default NoopSavedStateSpec.class;
}
