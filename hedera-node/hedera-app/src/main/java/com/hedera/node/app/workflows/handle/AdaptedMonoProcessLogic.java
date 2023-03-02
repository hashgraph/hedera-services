/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerAndOthers;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.forPayerOnly;
import static com.hedera.node.app.service.mono.utils.RationalizedSigMeta.noneAvailable;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.order.LinkedRefs;
import com.hedera.node.app.service.mono.state.logic.StandardProcessLogic;
import com.hedera.node.app.service.mono.txns.ProcessLogic;
import com.hedera.node.app.service.mono.utils.accessors.PlatformTxnAccessor;
import com.hedera.node.app.service.mono.utils.accessors.SwirldsTxnAccessor;
import com.hedera.node.app.spi.meta.TransactionMetadata;
import com.hedera.node.app.workflows.prehandle.PreHandleWorkflow;
import com.swirlds.common.system.transaction.ConsensusTransaction;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AdaptedMonoProcessLogic implements ProcessLogic {
    private final PreHandleWorkflow preHandleWorkflow;
    private final StandardProcessLogic monoProcessLogic;

    @Inject
    public AdaptedMonoProcessLogic(
            @NonNull final PreHandleWorkflow preHandleWorkflow, @NonNull final StandardProcessLogic monoProcessLogic) {
        this.preHandleWorkflow = preHandleWorkflow;
        this.monoProcessLogic = monoProcessLogic;
    }

    @Override
    public void incorporateConsensusTxn(final ConsensusTransaction platformTxn, final long submittingMember) {
        if (platformTxn.getMetadata() instanceof TransactionMetadata metadata) {
            final var accessor = adaptForMono(platformTxn, metadata);
            platformTxn.setMetadata(accessor);
        }
        monoProcessLogic.incorporateConsensusTxn(platformTxn, submittingMember);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private SwirldsTxnAccessor adaptForMono(
            final ConsensusTransaction platformTxn, final TransactionMetadata metadata) {
        try {
            final var accessor = PlatformTxnAccessor.from(platformTxn.getContents());
            // TODO - recompute required keys and compare with metadata
            accessor.addAllCryptoSigs(metadata.cryptoSignatures());
            final var preHandleStatus = metadata.status();
            final var payerKey = metadata.payerKey();
            if (payerKey != null) {
                if (preHandleStatus != OK) {
                    accessor.setSigMeta(forPayerOnly((JKey) payerKey, metadata.cryptoSignatures(), accessor));
                } else {
                    accessor.setSigMeta(forPayerAndOthers(
                            (JKey) payerKey, (List) metadata.otherPartyKeys(), metadata.cryptoSignatures(), accessor));
                }
            } else {
                accessor.setSigMeta(noneAvailable());
            }
            // Prevent the mono-service worfklow from rationalizing sigs
            accessor.setExpandedSigStatus(preHandleStatus);
            accessor.setLinkedRefs(new LinkedRefs());
            return accessor;
        } catch (InvalidProtocolBufferException e) {
            throw new AssertionError("Not implemented");
        }
    }
}
