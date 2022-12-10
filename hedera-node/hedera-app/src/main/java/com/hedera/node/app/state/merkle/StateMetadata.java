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
package com.hedera.node.app.state.merkle;

import com.hedera.node.app.spi.state.Parser;
import com.hedera.node.app.spi.state.Ruler;
import com.hedera.node.app.spi.state.Writer;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

public record StateMetadata<K, V>(
        @NonNull String serviceName,
        @NonNull String stateKey,
        @NonNull Parser<K> keyParser,
        @NonNull Parser<V> valueParser,
        @NonNull Writer<K> keyWriter,
        @NonNull Writer<V> valueWriter,
        @Nullable Ruler keyRuler) {}
