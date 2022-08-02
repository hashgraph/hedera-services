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
package com.hedera.services.utils.accessors;

import static com.hedera.services.legacy.proto.utils.CommonUtils.extractTransactionBody;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.NodeInfo;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.PureTransferSemanticChecks;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hedera.services.utils.accessors.custom.CryptoCreateAccessor;
import com.hedera.services.utils.accessors.custom.CryptoTransferAccessor;
import com.hedera.services.utils.accessors.custom.SubmitMessageAccessor;
import com.hedera.services.utils.accessors.custom.TokenWipeAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.swirlds.merkle.map.MerkleMap;
import java.util.function.Supplier;
import javax.inject.Inject;

public class AccessorFactory {
    private final GlobalDynamicProperties dynamicProperties;
    private final OptionValidator validator;
    private final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts;
    private final NodeInfo nodeInfo;

    private final PureTransferSemanticChecks transferChecks;

    @Inject
    public AccessorFactory(
            final GlobalDynamicProperties dynamicProperties,
            final OptionValidator validator,
            final Supplier<MerkleMap<EntityNum, MerkleAccount>> accounts,
            final NodeInfo nodeInfo,
            final PureTransferSemanticChecks transferChecks) {
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
        this.accounts = accounts;
        this.nodeInfo = nodeInfo;
        this.transferChecks = transferChecks;
    }

    public TxnAccessor nonTriggeredTxn(byte[] signedTxnWrapperBytes)
            throws InvalidProtocolBufferException {
        final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
        subtype.setScheduleRef(null);
        return subtype;
    }

    public TxnAccessor triggeredTxn(
            byte[] signedTxnWrapperBytes,
            final AccountID payer,
            ScheduleID parent,
            boolean markThrottleExempt,
            boolean markCongestionExempt)
            throws InvalidProtocolBufferException {
        final var subtype = constructSpecializedAccessor(signedTxnWrapperBytes);
        subtype.setScheduleRef(parent);
        subtype.setPayer(payer);
        if (markThrottleExempt) {
            subtype.markThrottleExempt();
        }
        if (markCongestionExempt) {
            subtype.markCongestionExempt();
        }
        return subtype;
    }

    /**
     * parse the signedTxnWrapperBytes, figure out what specialized implementation to use construct
     * the subtype instance
     *
     * @param signedTxnWrapperBytes
     * @return
     */
    public SignedTxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes)
            throws InvalidProtocolBufferException {
        final var signedTxn = Transaction.parseFrom(signedTxnWrapperBytes);
        final var body = extractTransactionBody(signedTxn);
        final var function = MiscUtils.FUNCTION_EXTRACTOR.apply(body);
        return switch (function) {
            case TokenAccountWipe -> new TokenWipeAccessor(
                    signedTxnWrapperBytes, signedTxn, dynamicProperties);
            case CryptoTransfer -> new CryptoTransferAccessor(
                    signedTxnWrapperBytes, signedTxn, dynamicProperties, transferChecks);
            case CryptoCreate -> new CryptoCreateAccessor(
                    signedTxnWrapperBytes,
                    signedTxn,
                    dynamicProperties,
                    validator,
                    accounts,
                    nodeInfo);
            case ConsensusSubmitMessage -> new SubmitMessageAccessor(
                    signedTxnWrapperBytes, signedTxn);
            default -> SignedTxnAccessor.from(signedTxnWrapperBytes, signedTxn);
        };
    }
}
