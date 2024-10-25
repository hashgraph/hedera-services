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

import static com.hedera.node.app.service.addressbook.impl.ReadableNodeStoreImpl.constructFromNodesState;

import com.hedera.hapi.node.state.tss.TssMessageMapKey;
import com.hedera.hapi.services.auxiliary.tss.TssMessageTransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hedera.services.bdd.spec.HapiSpec;
import com.hedera.services.bdd.spec.utilops.UtilOp;
import com.swirlds.platform.roster.RosterUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Objects;

public class MutateTssMsgState extends UtilOp {
    public MutateTssMsgState() {}

    @Override
    protected boolean submitOp(@NonNull final HapiSpec spec) throws Throwable {
        final var rosterSingletonState = spec.embeddedRosterStateOrThrow().get();

        final var activeRosterHash = Objects.requireNonNull(rosterSingletonState)
                .roundRosterPairs()
                .get(0)
                .activeRosterHash();
        final var tssMessages = spec.embeddedTssMsgStateOrThrow();

        var sequenceNumber = 0;
        // Since each node will get 3 shares when weight of nodes is 1 in Embedded test,
        // we need to create 12 TssMessages
        for (int i = 0; i < 12; i++) {
            final var tsMessageMapKey = TssMessageMapKey.newBuilder()
                    .rosterHash(activeRosterHash)
                    .sequenceNumber(sequenceNumber++)
                    .build();
            final var tssMsgBody = TssMessageTransactionBody.newBuilder()
                    .sourceRosterHash(Bytes.EMPTY)
                    .targetRosterHash(activeRosterHash)
                    .shareIndex(sequenceNumber)
                    .tssMessage(Bytes.EMPTY)
                    .build();
            tssMessages.put(tsMessageMapKey, tssMsgBody);
        }

        spec.commitEmbeddedState();
        return false;
    }
}
