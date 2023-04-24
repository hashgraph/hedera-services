/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.workflows.handle;

import static com.hedera.node.app.service.mono.pbj.PbjConverter.toPbj;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.ConsensusUpdateTopic;

import com.hedera.node.app.fees.MonoGetTopicInfoUsage;
import com.hedera.node.app.hapi.utils.exception.InvalidTxBodyException;
import com.hedera.node.app.hapi.utils.fee.FeeObject;
import com.hedera.node.app.service.consensus.impl.ReadableTopicStore;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.fees.FeeCalculator;
import com.hedera.node.app.service.mono.fees.HbarCentExchange;
import com.hedera.node.app.service.mono.fees.calculation.RenewAssessment;
import com.hedera.node.app.service.mono.fees.calculation.UsageBasedFeeCalculator;
import com.hedera.node.app.service.mono.fees.calculation.UsagePricesProvider;
import com.hedera.node.app.service.mono.fees.calculation.consensus.txns.UpdateTopicResourceUsage;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.utils.accessors.TxnAccessor;
import com.hedera.node.app.state.HederaState;
import com.hedera.node.app.state.WorkingStateAccessor;
import com.hedera.node.app.workflows.dispatcher.ReadableStoreFactory;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.FeeData;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.ResponseType;
import com.hederahashgraph.api.proto.java.SubType;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.swirlds.common.utility.AutoCloseableWrapper;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Instant;
import java.util.Map;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class AdaptedMonoFeeCalculator implements FeeCalculator {
    private final HbarCentExchange exchange;
    private final UsagePricesProvider usagePrices;
    private final UsageBasedFeeCalculator monoFeeCalculator;
    private final WorkingStateAccessor workingStateAccessor;
    private final UpdateTopicResourceUsage monoUpdateTopicUsage;
    private final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor;

    @Inject
    public AdaptedMonoFeeCalculator(
            @NonNull final HbarCentExchange exchange,
            @NonNull final UsagePricesProvider usagePrices,
            @NonNull final UsageBasedFeeCalculator monoFeeCalculator,
            @NonNull final WorkingStateAccessor workingStateAccessor,
            @NonNull final UpdateTopicResourceUsage monoUpdateTopicUsage,
            @NonNull final Supplier<AutoCloseableWrapper<HederaState>> stateAccessor) {
        this.exchange = exchange;
        this.usagePrices = usagePrices;
        this.monoFeeCalculator = monoFeeCalculator;
        this.workingStateAccessor = workingStateAccessor;
        this.monoUpdateTopicUsage = monoUpdateTopicUsage;
        this.stateAccessor = stateAccessor;
    }

    @Override
    public void init() {
        monoFeeCalculator.init();
    }

    @Override
    public long estimatedGasPriceInTinybars(final HederaFunctionality function, final Timestamp at) {
        return monoFeeCalculator.estimatedGasPriceInTinybars(function, at);
    }

    @Override
    public long estimatedNonFeePayerAdjustments(final TxnAccessor accessor, final Timestamp at) {
        return monoFeeCalculator.estimatedNonFeePayerAdjustments(accessor, at);
    }

    @Override
    public FeeObject computeFee(
            @NonNull final TxnAccessor accessor,
            @NonNull final JKey payerKey,
            @NonNull final StateView view,
            @NonNull final Instant now) {
        if (accessor.getFunction() == ConsensusUpdateTopic) {
            final var workingState = workingStateAccessor.getHederaState();
            return topicUpdateFeeGiven(
                    accessor, payerKey, workingState, usagePrices.activePrices(accessor), exchange.activeRate(now));
        } else {
            return monoFeeCalculator.computeFee(accessor, payerKey, view, now);
        }
    }

    @Override
    public FeeObject estimateFee(
            @NonNull final TxnAccessor accessor,
            @NonNull final JKey payerKey,
            @NonNull final StateView view,
            @NonNull final Timestamp at) {
        if (accessor.getFunction() == ConsensusUpdateTopic) {
            try (final var immutableState = stateAccessor.get()) {
                // TODO - what if this is null?
                final var workingState = immutableState.get();
                return topicUpdateFeeGiven(
                        accessor,
                        payerKey,
                        workingState,
                        monoFeeCalculator.uncheckedPricesGiven(accessor, at),
                        exchange.rate(at));
            }
        } else {
            return monoFeeCalculator.estimateFee(accessor, payerKey, view, at);
        }
    }

    @Override
    public FeeObject estimatePayment(
            @NonNull final Query query,
            @NonNull final FeeData usagePrices,
            @NonNull final StateView view,
            @NonNull final Timestamp at,
            @NonNull final ResponseType type) {
        return monoFeeCalculator.estimatePayment(query, usagePrices, view, at, type);
    }

    @Override
    public FeeObject computePayment(
            @NonNull final Query query,
            @NonNull final FeeData usagePrices,
            @NonNull final StateView view,
            @NonNull final Timestamp at,
            @NonNull final Map<String, Object> queryCtx) {
        return monoFeeCalculator.computePayment(query, usagePrices, view, at, queryCtx);
    }

    @Override
    public RenewAssessment assessCryptoAutoRenewal(
            @NonNull final HederaAccount expiredAccount,
            final long requestedRenewal,
            @NonNull final Instant now,
            @NonNull final HederaAccount payer) {
        return monoFeeCalculator.assessCryptoAutoRenewal(expiredAccount, requestedRenewal, now, payer);
    }

    private FeeObject topicUpdateFeeGiven(
            final TxnAccessor accessor,
            final JKey payerKey,
            final HederaState state,
            final Map<SubType, FeeData> prices,
            final ExchangeRate rate) {
        final var storeFactory = new ReadableStoreFactory(state);
        final var topicStore = storeFactory.createStore(ReadableTopicStore.class);
        final var topic = topicStore.getTopicLeaf(
                toPbj(accessor.getTxn().getConsensusUpdateTopic().getTopicID()));
        try {
            final var usage = monoUpdateTopicUsage.usageGivenExplicit(
                    accessor.getTxn(),
                    monoFeeCalculator.getSigUsage(accessor, payerKey),
                    topic.map(MonoGetTopicInfoUsage::monoTopicFrom).orElse(null));
            final var typedPrices = prices.get(accessor.getSubType());
            return monoFeeCalculator.feesIncludingCongestion(usage, typedPrices, accessor, rate);
        } catch (final InvalidTxBodyException e) {
            throw new IllegalStateException(e);
        }
    }
}
