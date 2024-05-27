/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.fixtures.state;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.Signature;
import com.swirlds.common.notification.NotificationEngine;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.utility.AutoCloseableWrapper;
import com.swirlds.platform.system.Platform;
import com.swirlds.platform.system.SwirldState;
import com.swirlds.platform.system.address.AddressBook;
import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A fake implementation of the {@link Platform} interface.
 */
public final class FakePlatform implements Platform {
    private final NodeId selfNodeId;
    private final AddressBook addressBook;

    public FakePlatform(long selfNodeId, AddressBook addressBook) {
        this.selfNodeId = new NodeId(selfNodeId);
        this.addressBook = addressBook;
    }

    @Override
    public PlatformContext getContext() {
        return null;
    }

    @Override
    public NotificationEngine getNotificationEngine() {
        return null;
    }

    @Override
    public Signature sign(byte[] bytes) {
        return null;
    }

    @Override
    public AddressBook getAddressBook() {
        return addressBook;
    }

    @Override
    public NodeId getSelfId() {
        return selfNodeId;
    }

    @Override
    public <T extends SwirldState> AutoCloseableWrapper<T> getLatestImmutableState(@NonNull String s) {
        return null;
    }

    @Override
    public boolean createTransaction(@NonNull byte[] bytes) {
        return false;
    }

    @Override
    public void start() {}
}
