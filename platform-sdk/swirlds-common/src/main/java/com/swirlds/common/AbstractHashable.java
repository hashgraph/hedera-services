/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.common;

import com.swirlds.common.crypto.Hash;
import com.swirlds.common.crypto.Hashable;

/**
 * Boilerplate implementation for the Hashable interface.
 */
public abstract class AbstractHashable implements Hashable {

    private Hash hash = null;

    /**
     * {@inheritDoc}
     */
    @Override
    public Hash getHash() {
        return hash;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setHash(Hash hash) {
        this.hash = hash;
    }
}
