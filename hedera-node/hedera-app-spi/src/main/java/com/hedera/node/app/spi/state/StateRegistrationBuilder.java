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

/**
 * Creates new state definition using a fluent-API.
 *
 * <p>During construction of a service instance, the service needs the ability to define its state.
 * Is it to be stored in memory, or on disk? This is a critical consideration for performance and
 * scalability reasons. What are the keys and values to be used with that state? How are they
 * serialized? This class is used to create those definitions.
 */
public interface StateRegistrationBuilder {

    @NonNull
    <K> StateRegistrationBuilder keyParser(@NonNull Parser<K> parser);

    @NonNull
    <V> StateRegistrationBuilder valueParser(@NonNull Parser<V> parser);

    @NonNull
    <K> StateRegistrationBuilder keyWriter(@NonNull Writer<K> writer);

    @NonNull
    <V> StateRegistrationBuilder valueWriter(@NonNull Writer<V> writer);

    @NonNull
    StateRegistrationBuilder memory();

    @NonNull
    StateRegistrationBuilder disk();

    @NonNull
    <K, V> StateRegistrationBuilder migrateFrom(
            @NonNull String stateKey,
            @NonNull Parser<K> keyParser,
            @NonNull Parser<V> valueParser,
            @NonNull Writer<K> keyWriter,
            @NonNull Writer<V> valueWriter);

    @NonNull
    <K, V> StateRegistrationBuilder onMigrate(@NonNull MigrationHandler<K, V> handler);

    void complete();
}
