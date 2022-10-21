/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.fees.calculation.consensus.txns;

import static com.hederahashgraph.fee.ConsensusServiceFeeBuilder.getConsensusUpdateTopicFee;
import static com.hederahashgraph.fee.ConsensusServiceFeeBuilder.getUpdateTopicRbsIncrease;

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.fees.calculation.TxnResourceUsageEstimator;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.utils.EntityNum;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.exception.InvalidTxBodyException;
import com.hederahashgraph.fee.SigValueObj;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.commons.codec.DecoderException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public final class UpdateTopicResourceUsage implements TxnResourceUsageEstimator {
    private static final Logger log = LogManager.getLogger(UpdateTopicResourceUsage.class);

    @Inject
    public UpdateTopicResourceUsage() {
        /* No-op */
    }

    @Override
    public boolean applicableTo(final TransactionBody txn) {
        return txn.hasConsensusUpdateTopic();
    }

    @Override
    public FeeData usageGiven(
            @Nullable final TransactionBody txnBody,
            final SigValueObj sigUsage,
            @Nullable final StateView view)
            throws InvalidTxBodyException, IllegalStateException {
        if (txnBody == null || !txnBody.hasConsensusUpdateTopic()) {
            throw new InvalidTxBodyException(
                    "consensusUpdateTopic field not available for Fee Calculation");
        }
        if (view == null) {
            throw new IllegalStateException("No StateView present !!");
        }

        long rbsIncrease = 0;
        final var merkleTopic =
                view.topics()
                        .get(EntityNum.fromTopicId(txnBody.getConsensusUpdateTopic().getTopicID()));

        if (merkleTopic != null && merkleTopic.hasAdminKey()) {
            final var expiry =
                    Timestamp.newBuilder()
                            .setSeconds(merkleTopic.getExpirationTimestamp().getSeconds())
                            .build();
            try {
                rbsIncrease =
                        getUpdateTopicRbsIncrease(
                                txnBody.getTransactionID().getTransactionValidStart(),
                                JKey.mapJKey(merkleTopic.getAdminKey()),
                                JKey.mapJKey(merkleTopic.getSubmitKey()),
                                merkleTopic.getMemo(),
                                merkleTopic.hasAutoRenewAccountId(),
                                expiry,
                                txnBody.getConsensusUpdateTopic());
            } catch (final DecoderException illegal) {
                log.warn("Usage estimation unexpectedly failed for {}!", txnBody, illegal);
                throw new InvalidTxBodyException(illegal);
            }
        }
        return getConsensusUpdateTopicFee(txnBody, rbsIncrease, sigUsage);
    }
}
