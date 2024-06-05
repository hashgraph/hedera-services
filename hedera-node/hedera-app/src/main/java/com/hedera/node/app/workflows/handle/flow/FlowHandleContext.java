/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle.flow;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.base.SubType;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fees.FeeManager;
import com.hedera.node.app.hapi.utils.throttles.DeterministicThrottle;
import com.hedera.node.app.records.BlockRecordManager;
import com.hedera.node.app.signature.KeyVerifier;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import com.hedera.node.app.spi.fees.ExchangeRateInfo;
import com.hedera.node.app.spi.fees.FeeAccumulator;
import com.hedera.node.app.spi.fees.FeeCalculator;
import com.hedera.node.app.spi.fees.FeeContext;
import com.hedera.node.app.spi.fees.Fees;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.records.BlockRecordInfo;
import com.hedera.node.app.spi.records.RecordCache;
import com.hedera.node.app.spi.signatures.SignatureVerification;
import com.hedera.node.app.spi.signatures.VerificationAssistant;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.ComputeDispatchFeesAsTopLevel;
import com.hedera.node.app.spi.workflows.FunctionalityResourcePrices;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.TransactionKeys;
import com.hedera.node.app.spi.workflows.record.ExternalizedRecordCustomizer;
import com.hedera.node.app.spi.workflows.record.RecordListCheckPoint;
import com.hedera.node.app.workflows.TransactionInfo;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import javax.inject.Inject;

/**
 * The HandleContext Implementation
 */
public class FlowHandleContext implements HandleContext, FeeContext {
    private final Instant consensusNow;
    private final TransactionInfo txnInfo;
    private final Configuration configuration;
    private final Authorizer authorizer;
    private final BlockRecordManager blockRecordManager;
    private final FeeManager feeManager;
    private final ReadableStoreFactory storeFactory;
    private final AccountID syntheticPayer;
    private final KeyVerifier verifier;
    private final Key payerkey;

    @Inject
    public FlowHandleContext(
            final Instant consensusNow,
            final TransactionInfo transactionInfo,
            final Configuration configuration,
            final Authorizer authorizer,
            final BlockRecordManager blockRecordManager,
            final FeeManager feeManager,
            final ReadableStoreFactory storeFactory,
            final AccountID syntheticPayer,
            final KeyVerifier verifier,
            final Key payerkey) {
        this.consensusNow = consensusNow;
        this.txnInfo = transactionInfo;
        this.configuration = configuration;
        this.authorizer = authorizer;
        this.blockRecordManager = blockRecordManager;
        this.feeManager = feeManager;
        this.storeFactory = storeFactory;
        this.syntheticPayer = syntheticPayer;
        this.verifier = verifier;
        this.payerkey = payerkey;
    }

    @NonNull
    @Override
    public Instant consensusNow() {
        return consensusNow;
    }

    @NonNull
    @Override
    public TransactionBody body() {
        return txnInfo.txBody();
    }

    @NonNull
    @Override
    public AccountID payer() {
        return syntheticPayer;
    }

    @NonNull
    @Override
    public Configuration configuration() {
        return configuration;
    }

    @Nullable
    @Override
    public Authorizer authorizer() {
        return authorizer;
    }

    @Override
    public int numTxnSignatures() {
        return verifier.numSignaturesVerified();
    }

    @NonNull
    @Override
    public BlockRecordInfo blockRecordInfo() {
        return blockRecordManager;
    }

    @NonNull
    @Override
    public FunctionalityResourcePrices resourcePricesFor(
            @NonNull final HederaFunctionality functionality, @NonNull final SubType subType) {
        return new FunctionalityResourcePrices(
                requireNonNull(feeManager.getFeeData(functionality, consensusNow, subType)),
                feeManager.congestionMultiplierFor(txnInfo.txBody(), functionality, storeFactory));
    }

    @NonNull
    @Override
    public FeeCalculator feeCalculator(@NonNull final SubType subType) {
        return feeManager.createFeeCalculator(
                txnInfo.txBody(),
                payerkey,
                txnInfo.functionality(),
                numTxnSignatures(),
                SignatureMap.PROTOBUF.measureRecord(txnInfo.signatureMap()),
                consensusNow,
                subType,
                false,
                storeFactory);
    }

    @NonNull
    @Override
    public FeeAccumulator feeAccumulator() {
        return null;
    }

