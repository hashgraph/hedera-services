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
package com.hedera.services.utils.accessors;

import static com.hedera.services.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.services.legacy.proto.utils.ByteStringUtils.unwrapUnsafelyIfPossible;
import static com.hedera.services.legacy.proto.utils.CommonUtils.noThrowSha384HashOf;
import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hedera.services.utils.MiscUtils.FUNCTION_EXTRACTOR;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.*;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_FUNGIBLE_COMMON;
import static com.hederahashgraph.api.proto.java.SubType.TOKEN_NON_FUNGIBLE_UNIQUE;

import com.google.common.base.MoreObjects;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ethereum.EthTxData;
import com.hedera.services.grpc.marshalling.AliasResolver;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.sigs.sourcing.PojoSigMapPubKeyToSigBytes;
import com.hedera.services.sigs.sourcing.PubKeyToSigBytes;
import com.hedera.services.txns.span.ExpandHandleSpanMapAccessor;
import com.hedera.services.usage.BaseTransactionMeta;
import com.hedera.services.usage.SigUsage;
import com.hedera.services.usage.consensus.SubmitMessageMeta;
import com.hedera.services.usage.crypto.*;
import com.hedera.services.usage.token.TokenOpsUsage;
import com.hedera.services.usage.token.meta.FeeScheduleUpdateMeta;
import com.hedera.services.usage.util.UtilPrngMeta;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.utils.MiscUtils;
import com.hederahashgraph.api.proto.java.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import org.apache.commons.codec.binary.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bouncycastle.util.Arrays;

/** Encapsulates access to several commonly referenced parts of a gRPC {@link Transaction}. */
public class SignedTxnAccessor implements TxnAccessor {
    private static final Logger log = LogManager.getLogger(SignedTxnAccessor.class);

    private static final int UNKNOWN_NUM_AUTO_CREATIONS = -1;
    private static final String ACCESSOR_LITERAL = " accessor";

    private static final TokenOpsUsage TOKEN_OPS_USAGE = new TokenOpsUsage();
    private static final ExpandHandleSpanMapAccessor SPAN_MAP_ACCESSOR =
            new ExpandHandleSpanMapAccessor();

    private Map<String, Object> spanMap = new HashMap<>();

    private int sigMapSize;
    private int numSigPairs;
    private int numAutoCreations = UNKNOWN_NUM_AUTO_CREATIONS;
    private byte[] hash;
    private byte[] txnBytes;
    private byte[] utf8MemoBytes;
    private byte[] signedTxnWrapperBytes;
    private String memo;
    private boolean memoHasZeroByte;
    private Transaction signedTxnWrapper;
    private SignatureMap sigMap;
    private TransactionID txnId;
    private TransactionBody txn;
    private SubmitMessageMeta submitMessageMeta;
    private CryptoTransferMeta xferUsageMeta;
    private BaseTransactionMeta txnUsageMeta;
    private HederaFunctionality function;
    private ResponseCodeEnum expandedSigStatus;
    private PubKeyToSigBytes pubKeyToSigBytes;
    private boolean throttleExempt;
    private boolean congestionExempt;

    private AccountID payer;
    private ScheduleID scheduleRef;
    private StateView view;

    public static SignedTxnAccessor uncheckedFrom(Transaction validSignedTxn) {
        try {
            return SignedTxnAccessor.from(validSignedTxn.toByteArray());
        } catch (Exception illegal) {
            log.warn("Unexpected use of factory with invalid gRPC transaction", illegal);
            throw new IllegalArgumentException(
                    "Argument 'validSignedTxn' must be a valid signed txn");
        }
    }

