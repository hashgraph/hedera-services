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
package com.hedera.test.utils;

import static com.hedera.test.factories.txns.CryptoTransferFactory.newSignedCryptoTransfer;
import static com.hedera.test.factories.txns.TinyBarsFromTo.tinyBarsFromTo;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asContract;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_CONTRACT_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.ByteString;
import com.hedera.services.exceptions.InvalidTransactionException;
import com.hedera.services.exceptions.ResourceLimitException;
import com.hedera.services.legacy.core.jproto.JKey;
import com.hedera.services.legacy.core.jproto.JKeyList;
import com.hedera.services.legacy.core.jproto.TxnReceipt;
import com.hedera.services.state.submerkle.*;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.factories.keys.KeyTree;
import com.hedera.test.factories.scenarios.TxnHandlingScenario;
import com.hederahashgraph.api.proto.java.*;
import java.time.Instant;
import java.util.List;
import java.util.Random;
import java.util.UUID;

public class TxnUtils {
    public static TransferList withAdjustments(
            AccountID a, long A, AccountID b, long B, AccountID c, long C) {
        return TransferList.newBuilder()
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
                .build();
    }

    public static CurrencyAdjustments withAdjustments(
            final long[] balanceChanges, final long[] accountCodes) {
        return new CurrencyAdjustments(balanceChanges, accountCodes);
    }

