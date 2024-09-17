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

package com.hedera.services.bdd.spec.dsl.entities;

import static com.hedera.hapi.node.base.TokenType.FUNGIBLE_COMMON;

import com.hedera.services.bdd.spec.dsl.annotations.FungibleToken;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Represents a fungible token that may exist on one or more target networks and be
 * registered with more than one {@link com.hedera.services.bdd.spec.HapiSpec} if desired.
 */
public class SpecFungibleToken extends SpecToken {
    /**
     * Create a new instance of {@link SpecFungibleToken} from a {@link FungibleToken} annotation.
     * @param annotation The annotation to create the instance from.
     * @param defaultName The default name to use if the annotation does not specify one.
     * @return The new instance of {@link SpecFungibleToken}.
     */
    public static SpecFungibleToken from(@NonNull final FungibleToken annotation, @NonNull final String defaultName) {
        final var token = new SpecFungibleToken(annotation.name().isBlank() ? defaultName : annotation.name());
        customizeToken(token, annotation.keys(), annotation.useAutoRenewAccount());
        token.builder().maxSupply(annotation.maxSupply()).totalSupply(annotation.initialSupply());
        return token;
    }

    public SpecFungibleToken(@NonNull final String name) {
        super(name, FUNGIBLE_COMMON);
    }
}
