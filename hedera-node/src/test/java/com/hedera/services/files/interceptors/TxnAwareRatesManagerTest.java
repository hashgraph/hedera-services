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

import static com.hedera.services.files.interceptors.TxnAwareRatesManager.INVALID_VERDICT;
import static com.hedera.services.files.interceptors.TxnAwareRatesManager.LIMIT_EXCEEDED_VERDICT;
import static com.hedera.services.files.interceptors.TxnAwareRatesManager.YES_VERDICT;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.mock;
import static org.mockito.BDDMockito.never;
import static org.mockito.BDDMockito.verify;

import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.files.HFileMeta;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.state.submerkle.ExchangeRates;
import com.hedera.services.utils.accessors.PlatformTxnAccessor;
import com.hedera.services.utils.accessors.SignedTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Transaction;
import java.time.Instant;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TxnAwareRatesManagerTest {
    private HFileMeta attr;
    byte[] invalidBytes = "Definitely not an ExchangeRateSet".getBytes();
    ExchangeRate.Builder someRate =
            ExchangeRate.newBuilder().setHbarEquiv(30_000).setCentEquiv(120_000);
    ExchangeRateSet validRatesObj =
            ExchangeRateSet.newBuilder().setCurrentRate(someRate).setNextRate(someRate).build();
    byte[] validRates = validRatesObj.toByteArray();

    FileID exchangeRates = asFile("0.0.112");
    FileID otherFile = asFile("0.0.912");
    AccountID civilian = asAccount("0.0.13257");
    AccountID treasury = IdUtils.asAccount("0.0.2");
    AccountID master = IdUtils.asAccount("0.0.50");
    AccountID ratesAdmin = IdUtils.asAccount("0.0.57");

    int actualLimit = 3;

    FileNumbers fileNums;
    GlobalDynamicProperties properties;
    TransactionContext txnCtx;
    ExchangeRates midnightRates;
    Consumer<ExchangeRateSet> postUpdateCb;
    IntFunction<BiPredicate<ExchangeRates, ExchangeRateSet>> intradayLimitFactory;
    BiPredicate<ExchangeRates, ExchangeRateSet> intradayLimit;

    TxnAwareRatesManager subject;

    @BeforeEach
    void setup() {
        attr = new HFileMeta(false, new JContractIDKey(1, 2, 3), Instant.now().getEpochSecond());

        PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
        given(accessor.getSignedTxnWrapper()).willReturn(Transaction.getDefaultInstance());
        txnCtx = mock(TransactionContext.class);
        given(txnCtx.swirldsTxnAccessor()).willReturn(accessor);
        midnightRates = mock(ExchangeRates.class);
        postUpdateCb = mock(Consumer.class);

        intradayLimit = mock(BiPredicate.class);
        intradayLimitFactory = mock(IntFunction.class);
        given(intradayLimitFactory.apply(actualLimit)).willReturn(intradayLimit);

        properties = mock(GlobalDynamicProperties.class);
        given(properties.ratesIntradayChangeLimitPercent()).willReturn(actualLimit);

        subject =
                new TxnAwareRatesManager(
                        new MockFileNumbers(),
                        new MockAccountNumbers(),
                        properties,
                        txnCtx,
                        () -> midnightRates,
                        postUpdateCb,
                        intradayLimitFactory);
    }

    @Test
    void hasExpectedRelevanceAndPriority() {
        // expect:
        assertTrue(subject.priorityForCandidate(otherFile).isEmpty());
        // and:
        assertEquals(0, subject.priorityForCandidate(exchangeRates).getAsInt());
    }

    @Test
    void rubberstampsPreDelete() {
        // expect:
        assertEquals(YES_VERDICT, subject.preDelete(exchangeRates));
    }

    @Test
    void rubberstampsPreChange() {
        // expect:
        assertEquals(YES_VERDICT, subject.preAttrChange(exchangeRates, attr));
    }

    @Test
    void ingnoresPostUpdateForIrrelevantFile() {
        // when:
        subject.postUpdate(otherFile, validRates);

        // then:
        verify(postUpdateCb, never()).accept(any());
    }

    @Test
    void ingnoresPostUpdateForSomehowInvalidBytes() {
        final var accessor = mock(SignedTxnAccessor.class);
        given(txnCtx.accessor()).willReturn(accessor);
        // when:
        subject.postUpdate(exchangeRates, invalidBytes);

        // then:
        verify(postUpdateCb, never()).accept(any());
    }

    @Test
    void invokesCbPostUpdateOnlyIfNotMaster() {
        givenPayer(treasury);

        // when:
        subject.postUpdate(exchangeRates, validRates);

        // then:
        verify(postUpdateCb).accept(validRatesObj);
        verify(midnightRates, never()).replaceWith(any());
    }

    @Test
    void updatesMidnightRatesIfMasterUpdate() {
        givenPayer(master);

        // when:
        subject.postUpdate(exchangeRates, validRates);

        // then:
        verify(postUpdateCb).accept(validRatesObj);
        verify(midnightRates).replaceWith(validRatesObj);
    }

    @Test
    void allowsLargeValidChangeFromMaster() {
        givenPayer(master);
        givenLargeChange();

        // when:
        var verdict = subject.preUpdate(exchangeRates, validRates);

        // then:
        assertEquals(YES_VERDICT, verdict);
    }

    @Test
    void allowsLargeValidChangeFromTreasury() {
        givenPayer(treasury);
        givenLargeChange();

        // when:
        var verdict = subject.preUpdate(exchangeRates, validRates);

        // then:
        assertEquals(YES_VERDICT, verdict);
    }

    @Test
    void rejectsLargeValidChangeFromAdmin() {
        givenPayer(ratesAdmin);
        givenLargeChange();

        // when:
        var verdict = subject.preUpdate(exchangeRates, validRates);

        // then:
        assertEquals(LIMIT_EXCEEDED_VERDICT, verdict);
        // and:
        verify(intradayLimit).test(midnightRates, validRatesObj);
    }

    @Test
    void acceptsSmallValidChangeFromAdmin() {
        givenPayer(ratesAdmin);
        givenSmallChange();

        // when:
        var verdict = subject.preUpdate(exchangeRates, validRates);

        // then:
        assertEquals(YES_VERDICT, verdict);
        // and:
        verify(intradayLimit).test(midnightRates, validRatesObj);
    }

    @Test
    void rejectsInvalidBytes() {
        givenPayer(treasury);

        // when:
        var verdict = subject.preUpdate(exchangeRates, invalidBytes);

        // then:
        assertEquals(INVALID_VERDICT, verdict);
    }

    @Test
    void rubberstampsIrrelevantFiles() {
        givenPayer(treasury);

        // when:
        var verdict = subject.preUpdate(otherFile, invalidBytes);

        // then:
        assertEquals(YES_VERDICT, verdict);
    }

    private void givenSmallChange() {
        given(intradayLimit.test(any(), any())).willReturn(true);
    }

    private void givenLargeChange() {
        given(intradayLimit.test(any(), any())).willReturn(false);
    }

    private void givenPayer(AccountID id) {
        given(txnCtx.activePayer()).willReturn(id);
    }
}
