/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.stack;

import static java.util.Objects.requireNonNull;

import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.state.WrappedHederaState;
import com.hedera.node.app.workflows.handle.validation.AttributeValidatorImpl;
import com.hedera.node.app.workflows.handle.validation.ExpiryValidatorImpl;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A savepoint in the state stack.
 *
 * <p>Savepoints are used to track the state and configuration during the handle workflow. Together with the
 * {@link SavepointStackImpl} they allow to revert the state to a previous point
 * in time.
 *
 * <p>A savepoint also contains convenience functionality, that depends on either the state or the configuration.
 */
public class Savepoint {

    private final WrappedHederaState state;
    private Configuration configuration;

    /**
     * Constructs a new {@link Savepoint} with the given state and configuration.
     *
     * @param state the state of the savepoint
     * @param configuration the configuration of the savepoint
     * @throws NullPointerException if one of the arguments is {@code null}
     */
    public Savepoint(@NonNull final WrappedHederaState state, @NonNull final Configuration configuration) {
        this.state = requireNonNull(state, "state must not be null");
        this.configuration = requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Returns the state of the savepoint.
     *
     * @return the state of the savepoint
     */
    @NonNull
    public WrappedHederaState state() {
        return state;
    }

    /**
     * Returns the configuration of the savepoint.
     *
     * @return the configuration of the savepoint
     */
    @NonNull
    public Configuration configuration() {
        return configuration;
    }

    /**
     * Sets the configuration of the savepoint.
     *
     * @param configuration the configuration of the savepoint
     * @throws NullPointerException if {@code configuration} is {@code null}
     */
    void configuration(@NonNull final Configuration configuration) {
        this.configuration = requireNonNull(configuration, "configuration must not be null");
    }

    /**
     * Returns the next entity number.
     *
     * <p><em>Please note:</em> If the savepoint is reverted, the provided entity number will be reused.
     *
     * @return the next entity number
     */
    public long newEntityNum() {
        // TODO: Implement Savepoint.newEntityNum (https://github.com/hashgraph/hedera-services/issues/6701)
        return 1L;
    }

    /**
     * Returns an {@link AttributeValidator} that is based on the current configuration and state.
     *
     * @return an {@link AttributeValidator}
     */
    @NonNull
    public AttributeValidator attributeValidator() {
        // TODO: Implement Savepoint.attributeValidator (https://github.com/hashgraph/hedera-services/issues/6701)
        return new AttributeValidatorImpl();
    }

    /**
     * Returns an {@link ExpiryValidator} that is based on the current configuration and state.
     *
     * @return an {@link ExpiryValidator}
     */
    @NonNull
    public ExpiryValidator expiryValidator() {
        // TODO: Implement Savepoint.expiryValidator (https://github.com/hashgraph/hedera-services/issues/6701)
        return new ExpiryValidatorImpl();
    }
}
