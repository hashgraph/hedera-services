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

package com.hedera.services.bdd.spec.dsl.annotations;

import com.hedera.services.bdd.junit.extensions.SpecEntityExtension;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Describes a {@link com.hedera.services.bdd.spec.dsl.entities.SpecAccount}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@ExtendWith(SpecEntityExtension.class)
public @interface Account {
    /**
     * If set, a different {@link com.hedera.services.bdd.spec.HapiSpec} name to use for the account.
     *
     * @return the spec name of the account
     */
    String name() default "";

    /**
     * If set, the initial balance of the account in tinybars.
     *
     * @return the initial balance of the account in tinybars
     */
    long tinybarBalance() default 0;

    /**
     * If set, the initial balance of the account in cents (to be converted to tinybars at the spec's
     * active exchange rate).
     *
     * @return the initial balance of the account in cents
     */
    long centBalance() default 0;

    /**
     * If set, the initial staked node id of the account.
     *
     * @return the initial staked node id of the account
     */
    long stakedNodeId() default -1;

    /**
     * If set, the maximum number of auto-associations to allow for the account.
     * @return the maximum number of auto-associations
     */
    int maxAutoAssociations() default 0;
}
