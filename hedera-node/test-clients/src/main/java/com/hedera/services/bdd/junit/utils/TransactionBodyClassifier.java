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

package com.hedera.services.bdd.junit.utils;

import static com.hedera.node.app.hapi.utils.CommonUtils.functionOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NONE;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.NodeStakeUpdate;
import static com.hederahashgraph.api.proto.java.TransactionBody.DataCase.NODE_STAKE_UPDATE;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.node.app.hapi.utils.exception.UnknownHederaFunctionality;
import com.hedera.services.stream.proto.RecordStreamItem;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody.DataCase;
import java.util.HashSet;
import java.util.Set;

public class TransactionBodyClassifier {
    private final Set<HederaFunctionality> transactionType = new HashSet<>();

    public void incorporate(final RecordStreamItem item) throws InvalidProtocolBufferException {
        var txnType = NONE;
        TransactionBody txnBody = CommonUtils.extractTransactionBody(item.getTransaction());

        try {
            txnType = functionOf(txnBody);
        } catch (UnknownHederaFunctionality ex) {
            txnType = checkNodeStakeUpdate(txnBody);
        }
        transactionType.add(txnType);
    }

    private HederaFunctionality checkNodeStakeUpdate(final TransactionBody txn) {
        DataCase dataCase = txn.getDataCase();
        return dataCase.equals(NODE_STAKE_UPDATE) ? NodeStakeUpdate : NONE;
    }

    public boolean isInvalid() {
        return transactionType.contains(NONE);
    }
}
