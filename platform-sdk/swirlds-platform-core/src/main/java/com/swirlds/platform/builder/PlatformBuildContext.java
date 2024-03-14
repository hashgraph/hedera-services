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

package com.swirlds.platform.builder;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.platform.NodeId;
import com.swirlds.common.stream.Signer;
import com.swirlds.platform.eventhandling.TransactionPool;
import com.swirlds.platform.system.SoftwareVersion;
import com.swirlds.platform.system.address.AddressBook;
import com.swirlds.platform.system.status.PlatformStatus;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Objects and utilities needed to construct platform components.
 */
public class PlatformBuildContext {
    private final PlatformContext platformContext;
    private final Signer signer;
    private final AddressBook initialAddressBook;
    private final NodeId selfId;
    private final SoftwareVersion appVersion;
    private final TransactionPool transactionPool;
    private final LongSupplier intakeQueueSizeSupplier;
    private final Supplier<PlatformStatus> platformStatusSupplier;

    // TODO fix nullity
    public PlatformBuildContext(
            final PlatformContext platformContext,
            final Signer signer,
            final AddressBook initialAddressBook,
            final NodeId selfId,
            final SoftwareVersion appVersion,
            final TransactionPool transactionPool,
            final LongSupplier intakeQueueSizeSupplier,
            final Supplier<PlatformStatus> platformStatusSupplier) {
        this.platformContext = platformContext;
        this.signer = signer;
        this.initialAddressBook = initialAddressBook;
        this.selfId = selfId;
        this.appVersion = appVersion;
        this.transactionPool = transactionPool;
        this.intakeQueueSizeSupplier = intakeQueueSizeSupplier;
        this.platformStatusSupplier = platformStatusSupplier;
    }

    public PlatformContext getPlatformContext() {
        return platformContext;
    }

    public Signer getSigner() {
        return signer;
    }

    public AddressBook getInitialAddressBook() {
        return initialAddressBook;
    }

    public NodeId getSelfId() {
        return selfId;
    }

    public SoftwareVersion getAppVersion() {
        return appVersion;
    }

    public TransactionPool getTransactionPool() {
        return transactionPool;
    }

    public LongSupplier getIntakeQueueSizeSupplier() {
        return intakeQueueSizeSupplier;
    }

    public Supplier<PlatformStatus> getPlatformStatusSupplier() {
        return platformStatusSupplier;
    }
}
