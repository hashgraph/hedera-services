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

package com.hedera.node.app.service.contract.impl.exec.utils;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.streams.ContractAction;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A non-atomic wrapper for a {@link ContractAction} value, useful when we want to a "stable reference"
 * to an evolving {@link ContractAction} object in the {@link ActionStack}---even though it will actually
 * need to be recreated each time it changes.
 */
public class ActionWrapper {
    private ContractAction value;

    /**
     * @param value the contract action to be initialized
     */
    public ActionWrapper(@NonNull final ContractAction value) {
        this.value = requireNonNull(value);
    }

    /**
     * @return the contract action
     */
    public @NonNull ContractAction get() {
        return value;
    }

    /**
     * @param value the contract action to be set
     */
    public void set(@NonNull final ContractAction value) {
        this.value = requireNonNull(value);
    }
}
