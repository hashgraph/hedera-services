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
package com.hedera.services.files.interceptors;

import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_EXCHANGE_RATE_FILE;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.services.config.AccountNumbers;
import com.hedera.services.config.FileNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.files.FileUpdateInterceptor;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.AbstractMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Singleton
public class TxnAwareRatesManager implements FileUpdateInterceptor {
    private static final Logger log = LogManager.getLogger(TxnAwareRatesManager.class);
    private static final int APPLICABLE_PRIORITY = 0;

    static final Map.Entry<ResponseCodeEnum, Boolean> YES_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(SUCCESS, true);
    static final Map.Entry<ResponseCodeEnum, Boolean> INVALID_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(INVALID_EXCHANGE_RATE_FILE, false);
    static final Map.Entry<ResponseCodeEnum, Boolean> LIMIT_EXCEEDED_VERDICT =
            new AbstractMap.SimpleImmutableEntry<>(EXCHANGE_RATE_CHANGE_LIMIT_EXCEEDED, false);

    private final FileNumbers fileNums;
    private final AccountNumbers accountNums;
    private final TransactionContext txnCtx;
    private final GlobalDynamicProperties properties;
    private final Supplier<ExchangeRates> midnightRates;
    private final Consumer<ExchangeRateSet> postUpdateCb;
    private final IntFunction<BiPredicate<ExchangeRates, ExchangeRateSet>> intradayLimitFactory;

    @Inject
    public TxnAwareRatesManager(
            FileNumbers fileNums,
            AccountNumbers accountNums,
            GlobalDynamicProperties properties,
            TransactionContext txnCtx,
            Supplier<ExchangeRates> midnightRates,
            Consumer<ExchangeRateSet> postUpdateCb,
            IntFunction<BiPredicate<ExchangeRates, ExchangeRateSet>> intradayLimitFactory) {
        this.txnCtx = txnCtx;
        this.fileNums = fileNums;
        this.properties = properties;
        this.accountNums = accountNums;
        this.postUpdateCb = postUpdateCb;
        this.midnightRates = midnightRates;
        this.intradayLimitFactory = intradayLimitFactory;
    }

    @Override
    public OptionalInt priorityForCandidate(FileID id) {
        return isRates(id) ? OptionalInt.of(APPLICABLE_PRIORITY) : OptionalInt.empty();
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preUpdate(FileID id, byte[] newContents) {
        if (!isRates(id)) {
            return YES_VERDICT;
        }

        Optional<ExchangeRateSet> rates = uncheckedParseFrom(newContents);
        if (rates.isEmpty()) {
            return INVALID_VERDICT;
        }

        return checkBound(rates);
    }

    private Map.Entry<ResponseCodeEnum, Boolean> checkBound(Optional<ExchangeRateSet> rates) {
        var bound = properties.ratesIntradayChangeLimitPercent();
        var intradayLimit = intradayLimitFactory.apply(bound);
        if (isSudoer()
                || (rates.isPresent() && intradayLimit.test(midnightRates.get(), rates.get()))) {
            return YES_VERDICT;
        } else {
            return LIMIT_EXCEEDED_VERDICT;
        }
    }

    @Override
    public void postUpdate(FileID id, byte[] newContents) {
        if (isRates(id)) {
            uncheckedParseFrom(newContents)
                    .ifPresentOrElse(
                            rates -> {
                                postUpdateCb.accept(rates);
                                if (isSysAdmin()) {
                                    log.info("Overwriting midnight rates with {}", rates);
                                    midnightRates.get().replaceWith(rates);
                                }
                            },
                            () ->
                                    log.error(
                                            "Rates postUpdate called with invalid data by {}!",
                                            txnCtx.accessor().getSignedTxnWrapper()));
        }
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preDelete(FileID id) {
        return YES_VERDICT;
    }

    @Override
    public Map.Entry<ResponseCodeEnum, Boolean> preAttrChange(FileID id, HFileMeta newAttr) {
        return YES_VERDICT;
    }

    private boolean isRates(FileID id) {
        return id.getFileNum() == fileNums.exchangeRates();
    }

    private boolean isSudoer() {
        return isSysAdmin() || isTreasury();
    }

    private boolean isSysAdmin() {
        return txnCtx.activePayer().getAccountNum() == accountNums.systemAdmin();
    }

    private boolean isTreasury() {
        return txnCtx.activePayer().getAccountNum() == accountNums.treasury();
    }

    private Optional<ExchangeRateSet> uncheckedParseFrom(byte[] data) {
        try {
            return Optional.of(ExchangeRateSet.parseFrom(data));
        } catch (InvalidProtocolBufferException ignore) {
            return Optional.empty();
        }
    }
}
