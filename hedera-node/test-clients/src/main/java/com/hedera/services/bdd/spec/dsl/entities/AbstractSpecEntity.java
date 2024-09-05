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

import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.allRunFor;
import static com.hedera.services.bdd.spec.utilops.CustomSpecAssert.handleExec;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.CompletableFuture.supplyAsync;

import com.hedera.services.bdd.junit.hedera.HederaNetwork;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.SpecOperation;
import com.hedera.services.bdd.spec.dsl.SpecEntity;
import com.hedera.services.bdd.spec.dsl.SpecEntityRegistrar;
import com.hedera.services.bdd.spec.dsl.operations.deferred.DoWithModelOperation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

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
     * Indicates whether the entity is locked.
     */
    private volatile boolean locked = false;
    /**
     * The name of the entity.
     */
    protected final String name;
    /**
     * A map of network names to atomic references to futures that will contain the results
     * of entity creations on the target networks.
     */
    private final Map<String, AtomicReference<CompletableFuture<Result<M>>>> results = new ConcurrentHashMap<>();

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
        final var maybeResultFuture = results.computeIfAbsent(network.name(), k -> new AtomicReference<>())
                .get();
        return (maybeResultFuture != null) ? maybeResultFuture.join().registrar() : null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SpecEntityRegistrar createWith(@NonNull final HapiSpec spec) {
        final var network = spec.targetNetworkOrThrow();
        final var resultFutureRef = requireNonNull(results.get(network.name()));
        final CompletableFuture<Result<M>> resultFuture = supplyAsync(() -> {
            final var creation = newCreation(spec);
            // Throws if the creation op fails
            handleExec(spec, creation.op());
            final var result = resultForSuccessful(creation, spec);
            allRunFor(spec, postSuccessOps());
            return result;
        });
        if (!resultFutureRef.compareAndSet(null, resultFuture)) {
            resultFuture.cancel(true);
        }
        return resultFutureRef.get().join().registrar();
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
    public M modelOrThrow(@NonNull final HederaNetwork network) {
        requireNonNull(network);
        return requireNonNull(requireNonNull(results.get(network.name())).get())
                .join()
                .model();
    }

    /**
     * Executes a deferred operation on the model.
     *
     * @param function the function that computes the operation
     * @return the deferred operation
     */
    public DoWithModelOperation<M> doWith(@NonNull final Function<M, SpecOperation> function) {
        return new DoWithModelOperation<>(this, function);
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

    /**
     * Supplies a list of operations to be executed after a successful creation.
     *
     * @return the list of operations
     */
    protected List<SpecOperation> postSuccessOps() {
        return emptyList();
    }

    /**
     * Replaces the result of a creation operation on a network.
     *
     * @param network the network
     * @param result the result
     */
    protected void replaceResult(@NonNull final HederaNetwork network, @NonNull final Result<M> result) {
        requireNonNull(network);
        requireNonNull(result);
        requireNonNull(results.get(network.name())).set(CompletableFuture.completedFuture(result));
    }
}
