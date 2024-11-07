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

package com.hedera.node.app.services;

import com.hedera.node.app.spi.AppContext;
import com.hedera.node.app.spi.signatures.SignatureVerifier;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.InstantSource;
import java.util.function.LongSupplier;

/**
 * Provides the context for the application.
 * @param instantSource The source of the current instant.
 * @param signatureVerifier The verifier of signatures.
 * @param gossip The gossip interface.
 * @param configuration The configuration.
 * @param selfIdSupplier The supplier of the self-node id.
 */
public record AppContextImpl(
        @NonNull InstantSource instantSource,
        @NonNull SignatureVerifier signatureVerifier,
        @NonNull Gossip gossip,
        @NonNull Configuration configuration,
        LongSupplier selfIdSupplier)
        implements AppContext {}