    @NonNull
    @Override
    public ExchangeRateInfo exchangeRateInfo() {
        return null;
    }

    @Override
    public long newEntityNum() {
        return 0;
    }

    @Override
    public long peekAtNewEntityNum() {
        return 0;
    }

    @NonNull
    @Override
    public AttributeValidator attributeValidator() {
        return null;
    }

    @NonNull
    @Override
    public ExpiryValidator expiryValidator() {
        return null;
    }

    @NonNull
    @Override
    public TransactionKeys allKeysForTransaction(
            @NonNull final TransactionBody nestedTxn, @NonNull final AccountID payerForNested)
            throws PreCheckException {
        return null;
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Key key) {
        return null;
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(
            @NonNull final Key key, @NonNull final VerificationAssistant callback) {
        return null;
    }

    @NonNull
    @Override
    public SignatureVerification verificationFor(@NonNull final Bytes evmAlias) {
        return null;
    }

    @Override
    public boolean isSuperUser() {
        return false;
    }

    @Override
    public SystemPrivilege hasPrivilegedAuthorization() {
        return null;
    }

    @NonNull
    @Override
    public RecordCache recordCache() {
        return null;
    }

    @NonNull
    @Override
    public <T> T readableStore(@NonNull final Class<T> storeInterface) {
        return null;
    }

    @NonNull
    @Override
    public <T> T writableStore(@NonNull final Class<T> storeInterface) {
        return null;
    }

    @NonNull
    @Override
    public <T> T serviceApi(@NonNull final Class<T> apiInterface) {
        return null;
    }

    @NonNull
    @Override
    public NetworkInfo networkInfo() {
        return null;
    }

    @NonNull
    @Override
    public <T> T recordBuilder(@NonNull final Class<T> recordBuilderClass) {
        return null;
    }

    @Override
    public Fees dispatchComputeFees(
            @NonNull final TransactionBody txBody,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final ComputeDispatchFeesAsTopLevel computeDispatchFeesAsTopLevel) {
        return null;
    }

    @NonNull
    @Override
    public <T> T dispatchPrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> verifier,
            final AccountID syntheticPayer) {
        return null;
    }

    @NonNull
    @Override
    public <T> T dispatchReversiblePrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @NonNull final Predicate<Key> verifier,
            final AccountID syntheticPayer) {
        return null;
    }

    @NonNull
    @Override
    public <T> T dispatchRemovablePrecedingTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> verifier,
            final AccountID syntheticPayer) {
        return null;
    }

    @NonNull
    @Override
    public <T> T dispatchChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final TransactionCategory childCategory) {
        return null;
    }

    @NonNull
    @Override
    public <T> T dispatchRemovableChildTransaction(
            @NonNull final TransactionBody txBody,
            @NonNull final Class<T> recordBuilderClass,
            @Nullable final Predicate<Key> callback,
            @NonNull final AccountID syntheticPayerId,
            @NonNull final ExternalizedRecordCustomizer customizer) {
        return null;
    }

    @NonNull
    @Override
    public <T> T addChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        return null;
    }

    @NonNull
    @Override
    public <T> T addPrecedingChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        return null;
    }

    @NonNull
    @Override
    public <T> T addRemovableChildRecordBuilder(@NonNull final Class<T> recordBuilderClass) {
        return null;
    }

    @NonNull
    @Override
    public SavepointStack savepointStack() {
        return null;
    }

    @Override
    public void revertRecordsFrom(@NonNull final RecordListCheckPoint recordListCheckPoint) {}

    @Override
    public boolean shouldThrottleNOfUnscaled(final int n, final HederaFunctionality function) {
        return false;
    }

    @Override
    public boolean hasThrottleCapacityForChildTransactions() {
        return false;
    }

    @NonNull
    @Override
    public RecordListCheckPoint createRecordListCheckPoint() {
        return null;
    }

    @Override
    public List<DeterministicThrottle.UsageSnapshot> getUsageSnapshots() {
        return null;
    }

    @Override
    public void resetUsageThrottlesTo(final List<DeterministicThrottle.UsageSnapshot> snapshots) {}

    @Override
    public boolean isSelfSubmitted() {
        return false;
    }

    @Nullable
    @Override
    public Instant freezeTime() {
        return null;
    }

    @NonNull
    @Override
    public Map<AccountID, Long> dispatchPaidRewards() {
        return null;
    }
}
