/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.node.app.spi.meta;

import com.hedera.node.app.spi.AccountKeyLookup;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import java.util.Collections;

/**
 * Metadata collected when transactions are handled as part of "pre-handle" needed for signature
 * verification. This class may have subclasses in the future.
 *
 * <p>NOTE: This class shouldn't exist here, and is something of a puzzle. We cannot add it to SPI,
 * because it includes a dependency on AccountStore. But we also cannot put it in the app module,
 * because doing so would cause service modules to have a circular dependency on the app module.
 * Maybe we need some kind of base module from which services can extend and put it there?
 */
public class ScheduleSigTransactionMetadata extends SigTransactionMetadata
        implements ScheduleTransactionMetadata {
    private TransactionMetadata scheduledTxnMeta;

    public ScheduleSigTransactionMetadata(
            final TransactionBody topLevelTxn,
            final AccountID payer,
            final ResponseCodeEnum status,
            final TransactionMetadata scheduledTxnMeta) {
        super(topLevelTxn, payer, status, Collections.emptyList());
        this.scheduledTxnMeta = scheduledTxnMeta;
    }

    @Override
    public TransactionMetadata scheduledMeta() {
        return scheduledTxnMeta;
    }
}