    public static SignedTxnAccessor from(byte[] signedTxnWrapperBytes)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, null);
    }

    public static SignedTxnAccessor from(
            byte[] signedTxnWrapperBytes, final Transaction signedTxnWrapper)
            throws InvalidProtocolBufferException {
        return new SignedTxnAccessor(signedTxnWrapperBytes, signedTxnWrapper);
    }

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

    @Override
    public EthTxData opEthTxData() {
        final var hapiTx = txn.getEthereumTransaction();
        return EthTxData.populateEthTxData(unwrapUnsafelyIfPossible(hapiTx.getEthereumData()));
    }

    @Override
    public void countAutoCreationsWith(final AliasManager aliasManager) {
        final var resolver = new AliasResolver();
        resolver.resolve(txn.getCryptoTransfer(), aliasManager);
        numAutoCreations = resolver.perceivedAutoCreations() + resolver.perceivedLazyCreations();
    }

    @Override
    public void setNumAutoCreations(final int numAutoCreations) {
        this.numAutoCreations = numAutoCreations;
    }

    @Override
    public int getNumAutoCreations() {
        return numAutoCreations;
    }

    @Override
    public boolean areAutoCreationsCounted() {
        return numAutoCreations != UNKNOWN_NUM_AUTO_CREATIONS;
    }

    @Override
    public SignatureMap getSigMap() {
        return sigMap;
    }

    @Override
    public HederaFunctionality getFunction() {
        if (function == null) {
            function = FUNCTION_EXTRACTOR.apply(getTxn());
        }
        return function;
    }

    @Override
    public long getOfferedFee() {
        return txn.getTransactionFee();
    }

    @Override
    public byte[] getTxnBytes() {
        return txnBytes;
    }

    @Override
    public void setExpandedSigStatus(final ResponseCodeEnum status) {
        this.expandedSigStatus = status;
    }

    @Override
    public ResponseCodeEnum getExpandedSigStatus() {
        return expandedSigStatus;
    }

    public PubKeyToSigBytes getPkToSigsFn() {
        return pubKeyToSigBytes;
    }

    @Override
    public Transaction getSignedTxnWrapper() {
        return signedTxnWrapper;
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
    public AccountID getPayer() {
        return payer;
    }

    @Override
    public byte[] getSignedTxnWrapperBytes() {
        return signedTxnWrapperBytes;
    }

    @Override
    public byte[] getMemoUtf8Bytes() {
        return utf8MemoBytes;
    }

    @Override
    public String getMemo() {
        return memo;
    }

    @Override
    public byte[] getHash() {
        return hash;
    }

    @Override
    public boolean canTriggerTxn() {
        return getTxn().hasScheduleCreate() || getTxn().hasScheduleSign();
    }

    @Override
    public boolean memoHasZeroByte() {
        return memoHasZeroByte;
    }

    @Override
    public boolean isTriggeredTxn() {
        return scheduleRef != null;
    }

    @Override
    public boolean throttleExempt() {
        if (throttleExempt) {
            return true;
        }
        var p = getPayer();
        if (p != null) {
            return STATIC_PROPERTIES.isThrottleExempt(p.getAccountNum());
        }
        return false;
    }

    public void markThrottleExempt() {
        this.throttleExempt = true;
    }

    @Override
    public boolean congestionExempt() {
        return congestionExempt;
    }

    public void markCongestionExempt() {
        this.congestionExempt = true;
    }

    @Override
    public ScheduleID getScheduleRef() {
        return scheduleRef;
    }

    @Override
    public void setScheduleRef(final ScheduleID scheduleRef) {
        this.scheduleRef = scheduleRef;
    }

    @Override
    public String toLoggableString() {
        return MoreObjects.toStringHelper(this)
                .add("sigMapSize", sigMapSize)
                .add("numSigPairs", numSigPairs)
                .add("numAutoCreations", numAutoCreations)
                .add("hash", hash)
                .add("txnBytes", txnBytes)
                .add("utf8MemoBytes", utf8MemoBytes)
                .add("memo", memo)
                .add("memoHasZeroByte", memoHasZeroByte)
                .add("signedTxnWrapper", signedTxnWrapper)
                .add("hash", hash)
                .add("txnBytes", txnBytes)
                .add("sigMap", sigMap)
                .add("txnId", txnId)
                .add("txn", txn)
                .add("submitMessageMeta", submitMessageMeta)
                .add("xferUsageMeta", xferUsageMeta)
                .add("txnUsageMeta", txnUsageMeta)
                .add("function", function)
                .add("pubKeyToSigBytes", pubKeyToSigBytes)
                .add("payer", payer)
                .add("scheduleRef", scheduleRef)
                .add("view", view)
                .toString();
    }

    @Override
    public void setPayer(final AccountID payer) {
        this.payer = payer;
    }

    @Override
    public BaseTransactionMeta baseUsageMeta() {
        return txnUsageMeta;
    }

    @Override
    public SigUsage usageGiven(final int numPayerKeys) {
        return new SigUsage(numSigPairs, sigMapSize, numPayerKeys);
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
    public SubmitMessageMeta availSubmitUsageMeta() {
        if (function != ConsensusSubmitMessage) {
            throw new IllegalStateException(
                    "Cannot get ConsensusSubmitMessage metadata for a "
                            + function
                            + ACCESSOR_LITERAL);
        }
        return submitMessageMeta;
    }

    @Override
    public Map<String, Object> getSpanMap() {
        return spanMap;
    }

    /** {@inheritDoc} */
    @Override
    public void setRationalizedSpanMap(final Map<String, Object> newSpanMap) {
        spanMap = Collections.unmodifiableMap(newSpanMap);
    }

    @Override
    public ExpandHandleSpanMapAccessor getSpanMapAccessor() {
        return SPAN_MAP_ACCESSOR;
    }

    @Override
    public long getGasLimitForContractTx() {
        return MiscUtils.getGasLimitForContractTx(
                getTxn(), getFunction(), () -> getSpanMapAccessor().getEthTxDataMeta(this));
    }

    @Override
    public void setStateView(final StateView view) {
        this.view = view;
    }

    protected EntityNum lookUpAlias(ByteString alias) {
        return view.aliases().get(alias);
    }

    protected EntityNum unaliased(final AccountID idOrAlias) {
        if (isAlias(idOrAlias)) {
            return lookUpAlias(idOrAlias.getAlias());
        }
        return EntityNum.fromAccountId(idOrAlias);
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

    /* This section should be deleted after custom accessors are complete */
    private void setOpUsageMeta() {
        if (function == CryptoTransfer) {
            setXferUsageMeta();
        } else if (function == ConsensusSubmitMessage) {
            setSubmitUsageMeta();
        } else if (function == TokenFeeScheduleUpdate) {
            setFeeScheduleUpdateMeta();
        } else if (function == TokenCreate) {
            setTokenCreateUsageMeta();
        } else if (function == TokenBurn) {
            setTokenBurnUsageMeta();
        } else if (function == TokenFreezeAccount) {
            setTokenFreezeUsageMeta();
        } else if (function == TokenUnfreezeAccount) {
            setTokenUnfreezeUsageMeta();
        } else if (function == TokenPause) {
            setTokenPauseUsageMeta();
        } else if (function == TokenUnpause) {
            setTokenUnpauseUsageMeta();
        } else if (function == CryptoCreate) {
            setCryptoCreateUsageMeta();
        } else if (function == CryptoUpdate) {
            setCryptoUpdateUsageMeta();
        } else if (function == CryptoApproveAllowance) {
            setCryptoApproveUsageMeta();
        } else if (function == CryptoDeleteAllowance) {
            setCryptoDeleteAllowanceUsageMeta();
        } else if (function == EthereumTransaction) {
            setEthTxDataMeta();
        } else if (function == UtilPrng) {
            setUtilPrngUsageMeta();
        }
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

    private void setTokenCreateUsageMeta() {
        final var tokenCreateMeta = TOKEN_OPS_USAGE_UTILS.tokenCreateUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenCreateMeta(this, tokenCreateMeta);
    }

    private void setTokenBurnUsageMeta() {
        final var tokenBurnMeta = TOKEN_OPS_USAGE_UTILS.tokenBurnUsageFrom(txn);
        SPAN_MAP_ACCESSOR.setTokenBurnMeta(this, tokenBurnMeta);
    }

    private void setTokenFreezeUsageMeta() {
        final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenFreezeMeta(this, tokenFreezeMeta);
    }

    private void setTokenUnfreezeUsageMeta() {
        final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnfreezeMeta(this, tokenUnfreezeMeta);
    }

    private void setTokenPauseUsageMeta() {
        final var tokenPauseMeta = TOKEN_OPS_USAGE_UTILS.tokenPauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenPauseMeta(this, tokenPauseMeta);
    }

    private void setTokenUnpauseUsageMeta() {
        final var tokenUnpauseMeta = TOKEN_OPS_USAGE_UTILS.tokenUnpauseUsageFrom();
        SPAN_MAP_ACCESSOR.setTokenUnpauseMeta(this, tokenUnpauseMeta);
    }

    private void setCryptoCreateUsageMeta() {
        final var cryptoCreateMeta = new CryptoCreateMeta(txn.getCryptoCreateAccount());
        SPAN_MAP_ACCESSOR.setCryptoCreateMeta(this, cryptoCreateMeta);
    }

    private void setCryptoUpdateUsageMeta() {
        final var cryptoUpdateMeta =
                new CryptoUpdateMeta(
                        txn.getCryptoUpdateAccount(),
                        txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoUpdate(this, cryptoUpdateMeta);
    }

    private void setCryptoApproveUsageMeta() {
        final var cryptoApproveMeta =
                new CryptoApproveAllowanceMeta(
                        txn.getCryptoApproveAllowance(),
                        txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoApproveMeta(this, cryptoApproveMeta);
    }

    private void setCryptoDeleteAllowanceUsageMeta() {
        final var cryptoDeleteAllowanceMeta =
                new CryptoDeleteAllowanceMeta(
                        txn.getCryptoDeleteAllowance(),
                        txn.getTransactionID().getTransactionValidStart().getSeconds());
        SPAN_MAP_ACCESSOR.setCryptoDeleteAllowanceMeta(this, cryptoDeleteAllowanceMeta);
    }

    private void setUtilPrngUsageMeta() {
        final var utilPrngUsageMeta = new UtilPrngMeta(txn.getUtilPrng());
        SPAN_MAP_ACCESSOR.setUtilPrngMeta(this, utilPrngUsageMeta);
    }

    private void setEthTxDataMeta() {
        SPAN_MAP_ACCESSOR.setEthTxDataMeta(this, opEthTxData());
    }

    @Override
    public SubType getSubType() {
        if (function == CryptoTransfer) {
            return xferUsageMeta.getSubType();
        } else if (function == TokenCreate) {
            return SPAN_MAP_ACCESSOR.getTokenCreateMeta(this).getSubType();
        } else if (function == TokenMint) {
            final var op = getTxn().getTokenMint();
            return op.getMetadataCount() > 0 ? TOKEN_NON_FUNGIBLE_UNIQUE : TOKEN_FUNGIBLE_COMMON;
        } else if (function == TokenBurn) {
            return SPAN_MAP_ACCESSOR.getTokenBurnMeta(this).getSubType();
        } else if (function == TokenAccountWipe) {
            return SPAN_MAP_ACCESSOR.getTokenWipeMeta(this).getSubType();
        }
        return SubType.DEFAULT;
    }

    @Override
    public StateView getStateView() {
        return view;
    }
}
