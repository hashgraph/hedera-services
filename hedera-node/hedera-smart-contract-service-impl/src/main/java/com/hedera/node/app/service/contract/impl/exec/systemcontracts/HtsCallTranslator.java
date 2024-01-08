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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts;

import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Strategy interface for translating {@link HtsCallAttempt}s into {@link HtsCall}s.
 */
public interface HtsCallTranslator {
    /**
     * Tries to translate the given {@code attempt} into a {@link HtsCall}, returning null if the call
     * doesn't match the target type of this translator.
     *
     * @param attempt the attempt to translate
     * @return the translated {@link HtsCall}
     */
    @Nullable
    HtsCall translateCallAttempt(@NonNull HtsCallAttempt attempt);
}
