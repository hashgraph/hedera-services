/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.common.stream;

import com.swirlds.common.crypto.Signature;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * An object capable of signing data.
 */
@FunctionalInterface
public interface Signer {

    /**
     * generate signature bytes for given data
     *
     * @param data an array of bytes
     * @return signature bytes
     */
    @NonNull
    Signature sign(@NonNull byte[] data);
}
