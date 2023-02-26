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

package com.swirlds.common.test.state;

import com.swirlds.common.system.Round;
import com.swirlds.common.system.SwirldDualState;
import com.swirlds.common.system.SwirldState1;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.transaction.Transaction;
import java.time.Instant;
import java.util.Objects;

/**
 * A dummy swirld state for SignedStateManager unit tests.
 */
public class DummySwirldState1 extends AbstractDummySwirldState implements SwirldState1 {

    // The version history of this class.
    // Versions that have been released must NEVER be given a different value.
    /**
     * In this version, serialization was performed by copyTo/copyToExtra and deserialization was performed by
     * copyFrom/copyFromExtra. This version is not supported by later deserialization methods and must be handled
     * specially by the platform.
     */
    private static final int VERSION_ORIGINAL = 1;

    private static final int CLASS_VERSION = VERSION_ORIGINAL;

    private static final long CLASS_ID = 0xa7d6e4b5feda7cd3L;

    public DummySwirldState1() {
        super();
    }

    private DummySwirldState1(final DummySwirldState1 that) {
        super(that);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AddressBook getAddressBookCopy() {
        return super.getAddressBookCopy();
    }

    @Override
    public void handleConsensusRound(final Round round, final SwirldDualState swirldDualState) {
        // intentionally does nothing
    }

    @Override
    public void handleTransaction(
            final long creatorId,
            final Instant timeCreated,
            final Instant estimatedTimestamp,
            final Transaction trans,
            final SwirldDualState swirldDualState) {
        // intentionally does nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public DummySwirldState1 copy() {
        throwIfImmutable();
        return new DummySwirldState1(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(final Object obj) {
        if (obj == this) {
            return true;
        }
        if (!(obj instanceof final DummySwirldState1 that)) {
            return false;
        }
        return Objects.equals(this.addressBook, that.addressBook);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        return 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getClassId() {
        return CLASS_ID;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getVersion() {
        return CLASS_VERSION;
    }
}
