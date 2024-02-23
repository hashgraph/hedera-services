/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.platform.components.state.output;

import com.swirlds.platform.state.signed.SignedState;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * There is a new, most up-to-date and complete signed state.
 */
@FunctionalInterface
public interface NewLatestCompleteStateConsumer {

    /**
     * There is a new latest complete signed state.
     *
     * @param signedState signed state
     */
    void newLatestCompleteStateEvent(@NonNull final SignedState signedState);
}
