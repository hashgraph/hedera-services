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
package com.hedera.evm.utils.accessors;

import static com.hedera.evm.utils.MiscUtils.FUNCTION_EXTRACTOR;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusSubmitMessage;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoTransfer;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.TokenFeeScheduleUpdate;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.evm.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.evm.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.evm.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.evm.usage.BaseTransactionMeta;
import com.hedera.evm.usage.consensus.SubmitMessageMeta;
import com.hedera.evm.usage.token.TokenOpsUsage;
import com.hedera.evm.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.evm.utils.accessors.crypto.CryptoTransferMeta;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SignedTransaction;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.bouncycastle.util.Arrays;

public class SignedTxnAccessor implements TxnAccessor {
    private static final String ACCESSOR_LITERAL = " accessor";
    private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
    private static final ExpandHandleSpanMapAccessor SPAN_MAP_ACCESSOR =
            new ExpandHandleSpanMapAccessor();
    private Map<String, Object> spanMap = new HashMap<>();
    private HederaFunctionality function;
    private CryptoTransferMeta xferUsageMeta;
    private TransactionID txnId;
    private TransactionBody txn;
    private Transaction signedTxnWrapper;
    private int sigMapSize;
    private byte[] signedTxnWrapperBytes;
    private byte[] txnBytes;
    private SignatureMap sigMap;
    private int numSigPairs;
    private byte[] hash;
    private String memo;
    private byte[] utf8MemoBytes;
    private boolean memoHasZeroByte;
    private AccountID payer;
    private PubKeyToSigBytes pubKeyToSigBytes;
    private BaseTransactionMeta txnUsageMeta;
    private SubmitMessageMeta submitMessageMeta;

    protected SignedTxnAccessor(
            byte[] signedTxnWrapperBytes, @Nullable final Transaction transaction)
            throws InvalidProtocolBufferException {
        this.signedTxnWrapperBytes = signedTxnWrapperBytes;

        final Transaction txnWrapper;
        if (transaction != null) {
            txnWrapper = transaction;
        } else {
            txnWrapper = Transaction.parseFrom(signedTxnWrapperBytes);
        }
        this.signedTxnWrapper = txnWrapper;

        final var signedTxnBytes = signedTxnWrapper.getSignedTransactionBytes();
        if (signedTxnBytes.isEmpty()) {
            txnBytes = unwrapUnsafelyIfPossible(signedTxnWrapper.getBodyBytes());
            sigMap = signedTxnWrapper.getSigMap();
            hash = noThrowSha384HashOf(signedTxnWrapperBytes);
        } else {
            final var signedTxn = SignedTransaction.parseFrom(signedTxnBytes);
            txnBytes = unwrapUnsafelyIfPossible(signedTxn.getBodyBytes());
            sigMap = signedTxn.getSigMap();
            hash = noThrowSha384HashOf(unwrapUnsafelyIfPossible(signedTxnBytes));
        }
        pubKeyToSigBytes = new PojoSigMapPubKeyToSigBytes(sigMap);

        txn = TransactionBody.parseFrom(txnBytes);
        memo = txn.getMemo();
        txnId = txn.getTransactionID();
        sigMapSize = sigMap.getSerializedSize();
        numSigPairs = sigMap.getSigPairCount();
        utf8MemoBytes = StringUtils.getBytesUtf8(memo);
        memoHasZeroByte = Arrays.contains(utf8MemoBytes, (byte) 0);
        payer = getTxnId().getAccountID();

        getFunction();
        setBaseUsageMeta();
        setOpUsageMeta();
    }

