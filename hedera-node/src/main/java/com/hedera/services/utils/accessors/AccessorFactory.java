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
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenAccountWipe;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.Transaction;
import javax.inject.Inject;

public class AccessorFactory {
    final GlobalDynamicProperties dynamicProperties;
    final OptionValidator validator;

    @Inject
    public AccessorFactory(
            final GlobalDynamicProperties dynamicProperties, final OptionValidator validator) {
        this.dynamicProperties = dynamicProperties;
        this.validator = validator;
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
    private TxnAccessor constructSpecializedAccessor(byte[] signedTxnWrapperBytes)
            throws InvalidProtocolBufferException {
        final var body = extractTransactionBody(Transaction.parseFrom(signedTxnWrapperBytes));
        final var function = MiscUtils.FUNCTION_EXTRACTOR.apply(body);
        if (function == TokenAccountWipe) {
            return new TokenWipeAccessor(signedTxnWrapperBytes, dynamicProperties, validator);
        }
        return SignedTxnAccessor.from(signedTxnWrapperBytes);
    }
}
