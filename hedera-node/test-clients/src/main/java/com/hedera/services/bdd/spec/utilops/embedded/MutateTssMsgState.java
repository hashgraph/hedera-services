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

package com.hedera.services.bdd.spec.utilops.embedded;

import static com.hedera.node.app.hapi.utils.CommonPbjConverters.toPbj;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.transactions.TxnUtils;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.function.Consumer;

public class MutateTssMsgState extends UtilOp {
    private final String sourceRoster;
    private final String targetRoster;
    private final Consumer<TssMessageTransactionBody.Builder> mutation;

    public MutateTssMsgState(
            final String sourceRoster,
            final String targetRoster,
            final Consumer<TssMessageTransactionBody.Builder> mutation) {
        this.sourceRoster = sourceRoster;
        this.targetRoster = targetRoster;
        this.mutation = mutation;
    }

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var tssMessages = spec.embeddedTssMsgStateOrThrow();
        //        final var sourceRoster = spec.setup()
        final var targetRoster = toPbj(TxnUtils.asId("roster", spec));

        var sequenceNumber = 0;
        for (int i = 0; i < 2; i++) {
            final var tsMessageMapKey = TssMessageMapKey.newBuilder()
                    //                    .rosterHash(targetRoster)
                    .sequenceNumber(sequenceNumber++)
                    .build();
            final var tssMsgBody = TssMessageTransactionBody.newBuilder()
                    //                    .sourceRosterHash(targetRoster)
                    //                    .targetRosterHash(targetRoster)
                    .shareIndex(sequenceNumber)
                    .tssMessage(Bytes.EMPTY)
                    .build();
            tssMessages.put(tsMessageMapKey, tssMsgBody);
        }

        spec.commitEmbeddedState();
        return false;
    }
}