    public static SignedTxnAccessor from(
            byte[] signedTxnWrapperBytes, final Transaction signedTxnWrapper)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, signedTxnWrapper);
    }

    @Override
    public CryptoTransferMeta availXferUsageMeta() {
        if (function != CryptoTransfer) {
            throw new IllegalStateException(
                    "Cannot get CryptoTransfer metadata for a " + function + ACCESSOR_LITERAL);
        }
        return xferUsageMeta;
    }

    @Override
    public HederaFunctionality getFunction() {
        if (function == null) {
            function = FUNCTION_EXTRACTOR.apply(getTxn());
        }
        return function;
    }

    @Override
    public TransactionBody getTxn() {
        return txn;
    }

    @Override
    public TransactionID getTxnId() {
        return txnId;
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return signedTxnWrapper;
    }

    @Override
    public Map<String, Object> getSpanMap() {
        return spanMap;
    }

    @Override
    public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
        return SPAN_MAP_ACCESSOR;
    }

    private void setBaseUsageMeta() {
        if (function == CryptoTransfer) {
            txnUsageMeta =
                    new BaseTransactionMeta(
                            utf8MemoBytes.length,
                            txn.getCryptoTransfer().getTransfers().getAccountAmountsCount());
        } else {
            txnUsageMeta = new BaseTransactionMeta(utf8MemoBytes.length, 0);
        }
    }

    // FUTURE WORK add the other functions
    private void setOpUsageMeta() {
        if (function == CryptoTransfer) {
            setXferUsageMeta();
        } else if (function == ConsensusSubmitMessage) {
            setSubmitUsageMeta();
        } else if (function == TokenFeeScheduleUpdate) {
            setFeeScheduleUpdateMeta();
        }
        //        } else if (function == TokenCreate) {
        //            setTokenCreateUsageMeta();
        //        } else if (function == TokenBurn) {
        //            setTokenBurnUsageMeta();
        //        } else if (function == TokenFreezeAccount) {
        //            setTokenFreezeUsageMeta();
        //        } else if (function == TokenUnfreezeAccount) {
        //            setTokenUnfreezeUsageMeta();
        //        } else if (function == TokenPause) {
        //            setTokenPauseUsageMeta();
        //        } else if (function == TokenUnpause) {
        //            setTokenUnpauseUsageMeta();
        //        } else if (function == CryptoCreate) {
        //            setCryptoCreateUsageMeta();
        //        } else if (function == CryptoUpdate) {
        //            setCryptoUpdateUsageMeta();
        //        } else if (function == CryptoApproveAllowance) {
        //            setCryptoApproveUsageMeta();
        //        } else if (function == CryptoDeleteAllowance) {
        //            setCryptoDeleteAllowanceUsageMeta();
        //        } else if (function == EthereumTransaction) {
        //            setEthTxDataMeta();
        //        } else if (function == UtilPrng) {
        //            setUtilPrngUsageMeta();
        //        }
    }

    private void setXferUsageMeta() {
        var totalTokensInvolved = 0;
        var totalTokenTransfers = 0;
        var numNftOwnershipChanges = 0;
        final var op = txn.getCryptoTransfer();
        for (var tokenTransfers : op.getTokenTransfersList()) {
            totalTokensInvolved++;
            totalTokenTransfers += tokenTransfers.getTransfersCount();
            numNftOwnershipChanges += tokenTransfers.getNftTransfersCount();
        }
        xferUsageMeta =
                new CryptoTransferMeta(
                        1, totalTokensInvolved, totalTokenTransfers, numNftOwnershipChanges);
    }

    private void setSubmitUsageMeta() {
        submitMessageMeta =
                new SubmitMessageMeta(txn.getConsensusSubmitMessage().getMessage().size());
    }

    private void setFeeScheduleUpdateMeta() {
        final var effConsTime = getTxnId().getTransactionValidStart().getSeconds();
        final var op = getTxn().getTokenFeeScheduleUpdate();
        final var reprBytes = TOKEN_OPS_USAGE.bytesNeededToRepr(op.getCustomFeesList());

        final var meta = new FeeScheduleUpdateMeta(effConsTime, reprBytes);
        SPAN_MAP_ACCESSOR.setFeeScheduleUpdateMeta(this, meta);
    }
}
