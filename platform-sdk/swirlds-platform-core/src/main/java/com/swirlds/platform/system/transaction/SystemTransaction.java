/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.system.transaction;

import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.crypto.TransactionSignature;
import java.util.Collections;
import java.util.List;

/**
 * A system transaction used by the platform only. These transactions are not passed to the application.
 */
public abstract class SystemTransaction extends ConsensusTransactionImpl {

    /** All system transactions have empty contents. */
    private static final byte[] EMPTY_CONTENTS = new byte[0];

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isSystem() {
        return true;
    }

    /**
     * {@inheritDoc}
     *
     * The contents of system transactions is always emtpy.
     */
    @Override
    public byte[] getContents() {
        return EMPTY_CONTENTS;
    }

    /**
     * {@inheritDoc}
     *
     * System transactions do not store metadata, so this returns {@code null}.
     */
    @Override
    public <T> T getMetadata() {
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * System transactions do not store metadata, so this method does nothing.
     */
    @Override
    public <T> void setMetadata(final T metadata) {
        // do nothing
    }
}
