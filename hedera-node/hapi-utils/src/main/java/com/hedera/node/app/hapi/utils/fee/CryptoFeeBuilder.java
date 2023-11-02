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

import com.hederahashgraph.api.proto.java.CryptoCreateTransactionBody;
import com.hederahashgraph.api.proto.java.FeeComponents;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class includes methods for generating Fee Matrices and calculating Fee for Crypto related
 * Transactions and Query.
 */
@Singleton
public final class CryptoFeeBuilder extends FeeBuilder {
    @Inject
    public CryptoFeeBuilder() {
        /* No-op */
    }

    /**
     * This method returns the fee matrices for crypto create transaction
     *
     * @param txBody transaction body
     * @param sigValObj signature value object
     * @return fee data
     */
    public static FeeData getCryptoCreateTxFeeMatrices(final TransactionBody txBody, final SigValueObj sigValObj) {
        final var txBodySize = getCommonTransactionBodyBytes(txBody);
        final var cryptoCreate = txBody.getCryptoCreateAccount();
        final var cryptoCreateSize = getCryptoCreateAccountBodyTxSize(cryptoCreate);
        final long bpt = txBodySize + cryptoCreateSize + sigValObj.getSignatureSize();
        final long vpt = sigValObj.getTotalSigCount();
        final long rbs = getCryptoRBS(cryptoCreate, cryptoCreateSize) + calculateRBS(txBody); // TxRecord
        final var rbsNetwork = getDefaultRBHNetworkSize() + BASIC_ENTITY_ID_SIZE * (RECEIPT_STORAGE_TIME_SEC);

        final long bpr = INT_SIZE;

        final var feeMatricesForTx = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(0L)
                .setGas(0L)
                .setTv(0L)
                .setBpr(bpr)
                .setSbpr(0L)
                .build();
        return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
    }

    /**
     * This method returns the fee matrices for crypto delete transaction
     *
     * @param txBody transaction body
     * @param sigValObj signature value object
     * @return fee data
     */
    public FeeData getCryptoDeleteTxFeeMatrices(final TransactionBody txBody, final SigValueObj sigValObj) {
        final long bpr = INT_SIZE;
        final var txBodySize = getCommonTransactionBodyBytes(txBody);
        final var bpt = txBodySize + 2 * BASIC_ENTITY_ID_SIZE + sigValObj.getSignatureSize();
        final long vpt = sigValObj.getTotalSigCount();

        final var rbs = calculateRBS(txBody);
        final var rbsNetwork = getDefaultRBHNetworkSize();
        final var feeMatricesForTx = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(vpt)
                .setRbh(rbs)
                .setSbh(0L)
                .setGas(0L)
                .setTv(0L)
                .setBpr(bpr)
                .setSbpr(0L)
                .build();

        return getFeeDataMatrices(feeMatricesForTx, sigValObj.getPayerAcctSigCount(), rbsNetwork);
    }

    /**
     * This method calculates total RAM Bytes (product of total bytes that will be stored in memory
     * and time till account expires)
     *
     * @param cryptoCreate the crypto create transaction body
     * @param crCreateSize the size of the crypto create transaction body
     * @return the total RAM Bytes for the given crypto create transaction body
     */
    private static long getCryptoRBS(final CryptoCreateTransactionBody cryptoCreate, final int crCreateSize) {
        final var seconds = cryptoCreate.hasAutoRenewPeriod()
                ? cryptoCreate.getAutoRenewPeriod().getSeconds()
                : 0L;
        return crCreateSize * seconds;
    }

    /**
     * This method returns the total bytes in a crypto create transaction body
     *
     * @param cryptoCreate the crypto create transaction body
     * @return the total bytes in the given crypto create transaction body
     */
    private static int getCryptoCreateAccountBodyTxSize(final CryptoCreateTransactionBody cryptoCreate) {
        final var keySize = getAccountKeyStorageSize(cryptoCreate.getKey());
        final var newRealmAdminKeySize =
                cryptoCreate.hasNewRealmAdminKey() ? getAccountKeyStorageSize(cryptoCreate.getNewRealmAdminKey()) : 0;

        return keySize + BASIC_ACCOUNT_SIZE + newRealmAdminKeySize;
    }

    /**
     * This method returns the fee matrices for query (for getting the cost of transaction record
     * query)
     *
     * @return fee data
     */
    public static FeeData getCostTransactionRecordQueryFeeMatrices() {
        return FeeData.getDefaultInstance();
    }

    /**
     * This method returns the fee matrices for transaction record query
     *
     * @param transRecord transaction record
     * @param responseType response type
     * @return fee data
     */
    public FeeData getTransactionRecordQueryFeeMatrices(
            final TransactionRecord transRecord, final ResponseType responseType) {
        if (transRecord == null) {
            return FeeData.getDefaultInstance();
        }
        final var bpt = BASIC_QUERY_HEADER + BASIC_TX_ID_SIZE;
        final var txRecordSize = getAccountTransactionRecordSize(transRecord);
        final var bpr = BASIC_QUERY_RES_HEADER + txRecordSize + getStateProofSize(responseType);

        final var feeMatrices = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(0L)
                .setRbh(0L)
                .setSbh(0L)
                .setGas(0L)
                .setTv(0L)
                .setBpr(bpr)
                .setSbpr(0L)
                .build();

        return getQueryFeeDataMatrices(feeMatrices);
    }

    /**
     * This method returns the fee matrices for account records query
     *
     * @param transRecords list of transaction records
     * @param responseType response type
     * @return fee data
     */
    public FeeData getCryptoAccountRecordsQueryFeeMatrices(
            final List<TransactionRecord> transRecords, final ResponseType responseType) {
        final var bpt = BASIC_QUERY_HEADER + BASIC_TX_ID_SIZE;

        int txRecordListsize = 0;
        if (transRecords != null) {
            for (final var transRecord : transRecords) {
                txRecordListsize += getAccountTransactionRecordSize(transRecord);
            }
        }

        final var bpr = BASIC_QUERY_RES_HEADER + txRecordListsize + getStateProofSize(responseType);

        final var feeMatrices = FeeComponents.newBuilder()
                .setBpt(bpt)
                .setVpt(0L)
                .setRbh(0L)
                .setSbh(0L)
                .setGas(0L)
                .setTv(0L)
                .setBpr(bpr)
                .setSbpr(0L)
                .build();

        return getQueryFeeDataMatrices(feeMatrices);
    }

    /**
     * This method returns the fee matrices for query (for getting the cost of account record query)
     *
     * @return fee data
     */
    public static FeeData getCostCryptoAccountRecordsQueryFeeMatrices() {
        return getCostForQueryByIDOnly();
    }

    /**
     * This method returns the fee matrices for query (for getting the cost of account info query)
     *
     * @return fee data
     */
    public static FeeData getCostCryptoAccountInfoQueryFeeMatrices() {
        return getCostForQueryByIDOnly();
    }

    private static int getAccountTransactionRecordSize(final TransactionRecord transRecord) {
        final var memoBytesSize = transRecord.getMemoBytes().size();

        final var accountAmountSize = transRecord.hasTransferList()
                ? transRecord.getTransferList().getAccountAmountsCount() * BASIC_ACCOUNT_AMT_SIZE
                : 0;

        return BASIC_TX_RECORD_SIZE + memoBytesSize + accountAmountSize;
    }
}
