/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class includes methods for generating Fee Matrices and calculating Fee for File related
 * Transactions and Query.
 */
@Singleton
public final class FileFeeBuilder extends FeeBuilder {
    @Inject
    public FileFeeBuilder() {
        /* No-op */
    }

    /**
     * This method returns fee matrices for file content query
     *
     * @param contentSize content size
     * @param responseType response type
     * @return fee data
     */
    public FeeData getFileContentQueryFeeMatrices(int contentSize, ResponseType responseType) {
        // get the Fee Matrices
        long bpt = 0;
        long vpt = 0;
        long rbs = 0;
        long sbs = 0;
        long gas = 0;
        long tv = 0;
        long bpr = 0;
        long sbpr = 0;

        /*
         * FileGetContentsQuery QueryHeader Transaction - CryptoTransfer - (will be taken care in
         * Transaction processing) ResponseType - INT_SIZE FileID - BASIC_ENTITY_ID_SIZE
         */

        bpt = calculateBPT();
        /*
         *
         * Response header NodeTransactionPrecheckCode - 4 bytes ResponseType - 4 bytes
         *
         * FileContents FileID fileID - BASIC_ENTITY_ID_SIZE bytes content - calculated value (size of the
         * content)
         *
         */

        bpr = BASIC_QUERY_RES_HEADER + getStateProofSize(responseType);

        sbpr = (long) BASIC_ENTITY_ID_SIZE + contentSize;

        FeeComponents feeMatrices = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(sbs)
                .setGas(gas)
                .setTv(tv)
                .setBpr(bpr)
                .setSbpr(sbpr)
                .build();

        return getQueryFeeDataMatrices(feeMatrices);
    }

    public FeeData getSystemDeleteFileTxFeeMatrices(TransactionBody txBody, SigValueObj numSignatures) {
        long bpt = 0;
        long vpt = 0;
        long rbs = 0;
        long sbs = 0;
        long gas = 0;
        long tv = 0;
        long bpr = 0;
        long sbpr = 0;

        // get the bytes per second
        bpt = getCommonTransactionBodyBytes(txBody);
        bpt = bpt + BASIC_ENTITY_ID_SIZE + LONG_SIZE;
        vpt = numSignatures.getTotalSigCount();

        rbs = calculateRBS(txBody);

        long rbsNetwork = getDefaultRBHNetworkSize();

        // sbs should not be charged as the fee for storage was already paid. What if expiration is
        // changed though?

        FeeComponents feeMatricesForTx = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(sbs)
                .setGas(gas)
                .setTv(tv)
                .setBpr(bpr)
                .setSbpr(sbpr)
                .build();

        return getFeeDataMatrices(feeMatricesForTx, numSignatures.getPayerAcctSigCount(), rbsNetwork);
    }

    public FeeData getSystemUnDeleteFileTxFeeMatrices(TransactionBody txBody, SigValueObj numSignatures) {
        long bpt = 0;
        long vpt = 0;
        long rbs = 0;
        long sbs = 0;
        long gas = 0;
        long tv = 0;
        long bpr = 0;
        long sbpr = 0;

        // get the bytes per second
        bpt = getCommonTransactionBodyBytes(txBody);
        bpt = bpt + BASIC_ENTITY_ID_SIZE + LONG_SIZE;
        vpt = numSignatures.getTotalSigCount();
        rbs = calculateRBS(txBody);
        long rbsNetwork = getDefaultRBHNetworkSize();

        // sbs should not be charged as the fee for storage was already paid. What if expiration is
        // changed though?

        FeeComponents feeMatricesForTx = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(sbs)
                .setGas(gas)
                .setTv(tv)
                .setBpr(bpr)
                .setSbpr(sbpr)
                .build();

        return getFeeDataMatrices(feeMatricesForTx, numSignatures.getPayerAcctSigCount(), rbsNetwork);
    }

    public FeeData getFileDeleteTxFeeMatrices(TransactionBody txBody, SigValueObj sigValObj) {
        long bpt = 0;
        long vpt = 0;
        long rbs = 0;
        long sbs = 0;
        long gas = 0;
        long tv = 0;
        long bpr = 0;
        long sbpr = 0;

        final long txBodySize = getCommonTransactionBodyBytes(txBody);

        // bpt - Bytes per Transaction
        bpt = txBodySize + BASIC_ENTITY_ID_SIZE + sigValObj.getSignatureSize();

        // vpt - verifications per transactions
        vpt = sigValObj.getTotalSigCount();

        bpr = INT_SIZE;

        rbs = calculateRBS(txBody);

        long rbsNetwork = getDefaultRBHNetworkSize();

        FeeComponents feeMatricesForTx = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(sbs)
                .setGas(gas)
                .setTv(tv)
                .setBpr(bpr)
                .setSbpr(sbpr)
                .build();

        return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
    }
}
