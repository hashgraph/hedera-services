/*
 * Copyright (C) 2018-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.test.consensus.framework.validation;

import com.swirlds.platform.test.consensus.framework.ConsensusOutput;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Validates output of a consensus test. The type of validation that is done depends on the implementation.
 */
public interface ConsensusOutputValidation {
    /**
     * Perform validation on all consensus output.
     *
     * @param output1 the output from one node
     * @param output2 the output from another node
     */
    void validate(@NonNull final ConsensusOutput output1, @NonNull final ConsensusOutput output2);
}
