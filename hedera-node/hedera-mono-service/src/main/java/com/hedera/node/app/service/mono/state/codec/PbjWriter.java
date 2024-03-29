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

package com.hedera.node.app.service.mono.state.codec;

import com.hedera.pbj.runtime.io.WritableSequentialData;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;

/**
 * Defines a writer to a PBJ {@link WritableSequentialData}; helpful for building {@link com.hedera.pbj.runtime.Codec}
 * implementations from a method reference to a PBJ-generated {@code Writer}
 * implementation.
 *
 * @param <T> the type of object being written
 */
@FunctionalInterface
public interface PbjWriter<T> {
    void write(@NonNull T item, @NonNull WritableSequentialData output) throws IOException;
}
