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

import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.ADMIN_KEY;
import static com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey.SUPPLY_KEY;

import com.hedera.services.bdd.junit.extensions.SpecEntityExtension;
import com.hedera.services.bdd.spec.dsl.entities.SpecTokenKey;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Describes a {@link com.hedera.services.bdd.spec.dsl.entities.SpecToken}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@ExtendWith(SpecEntityExtension.class)
public @interface NonFungibleToken {
    /**
     * If set, a {@link com.hedera.services.bdd.spec.HapiSpec} name to use for the token.
     *
     * @return the spec name of the contract
     */
    String name() default "";

    /**
     * The types of keys to associate with the token.
     *
     * @return the types of keys to associate with the token
     */
    SpecTokenKey[] keys() default {ADMIN_KEY, SUPPLY_KEY};

    /**
     * The number of pre-mints to perform.
     *
     * @return the number of pre-mints to perform
     */
    int numPreMints() default 0;

    /**
     * Whether to use an auto-renew account for the token.
     *
     * @return whether to use an auto-renew account for the token
     */
    boolean useAutoRenewAccount() default false;
}
