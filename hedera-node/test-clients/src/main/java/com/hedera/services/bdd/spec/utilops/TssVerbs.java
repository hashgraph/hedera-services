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

package com.hedera.services.bdd.spec.utilops;

import static com.hedera.services.bdd.spec.utilops.UtilVerbs.doingContextual;

import com.hedera.node.app.tss.TssBaseService;
import com.hedera.services.bdd.spec.SpecOperation;

/**
 * Factory for spec operations that support exercising TSS, especially in embedded mode.
 */
public class TssVerbs {
    private TssVerbs() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns an operation that instructs the embedded {@link TssBaseService} to ignoring TSS signature requests.
     * @return the operation that will ignore TSS signature requests
     */
    public static SpecOperation startIgnoringTssSignatureRequests() {
        return doingContextual(
                spec -> spec.embeddedHederaOrThrow().tssBaseService().startIgnoringRequests());
    }

    /**
     * Returns an operation that instructs the embedded {@link TssBaseService} to stop ignoring TSS signature requests.
     * @return the operation that will stop ignoring TSS signature requests
     */
    public static SpecOperation stopIgnoringTssSignatureRequests() {
        return doingContextual(
                spec -> spec.embeddedHederaOrThrow().tssBaseService().stopIgnoringRequests());
    }
}