    public static TransferList withAdjustments(
            AccountID a, long A, AccountID b, long B, AccountID c, long C, AccountID d, long D) {
        return TransferList.newBuilder()
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(a).setAmount(A).build())
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(b).setAmount(B).build())
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(c).setAmount(C).build())
                .addAccountAmounts(AccountAmount.newBuilder().setAccountID(d).setAmount(D).build())
                .build();
    }

    public static TransferList withAllowanceAdjustments(
            AccountID a,
            long A,
            boolean isAllowedA,
            AccountID b,
            long B,
            boolean isAllowedB,
            AccountID c,
            long C,
            boolean isAllowedC,
            AccountID d,
            long D,
            boolean isAllowedD) {
        return TransferList.newBuilder()
                .addAccountAmounts(
                        AccountAmount.newBuilder()
                                .setAccountID(a)
                                .setAmount(A)
                                .setIsApproval(isAllowedA)
                                .build())
                .addAccountAmounts(
                        AccountAmount.newBuilder()
                                .setAccountID(b)
                                .setAmount(B)
                                .setIsApproval(isAllowedB)
                                .build())
                .addAccountAmounts(
                        AccountAmount.newBuilder()
                                .setAccountID(c)
                                .setAmount(C)
                                .setIsApproval(isAllowedC)
                                .build())
                .addAccountAmounts(
                        AccountAmount.newBuilder()
                                .setAccountID(d)
                                .setAmount(D)
                                .setIsApproval(isAllowedD)
                                .build())
                .build();
    }

    public static List<TokenTransferList> withOwnershipChanges(
            TokenID a,
            AccountID aId,
            AccountID aCounterpartyId,
            long A,
            TokenID b,
            AccountID bId,
            AccountID bCounterpartyId,
            long B,
            TokenID c,
            AccountID cId,
            AccountID cCounterpartyId,
            long C) {
        return List.of(
                TokenTransferList.newBuilder()
                        .setToken(a)
                        .addNftTransfers(IdUtils.nftXfer(aId, aCounterpartyId, A))
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(b)
                        .addNftTransfers(IdUtils.nftXfer(bId, bCounterpartyId, B))
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(c)
                        .addNftTransfers(IdUtils.nftXfer(cId, cCounterpartyId, C))
                        .build());
    }

    public static TokenTransferList withNftAdjustments(
            TokenID a,
            AccountID aSenderId,
            AccountID aReceiverId,
            Long aSerialNumber,
            AccountID bSenderId,
            AccountID bReceiverId,
            Long bSerialNumber,
            AccountID cSenderId,
            AccountID cReceiverId,
            Long cSerialNumber) {
        return TokenTransferList.newBuilder()
                .setToken(a)
                .addNftTransfers(
                        NftTransfer.newBuilder()
                                .setSenderAccountID(aSenderId)
                                .setReceiverAccountID(aReceiverId)
                                .setSerialNumber(aSerialNumber))
                .addNftTransfers(
                        NftTransfer.newBuilder()
                                .setSenderAccountID(bSenderId)
                                .setReceiverAccountID(bReceiverId)
                                .setSerialNumber(bSerialNumber))
                .addNftTransfers(
                        NftTransfer.newBuilder()
                                .setSenderAccountID(cSenderId)
                                .setReceiverAccountID(cReceiverId)
                                .setSerialNumber(cSerialNumber))
                .build();
    }

    public static List<TokenTransferList> withTokenAdjustments(
            TokenID a,
            AccountID aId,
            long A,
            TokenID b,
            AccountID bId,
            long B,
            TokenID c,
            AccountID cId,
            long C) {
        return List.of(
                TokenTransferList.newBuilder()
                        .setToken(a)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(b)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(c)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
                        .build());
    }

    public static List<TokenTransferList> withTokenAdjustments(
            TokenID a,
            AccountID aId,
            long A,
            TokenID b,
            AccountID bId,
            long B,
            TokenID c,
            AccountID cId,
            long C,
            TokenID d,
            AccountID dId,
            long D) {
        return List.of(
                TokenTransferList.newBuilder()
                        .setToken(a)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(b)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(bId).setAmount(B).build())
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(aId).setAmount(A).build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(c)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(cId).setAmount(C).build())
                        .build(),
                TokenTransferList.newBuilder()
                        .setToken(d)
                        .addTransfers(
                                AccountAmount.newBuilder().setAccountID(dId).setAmount(D).build())
                        .build());
    }

    public static List<TokenTransferList> withTokenAdjustments(TokenID a, TokenID b) {
        return List.of(
                TokenTransferList.newBuilder().setToken(a).build(),
                TokenTransferList.newBuilder().setToken(b).build());
    }

    public static Transaction payerSponsoredTransfer(
            String payer, KeyTree payerKey, String beneficiary, long amount) throws Throwable {
        return newSignedCryptoTransfer()
                .payer(payer)
                .payerKt(payerKey)
                .transfers(tinyBarsFromTo(payer, beneficiary, amount))
                .get();
    }

    public static byte[] randomUtf8Bytes(int n) {
        byte[] data = new byte[n];
        int i = 0;
        while (i < n) {
            byte[] rnd = UUID.randomUUID().toString().getBytes();
            System.arraycopy(rnd, 0, data, i, Math.min(rnd.length, n - 1 - i));
            i += rnd.length;
        }
        return data;
    }

    public static String random384BitBinaryText() {
        Random rand = new Random();
        // Use to collect result
        String result = "";
        for (int i = 0; i < 384; ++i) {
            // Collect the random number
            result = (Math.abs(rand.nextInt() % 2)) + result;
        }
        return result;
    }

    public static ByteString randomUtf8ByteString(int n) {
        return ByteString.copyFrom(randomUtf8Bytes(n));
    }

    public static Timestamp timestampFrom(long secs, int nanos) {
        return Timestamp.newBuilder().setSeconds(secs).setNanos(nanos).build();
    }

    public static Key.Builder nestKeys(Key.Builder builder, int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            builder.setEd25519(TxnHandlingScenario.TOKEN_ADMIN_KT.asKey().getEd25519());
            return builder;
        } else {
            var nestedBuilder = Key.newBuilder();
            nestKeys(nestedBuilder, additionalKeysToNest - 1);
            builder.setKeyList(KeyList.newBuilder().addKeys(nestedBuilder));
            return builder;
        }
    }

    public static JKey nestJKeys(int additionalKeysToNest) {
        if (additionalKeysToNest == 0) {
            return TxnHandlingScenario.SIMPLE_NEW_ADMIN_KT.asJKeyUnchecked();
        } else {
            final var descendantKeys = nestJKeys(additionalKeysToNest - 1);
            return new JKeyList(List.of(descendantKeys));
        }
    }

    public static void assertFailsWith(final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    public static void assertExhaustsResourceLimit(
            final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(ResourceLimitException.class, something::run);
        assertEquals(status, ex.getResponseCode());
    }

    public static void assertFailsRevertingWith(
            final Runnable something, final ResponseCodeEnum status) {
        final var ex = assertThrows(InvalidTransactionException.class, something::run);
        assertEquals(status, ex.getResponseCode());
        assertTrue(ex.isReverting());
    }

    public static ExpirableTxnRecord recordOne() {
        return ExpirableTxnRecord.newBuilder()
                .setReceipt(
                        TxnReceipt.newBuilder()
                                .setStatus(INVALID_ACCOUNT_ID.name())
                                .setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.3")))
                                .build())
                .setTxnId(
                        TxnId.fromGrpc(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(9_999_999_999L))
                                        .build()))
                .setMemo("Alpha bravo charlie")
                .setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(9_999_999_999L)))
                .setFee(555L)
                .setHbarAdjustments(
                        CurrencyAdjustments.fromChanges(
                                new long[] {-4L, 2L, 2L}, new long[] {2L, 1001L, 1002L}))
                .setContractCallResult(
                        SerdeUtils.fromGrpc(
                                ContractFunctionResult.newBuilder()
                                        .setContractID(asContract("1.2.3"))
                                        .setErrorMessage("Couldn't figure it out!")
                                        .setGasUsed(55L)
                                        .addLogInfo(
                                                ContractLoginfo.newBuilder()
                                                        .setData(
                                                                ByteString.copyFrom(
                                                                        "Nonsense!".getBytes())))
                                        .build()))
                .build();
    }

    public static ExpirableTxnRecord recordTwo() {
        return ExpirableTxnRecord.newBuilder()
                .setReceipt(
                        TxnReceipt.newBuilder()
                                .setStatus(INVALID_CONTRACT_ID.name())
                                .setAccountId(EntityId.fromGrpcAccountId(asAccount("0.0.4")))
                                .build())
                .setTxnId(
                        TxnId.fromGrpc(
                                TransactionID.newBuilder()
                                        .setTransactionValidStart(
                                                Timestamp.newBuilder().setSeconds(7_777_777_777L))
                                        .build()))
                .setMemo("Alpha bravo charlie")
                .setConsensusTime(RichInstant.fromJava(Instant.ofEpochSecond(7_777_777_777L)))
                .setFee(556L)
                .setHbarAdjustments(
                        CurrencyAdjustments.fromChanges(
                                new long[] {-6L, 3L, 3L}, new long[] {2L, 1001L, 1002L}))
                .setContractCallResult(
                        SerdeUtils.fromGrpc(
                                ContractFunctionResult.newBuilder()
                                        .setContractID(asContract("4.3.2"))
                                        .setErrorMessage("Couldn't figure it out immediately!")
                                        .setGasUsed(55L)
                                        .addLogInfo(
                                                ContractLoginfo.newBuilder()
                                                        .setData(
                                                                ByteString.copyFrom(
                                                                        "Nonsensical!".getBytes())))
                                        .setGas(1_000_000L)
                                        .setFunctionParameters(
                                                ByteString.copyFrom("Sensible!".getBytes()))
                                        .build()))
                .build();
    }

    public static TokenTransferList ttlOf(
            TokenID scope, AccountID src, AccountID dest, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addTransfers(aaOf(src, -amount))
                .addTransfers(aaOf(dest, +amount))
                .build();
    }

    public static TokenTransferList exchangeOf(
            TokenID scope, AccountID src, AccountID dest, long serial) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addNftTransfers(serialFromTo(serial, src, dest))
                .build();
    }

    public static TokenTransferList asymmetricTtlOf(TokenID scope, AccountID src, long amount) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addTransfers(aaOf(src, -amount))
                .build();
    }

    public static TokenTransferList burnExchangeOf(TokenID scope, AccountID src, long serialNo) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addNftTransfers(
                        serialFromTo(serialNo, src, EntityNum.fromLong(0).toGrpcAccountId()))
                .build();
    }

    public static TokenTransferList returnExchangeOf(
            TokenID scope, AccountID src, AccountID dst, long serialNo) {
        return TokenTransferList.newBuilder()
                .setToken(scope)
                .addNftTransfers(serialFromTo(serialNo, src, dst))
                .build();
    }

    public static AccountAmount aaOf(AccountID id, long amount) {
        return AccountAmount.newBuilder().setAccountID(id).setAmount(amount).build();
    }

    public static NftTransfer serialFromTo(
            final long num, final AccountID sender, final AccountID receiver) {
        return NftTransfer.newBuilder()
                .setSerialNumber(num)
                .setSenderAccountID(sender)
                .setReceiverAccountID(receiver)
                .build();
    }

    public static List<CurrencyAdjustments> adjustmentsFrom(final List<TokenTransferList> ttls) {
        return ttls.stream()
                .map(
                        ttl ->
                                new CurrencyAdjustments(
                                        ttl.getTransfersList().stream()
                                                .mapToLong(AccountAmount::getAmount)
                                                .toArray(),
                                        ttl.getTransfersList().stream()
                                                .map(AccountAmount::getAccountID)
                                                .mapToLong(AccountID::getAccountNum)
                                                .toArray()))
                .toList();
    }

    public static List<NftAdjustments> exchangesFrom(final List<TokenTransferList> ttls) {
        return ttls.stream()
                .map(
                        ttl ->
                                new NftAdjustments(
                                        ttl.getNftTransfersList().stream()
                                                .mapToLong(NftTransfer::getSerialNumber)
                                                .toArray(),
                                        ttl.getNftTransfersList().stream()
                                                .map(
                                                        xfer ->
                                                                EntityId.fromGrpcAccountId(
                                                                        xfer.getSenderAccountID()))
                                                .toList(),
                                        ttl.getNftTransfersList().stream()
                                                .map(
                                                        xfer ->
                                                                EntityId.fromGrpcAccountId(
                                                                        xfer
                                                                                .getReceiverAccountID()))
                                                .toList()))
                .toList();
    }

    public static TransactionBody ethereumTransactionOp() {
        final var op =
                EthereumTransactionBody.newBuilder()
                        .setEthereumData(
                                ByteString.copyFrom(
                                        com.swirlds.common.utility.CommonUtils.unhex(
                                                "f864012f83018000947e3a9eaf9bcc39e2ffa38eb30bf7a93feacbc18180827653820277a0f9fbff985d374be4a55f296915002eec11ac96f1ce2df183adf992baa9390b2fa00c1e867cc960d9c74ec2e6a662b7908ec4c8cc9f3091e886bcefbeb2290fb792")))
                        .build();
        return TransactionBody.newBuilder()
                .setTransactionID(
                        TransactionID.newBuilder()
                                .setTransactionValidStart(
                                        Timestamp.newBuilder().setSeconds(1_234_567L)))
                .setEthereumTransaction(op)
                .build();
    }

    public static Transaction buildTransactionFrom(final TransactionBody transactionBody) {
        return buildTransactionFrom(signedTransactionFrom(transactionBody).toByteString());
    }

    public static Transaction buildTransactionFrom(final ByteString signedTransactionBytes) {
        return Transaction.newBuilder().setSignedTransactionBytes(signedTransactionBytes).build();
    }

    private static SignedTransaction signedTransactionFrom(final TransactionBody txnBody) {
        return signedTransactionFrom(txnBody, SignatureMap.getDefaultInstance());
    }

    public static SignedTransaction signedTransactionFrom(
            final TransactionBody txnBody, final SignatureMap sigMap) {
        return SignedTransaction.newBuilder()
                .setBodyBytes(txnBody.toByteString())
                .setSigMap(sigMap)
                .build();
    }
}
