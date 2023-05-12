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

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import com.swirlds.common.io.streams.SerializableDataInputStream;
import com.swirlds.common.io.streams.SerializableDataOutputStream;
import com.swirlds.common.merkle.MerkleLeaf;
import com.swirlds.common.merkle.impl.PartialMerkleLeaf;
import com.swirlds.common.system.address.AddressBook;
import java.io.IOException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public abstract class AbstractDummySwirldState extends PartialMerkleLeaf implements MerkleLeaf {

    private static final long DEFAULT_UNIT_TEST_SECS = 10;

    protected volatile boolean allowDeletion;

    protected AddressBook addressBook;

    protected CountDownLatch deletionLatch = new CountDownLatch(1);

    protected AtomicBoolean released = new AtomicBoolean(false);

    protected AbstractDummySwirldState() {
        this(false);
    }

    /**
     * protection should always be enabled but current unit tests don't expect this behavior
     *
     * @param protectionEnabled If protection is enabled then this SignedState can only be deleted after explicitly
     *                          enabled.
     */
    protected AbstractDummySwirldState(boolean protectionEnabled) {
        allowDeletion = !protectionEnabled;
    }

    protected AbstractDummySwirldState(final AbstractDummySwirldState that) {
        super(that);
        if (that.addressBook != null) {
            this.addressBook = that.addressBook.copy();
        }
        this.allowDeletion = that.allowDeletion;
        this.deletionLatch = that.deletionLatch;
        this.released = new AtomicBoolean(that.released.get());
    }

    public void enableDeletion() {
        allowDeletion = true;
    }

    public void disableDeletion() {
        allowDeletion = false;
    }

    public void waitForDeletion() {
        try {
            // 10 seconds is assumed to be more than sufficient for any unit test. If a test requires
            // a greater wait then a variable timeout parameter can be added.
            assertTrue(
                    deletionLatch.await(DEFAULT_UNIT_TEST_SECS, TimeUnit.SECONDS),
                    "Unit test took longer than the default of 10 seconds. Fix the test or override the wait time.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            fail();
        }
    }

    protected AddressBook getAddressBookCopy() {
        if (addressBook == null) {
            return null;
        } else {
            return addressBook.copy();
        }
    }

    public void setAddressBook(AddressBook addressBook) {
        this.addressBook = addressBook;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void destroyNode() {
        if (!allowDeletion) {
            fail("State is not allowed to be deleted");
        }
        if (!released.compareAndSet(false, true)) {
            throw new IllegalStateException("This type of node should only be deleted once");
        }
        deletionLatch.countDown();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void serialize(SerializableDataOutputStream out) throws IOException {
        // intentionally does nothing
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void deserialize(SerializableDataInputStream in, int version) throws IOException {
        // intentionally does nothing
    }
}
