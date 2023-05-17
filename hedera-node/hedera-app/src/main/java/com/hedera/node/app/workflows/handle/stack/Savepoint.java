/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.workflows.handle.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.workflows.handle.state.WrappedHederaState;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

public class Savepoint {

    private final WrappedHederaState state;
    private final Configuration config;

    public Savepoint(@NonNull final WrappedHederaState state, @NonNull final Configuration config) {
        this.state = requireNonNull(state, "state must not be null");
        this.config = requireNonNull(config, "config must not be null");
    }

    public WrappedHederaState state() {
        return state;
    }

    public Configuration config() {
        return config;
    }

    public long newEntityNum() {
        // TODO: Implement Savepoint.newEntityNum()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public AttributeValidator attributeValidator() {
        // TODO: Implement Savepoint.attributeValidator()
        throw new UnsupportedOperationException("Not implemented yet");
    }

    public ExpiryValidator expiryValidator() {
        // TODO: Implement Savepoint.expiryValidator()
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
