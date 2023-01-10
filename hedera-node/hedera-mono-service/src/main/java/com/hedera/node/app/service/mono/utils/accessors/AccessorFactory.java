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
package com.hedera.node.app.service.mono.utils.accessors;

import static com.hedera.node.app.hapi.utils.CommonUtils.extractTransactionBody;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import javax.inject.Inject;

public class AccessorFactory {
    private final GlobalDynamicProperties dynamicProperties;

    @Inject
    public AccessorFactory(final GlobalDynamicProperties dynamicProperties) {
        this.dynamicProperties = dynamicProperties;
    }

    public TxnAccessor nonTriggeredTxn(final byte[] transactionBytes)
            throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(
                transactionBytes, Transaction.parseFrom(transactionBytes));
    }

    public TxnAccessor triggeredTxn(
            final Transaction transaction,
            final AccountID payer,
            final ScheduleID parent,
            final boolean markThrottleExempt,
            final boolean markCongestionExempt)
            throws InvalidProtocolBufferException {
        final var subtype = constructSpecializedAccessor(transaction);
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
     * Given a gRPC {@link Transaction}, returns a {@link SignedTxnAccessor} specialized to handle
     * the transaction's logical operation.
     *
     * @param transaction the gRPC transaction
     * @return a specialized accessor
     */
    public SignedTxnAccessor constructSpecializedAccessor(final Transaction transaction)
            throws InvalidProtocolBufferException {
        return internalSpecializedConstruction(transaction.toByteArray(), transaction);
    }

    private SignedTxnAccessor internalSpecializedConstruction(
            final byte[] transactionBytes, final Transaction transaction)
            throws InvalidProtocolBufferException {
        final var body = extractTransactionBody(transaction);
        final var function = MiscUtils.FUNCTION_EXTRACTOR.apply(body);
        if (function == TokenAccountWipe) {
            return new TokenWipeAccessor(transactionBytes, transaction, dynamicProperties);
        }
        return SignedTxnAccessor.from(transactionBytes, transaction);
    }

    public TxnAccessor uncheckedSpecializedAccessor(final Transaction transaction) {
        try {
            return constructSpecializedAccessor(transaction);
        } catch (final InvalidProtocolBufferException e) {
            throw new IllegalArgumentException("Not a valid signed transaction");
        }
    }
}
