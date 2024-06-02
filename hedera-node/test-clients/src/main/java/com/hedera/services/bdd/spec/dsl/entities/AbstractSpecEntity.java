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

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.handleExec;
import static java.util.Objects.requireNonNull;

import com.hedera.services.bdd.SpecOperation;
import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.SpecEntityRegistrar;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

/**
 * Provides implementation support for a {@link SpecEntity}.
 *
 * @param <O> the type of the operation used to create the entity
 * @param <M> the type of the entity's model
 */
public abstract class AbstractSpecEntity<O extends SpecOperation, M extends Record> implements SpecEntity {
    /**
     * Represents the attempt to create an entity.
     *
     * @param op the operation used for the attempt
     * @param model the model object to be created
     * @param <S> the type of the operation
     * @param <R> the type of the model
     */
    protected record Creation<S extends SpecOperation, R extends Record>(S op, R model) {}

    /**
     * Represents the result of a successful entity creation.
     *
     * @param model the model object created
     * @param registrar the registrar for the entity
     * @param <R> the type of the model
     */
    protected record Result<R extends Record>(R model, SpecEntityRegistrar registrar) {}

    /**
     * Represents an entity created on a network.
     *
     * @param networkName the network name
     * @param result the successful result
     * @param <E> the type of the model
     */
    private record NetworkEntity<E extends Record>(String networkName, Result<E> result) {}

    private volatile boolean locked = false;
    private final List<NetworkEntity<M>> networkEntities = new ArrayList<>();
    protected final String name;

    protected AbstractSpecEntity(@NonNull final String name) {
        this.name = requireNonNull(name);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void lock() {
        locked = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String name() {
        return name;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable SpecEntityRegistrar registrarFor(@NonNull final HederaNetwork network) {
        for (final var entity : networkEntities) {
            if (entity.networkName.equals(network.name())) {
                return entity.result.registrar;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpecEntityRegistrar createWith(@NonNull final HapiSpec spec) {
        final var network = spec.targetNetworkOrThrow();
        if (registrarFor(network) != null) {
            throw new IllegalArgumentException(
                    "Entity '" + name + "' already exists on network '" + network.name() + "'");
        }
        final var creation = newCreation(spec);
        // Throws if the creation op fails
        handleExec(spec, creation.op);
        final var result = resultForSuccessful(creation, spec);
        networkEntities.add(new NetworkEntity<>(network.name(), result));
        return result.registrar;
    }

    /**
     * Throws an exception if the entity is locked.
     */
    protected void throwIfLocked() {
        if (locked) {
            throw new IllegalStateException("Entity '" + name + "' is locked");
        }
    }

    /**
     * Retrieves the model corresponding to a network.
     *
     * @param network the network
     * @return the model
     */
    protected M modelOrThrow(@NonNull final HederaNetwork network) {
        requireNonNull(network);
        for (final var entity : networkEntities) {
            if (entity.networkName.equals(network.name())) {
                return entity.result.model;
            }
        }
        throw new IllegalArgumentException("No entity exists on network '" + network.name() + "'");
    }

    /**
     * Supplies a {@link SpecOperation} that can be used to create the entity within the given spec.
     *
     * @param spec the spec
     * @return a new creation attempt
     */
    protected abstract Creation<O, M> newCreation(@NonNull HapiSpec spec);

    /**
     * Computes the registrar corresponding to a successful creation operation.
     *
     * @param creation the successful creation
     * @param spec the spec used to create the entity
     * @return the registrar
     */
    protected abstract Result<M> resultForSuccessful(@NonNull Creation<O, M> creation, @NonNull HapiSpec spec);
}
