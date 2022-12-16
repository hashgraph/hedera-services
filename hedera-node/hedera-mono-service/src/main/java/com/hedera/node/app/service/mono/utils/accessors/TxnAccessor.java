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
package com.hedera.node.app.service.mono.utils.accessors;

import com.hedera.node.app.hapi.fees.usage.BaseTransactionMeta;
import com.hedera.node.app.hapi.fees.usage.SigUsage;
import com.hedera.node.app.hapi.fees.usage.consensus.SubmitMessageMeta;
import com.hedera.node.app.hapi.fees.usage.crypto.CryptoTransferMeta;
import com.hedera.node.app.hapi.utils.ethereum.EthTxData;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.ledger.accounts.AliasManager;
import com.hedera.node.app.service.mono.txns.span.ExpandHandleSpanMapAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.ScheduleID;
import com.hederahashgraph.api.proto.java.SignatureMap;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import java.util.Map;

/**
 * Defines a type that gives access to several commonly referenced parts of a Hedera Services gRPC
 * {@link Transaction}.
 */
public interface TxnAccessor {
    // --- Used to calculate and charge fee for any transaction ---
    long getOfferedFee();

    SubType getSubType();

    AccountID getPayer();

    TransactionID getTxnId();

    HederaFunctionality getFunction();

    BaseTransactionMeta baseUsageMeta();

    SigUsage usageGiven(int numPayerKeys);

    // --- Used to process and validate any transaction ---
    byte[] getMemoUtf8Bytes();

    boolean memoHasZeroByte();

    boolean canTriggerTxn();

    boolean isTriggeredTxn();

    /**
     * @return true if the transaction should be affected by throttles
     */
    boolean throttleExempt();

    void markThrottleExempt();

    /**
     * @return true if the transaction should not be charged congestion pricing.
     */
    boolean congestionExempt();

    void markCongestionExempt();

    TransactionBody getTxn();

    long getGasLimitForContractTx();

    // --- Used to construct the record for any transaction ---
    String getMemo();

    byte[] getHash();

    ScheduleID getScheduleRef();

    void setScheduleRef(ScheduleID parent);

    // --- Used to track the results of creating signatures for all linked keys ---
    void setExpandedSigStatus(ResponseCodeEnum status);

    ResponseCodeEnum getExpandedSigStatus();

    // --- Used to log failures for any transaction ---
    String toLoggableString();

    void setPayer(AccountID payer);

    // --- Used universally for transaction submission/validation
    byte[] getSignedTxnWrapperBytes();

    boolean hasConsequentialUnknownFields();

    // --- Used universally for logging ---
    Transaction getSignedTxnWrapper();

    byte[] getTxnBytes();

    // --- Used only by specific transactions and will be moved to Custom accessors in future PR ---

    // Used only for CryptoTransfer
    CryptoTransferMeta availXferUsageMeta();

    void setNumImplicitCreations(int numImplicitCreations);

    int getNumImplicitCreations();

    boolean areImplicitCreationsCounted();

    void countImplicitCreationsWith(final AliasManager aliasManager);

    // Used only for EthereumTransaction
    EthTxData opEthTxData();

    // Used only for SubmitMessage
    SubmitMessageMeta availSubmitUsageMeta();

    // Used only for TokenMint
    boolean mintsWithMetadata();

    // Used only for ScheduleCreate/Sign, to find valid signatures that apply to a scheduled
    // transaction
    SignatureMap getSigMap();

    // ---- These will be removed by using the fields in custom accessors in future PR ---

    /**
     * Used in {@code handleTransaction} to reset this accessor's span map to new,
     * <b>unmodifiable</b> map with the authoritative results of expanding from the working state.
     * This protects the authoritative values from contamination by a pre-fetch thread.
     */
    void setRationalizedSpanMap(Map<String, Object> newSpanMap);

    Map<String, Object> getSpanMap();

    ExpandHandleSpanMapAccessor getSpanMapAccessor();

    /* --- Used for delegating precheck to custom accessors --- */
    default boolean supportsPrecheck() {
        return false;
    }

    default ResponseCodeEnum doPrecheck() {
        throw new UnsupportedOperationException();
    }

    void setStateView(StateView view);

    StateView getStateView();
}
