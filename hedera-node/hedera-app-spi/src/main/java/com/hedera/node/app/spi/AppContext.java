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

package com.hedera.node.app.spi;

import com.hedera.node.app.spi.signatures.SignatureVerifier;
import java.time.InstantSource;

/**
 * Gives context to {@link com.swirlds.state.spi.Service} implementations on how the application workflows will do
 * shared functions like verifying signatures or computing the current instant.
 */
public interface AppContext {
    /**
     * The source of the current instant.
     * @return the instant source
     */
    InstantSource instantSource();

    /**
     * The signature verifier the application workflows will use.
     * @return the signature verifier
     */
    SignatureVerifier signatureVerifier();
}
