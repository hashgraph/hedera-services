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

package com.hedera.node.app.spi.meta.bni;

import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * Defines how to translate some state read into the result of a {@code ContractCall} record.
 *
 * <p>Note that if the requested state could not be read, the result is always {@code null}
 * and there would be nothing to translate.
 *
 * @param <T> the type of state read
 */
public interface ResultTranslator<T> {
    /**
     * Translates the given state read into the result of a {@code ContractCall} record.
     *
     * @param entity the state read
     * @return the result of a {@code ContractCall} record
     */
    @NonNull
    Bytes computeResult(@NonNull T entity);
}
