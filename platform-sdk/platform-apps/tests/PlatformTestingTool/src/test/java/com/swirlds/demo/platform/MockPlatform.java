/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.swirlds.demo.platform;

import static org.mockito.Mockito.mock;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.crypto.SignatureType;
import com.swirlds.common.metrics.Metrics;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.system.events.PlatformEvent;
import com.swirlds.common.utility.AutoCloseableWrapper;
import java.time.Instant;

public class MockPlatform implements Platform {

    private AddressBook addressBook;
    private NodeId nodeId;
    private AutoCloseableWrapper<? extends SwirldState> lastCompleteSwirldState;
    private Metrics metrics;

    private MockPlatform() {}

    @Override
    public int getInstanceNumber() {
        return 0;
    }

    @Override
    public boolean createTransaction(final byte[] trans) {
        return false;
    }

    @Override
    public Instant estimateTime() {
        return null;
    }

    public PlatformEvent[] getAllEvents() {
        return new PlatformEvent[0];
    }

    @Override
    public AddressBook getAddressBook() {
        return null;
    }

    @Override
    public PlatformContext getContext() {
        return mock(PlatformContext.class);
    }

    @Override
    public NotificationEngine getNotificationEngine() {
        return mock(NotificationEngine.class);
    }

    @Override
    public NodeId getSelfId() {
        return this.nodeId;
    }

    @Override
    public Signature sign(final byte[] data) {
        return new Signature(SignatureType.RSA, new byte[SignatureType.RSA.signatureLength()]);
    }

    public static MockPlatformBuilder newBuilder() {
        return new MockPlatformBuilder();
    }

    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState() {
        return new AutoCloseableWrapper<>(null, () -> {});
    }

    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestSignedState() {
        return new AutoCloseableWrapper<>(null, () -> {});
    }

    public static class MockPlatformBuilder {

        private final MockPlatform mockPlatform;

        private MockPlatformBuilder() {
            this.mockPlatform = new MockPlatform();
        }

        public MockPlatform build() {
            return this.mockPlatform;
        }

        public MockPlatformBuilder setAddressBook(final AddressBook addressBook) {
            this.mockPlatform.addressBook = addressBook;
            return this;
        }

        public MockPlatformBuilder setNodeId(final NodeId nodeId) {
            this.mockPlatform.nodeId = nodeId;
            return this;
        }

        public MockPlatformBuilder setLastCompleteSwirldState(
                final AutoCloseableWrapper<? extends SwirldState> lastCompleteSwirldState) {
            this.mockPlatform.lastCompleteSwirldState = lastCompleteSwirldState;
            return this;
        }

        public MockPlatformBuilder setMetrics(final Metrics metrics) {
            this.mockPlatform.metrics = metrics;
            return this;
        }
    }
}
