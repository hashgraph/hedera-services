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

package com.hedera.node.app.hapi.utils.fee;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hederahashgraph.api.proto.java.EthereumTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.Test;

class SmartContractFeeBuilderTest {
    private final TransactionBody.Builder transactionBodyBuilder =
            TransactionBody.newBuilder().setMemo("memo tx");
    private final SigValueObj signValueObj = new SigValueObj(2, 2, 2);
    private final SmartContractFeeBuilder smartContractFeeBuilder = new SmartContractFeeBuilder();

    @Test
    void assertGetFileContentQueryFeeMatrices() {
        var transactionBody = transactionBodyBuilder
                .setEthereumTransaction(EthereumTransactionBody.newBuilder())
                .build();
        var result = smartContractFeeBuilder.getEthereumTransactionFeeMatrices(transactionBody, signValueObj);
        assertEquals(1, result.getNodedata().getConstant());
        assertEquals(229, result.getNodedata().getBpt());
        assertEquals(2, result.getNodedata().getVpt());
        assertEquals(4, result.getNodedata().getBpr());
        assertEquals(0, result.getNodedata().getSbpr());

        assertEquals(1, result.getNetworkdata().getConstant());
        assertEquals(229, result.getNetworkdata().getBpt());
        assertEquals(3, result.getNetworkdata().getVpt());
        assertEquals(1, result.getNetworkdata().getRbh());

        assertEquals(1, result.getServicedata().getConstant());
        assertEquals(3481, result.getServicedata().getRbh());
        assertEquals(0, result.getServicedata().getSbh());
        assertEquals(0, result.getServicedata().getTv());
    }
}
