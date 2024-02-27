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

package com.hedera.node.app.bbm.singleton;

import com.hedera.hapi.node.state.recordcache.TransactionRecordEntry;
import com.hedera.node.app.service.mono.state.submerkle.EntityId;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.submerkle.RichInstant;

public record TxnRecord(RichInstant consensusTime, EntityId payer, RichInstant txnValidStart) {

    public static TxnRecord fromMod(TransactionRecordEntry recordEntry) {
        // todo fix possible null pointer exceptions!!!
        var consensusTimestamp = recordEntry.transactionRecord().consensusTimestamp();
        var validStartTimestamp =
                recordEntry.transactionRecord().transactionID().transactionValidStart();
        return new TxnRecord(
                new RichInstant(consensusTimestamp.seconds(), consensusTimestamp.nanos()),
                EntityId.fromPbjAccountId(recordEntry.payerAccountId()),
                new RichInstant(validStartTimestamp.seconds(), validStartTimestamp.nanos()));
    }

    public static TxnRecord fromMono(ExpirableTxnRecord record) {
        return new TxnRecord(
                record.getConsensusTime(),
                record.getTxnId().getPayerAccount(),
                record.getTxnId().getValidStart());
    }
}
