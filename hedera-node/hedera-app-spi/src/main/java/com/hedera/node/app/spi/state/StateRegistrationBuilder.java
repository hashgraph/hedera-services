/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.state;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

/**
 * Creates new state definition using a fluent-API.
 *
 * <p>During construction of a service instance, the service needs the ability to define its state.
 * Is it to be stored in memory, or on disk? This is a critical consideration for performance and
 * scalability reasons. What are the keys and values to be used with that state? How are they
 * serialized? This class is used to create those definitions.
 */
public interface StateRegistrationBuilder {

    /**
     * Specifies the {@link Parser} to use for parsing keys in the registered state. This method
     * MUST be called.
     *
     * @param parser The {@link Parser} to use for keys
     * @return an instance of this builder
     * @param <K> The type of the key to parse
     */
    @NonNull
    <K> StateRegistrationBuilder keyParser(@NonNull Parser<K> parser);

    /**
     * Specifies the {@link Parser} to use for parsing values in the registered state. This method
     * MUST be called.
     *
     * @param parser The {@link Parser} to use for values
     * @return an instance of this builder
     * @param <V> The type of the value to parse
     */
    @NonNull
    <V> StateRegistrationBuilder valueParser(@NonNull Parser<V> parser);

    /**
     * Specifies the {@link Writer} to use for writing keys in the registered state. This method
     * MUST be called.
     *
     * @param writer The {@link Writer} to use for keys
     * @return an instance of this builder
     * @param <K> The type of the key to write
     */
    @NonNull
    <K> StateRegistrationBuilder keyWriter(@NonNull Writer<K> writer);

    /**
     * Specifies the {@link Writer} to use for writing values in the registered state. This method
     * MUST be called.
     *
     * @param writer The {@link Writer} to use for values
     * @return an instance of this builder
     * @param <V> The type of the value to write
     */
    @NonNull
    <V> StateRegistrationBuilder valueWriter(@NonNull Writer<V> writer);

    /**
     * Specifies that the state to be registered should be stored in memory. Calling this method
     * more than once has no special effect. It is mutually exclusive with {@link #disk()}. The last
     * method in the call chain ({@code memory()} or {@link #disk()} will be used.
     *
     * <p>Either this method, or {@link #disk()} MUST be called
     *
     * @return an instance of this builder
     */
    @NonNull
    StateRegistrationBuilder memory();

    /**
     * Specifies that the state to be registered should be stored on disk. Calling this method more
     * than once has no special effect. It is mutually exclusive with {@link #memory()}. The last
     * method in the call chain ({@link #memory()} or {@code disk()} will be used.
     *
     * <p>Either this method, or {@link #memory()} MUST be called
     *
     * @return an instance of this builder
     */
    @NonNull
    StateRegistrationBuilder disk();

    /**
     * Specifies a {@link Ruler} to use for measuring the number of bytes for a given serialized
     * input. This method is ONLY REQUIRED when using disk based states. For example, if the data is
     * serialized ({@link #keyWriter(Writer)}) as protobuf, then an implementation of the {@link
     * Ruler} would just read the first varint to discover the length of the key data.
     *
     * @param numKeys Must be a positive number
     * @return an instance of this builder
     */
    @NonNull
    StateRegistrationBuilder maxNumOfKeys(int numKeys);

    /**
     * Specifies the maximum number of keys permitted to be saved in a state. This must be specified
     * for {@link #disk()} registrations, and has no meaning otherwise.
     *
     * @param ruler Used to measure the number of bytes that make up the key
     * @return an instance of this builder
     */
    @NonNull
    StateRegistrationBuilder keyLength(@NonNull Ruler ruler);

    /**
     * Adds to the builder a {@link ReadableState} to be used a source ("oldState") to migrate from.
     * This method is additive. Each time it is called, it adds a new source. If called twice for
     * the same {@code stateKey}, then the second call takes precedence.
     *
     * <p>This is an OPTIONAL method. If not called, then the {@code oldStates} in the {@link
     * MigrationHandler} of {@link #onMigrate(MigrationHandler)} will be empty.
     *
     * @param stateKey The state key of the original state to migrate from
     * @param keyParser The key parser of the original state
     * @param valueParser The value parser of the original state
     * @param keyWriter The key writer of the original state
     * @param valueWriter The value writer of the original state
     * @return an instance of this builder
     * @param <K> The type of the key used in the old state
     * @param <V> The type of the value used in the old state
     */
    @NonNull
    <K, V> StateRegistrationBuilder addMigrationFrom(
            @NonNull String stateKey,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter,
            @Nullable Ruler keyRuler);

    /**
     * Specifies the {@link MigrationHandler} to use during migration.
     *
     * <p>This method is OPTIONAL. If not called, then no migration will happen for this state.
     *
     * @param handler The handler to call for migration
     * @return an instance of this builder
     * @param <K> The type of the key in the new state
     * @param <V> The type of the value in the new state
     */
    @NonNull
    <K, V> StateRegistrationBuilder onMigrate(@NonNull MigrationHandler<K, V> handler);

    /**
     * Finalizes the registration process of this builder.
     *
     * @throws IllegalStateException If the required methods of this builder have not been called.
     */
    void complete();
}
