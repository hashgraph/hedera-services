package com.hedera.services.files.interceptors;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2020 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import com.hedera.services.config.FileNumbers;
import com.hedera.services.config.MockAccountNumbers;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.context.properties.PropertySource;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ExchangeRate;
import com.hederahashgraph.api.proto.java.ExchangeRateSet;
import com.hederahashgraph.api.proto.java.FileID;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hedera.services.legacy.core.jproto.JContractIDKey;
import com.hedera.services.legacy.core.jproto.JFileInfo;
import com.hedera.services.state.submerkle.ExchangeRates;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.IntFunction;

import static com.hedera.services.files.interceptors.TxnAwareRatesManager.*;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asFile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.*;

class TxnAwareRatesManagerTest {
	private JFileInfo attr;
	byte[] invalidBytes = "Definitely not an ExchangeRateSet".getBytes();
	ExchangeRate.Builder someRate = ExchangeRate.newBuilder()
			.setHbarEquiv(30_000)
			.setCentEquiv(120_000);
	ExchangeRateSet validRatesObj = ExchangeRateSet.newBuilder()
			.setCurrentRate(someRate)
			.setNextRate(someRate)
			.build();
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
	private void setup() {
		attr = new JFileInfo(
				false,
				new JContractIDKey(1, 2, 3),
				Instant.now().getEpochSecond());

		PlatformTxnAccessor accessor = mock(PlatformTxnAccessor.class);
		given(accessor.getSignedTxn4Log()).willReturn(Transaction.getDefaultInstance());
		txnCtx = mock(TransactionContext.class);
		given(txnCtx.accessor()).willReturn(accessor);
		midnightRates = mock(ExchangeRates.class);
		postUpdateCb = mock(Consumer.class);

		intradayLimit = mock(BiPredicate.class);
		intradayLimitFactory = mock(IntFunction.class);
		given(intradayLimitFactory.apply(actualLimit)).willReturn(intradayLimit);

		properties = mock(GlobalDynamicProperties.class);
		given(properties.ratesIntradayChangeLimitPercent()).willReturn(actualLimit);

		subject = new TxnAwareRatesManager(
				new MockFileNumbers(),
				new MockAccountNumbers(),
				properties,
				txnCtx,
				() -> midnightRates,
				postUpdateCb,
				intradayLimitFactory);
	}

	@Test
	public void hasExpectedRelevanceAndPriority() {
		// expect:
		assertTrue(subject.priorityForCandidate(otherFile).isEmpty());
		// and:
		assertEquals(0, subject.priorityForCandidate(exchangeRates).getAsInt());
	}

	@Test
	public void rubberstampsPreDelete() {
		// expect:
		assertEquals(YES_VERDICT, subject.preDelete(exchangeRates));
	}

	@Test
	public void rubberstampsPreChange() {
		// expect:
		assertEquals(YES_VERDICT, subject.preAttrChange(exchangeRates, attr));
	}

	@Test
	public void ingnoresPostUpdateForIrrelevantFile() {
		// when:
		subject.postUpdate(otherFile, validRates);

		// then:
		verify(postUpdateCb, never()).accept(any());
	}

	@Test
	public void ingnoresPostUpdateForSomehowInvalidBytes() {
		// when:
		subject.postUpdate(exchangeRates, invalidBytes);

		// then:
		verify(postUpdateCb, never()).accept(any());
	}

	@Test
	public void invokesCbPostUpdateOnlyIfNotMaster() {
		givenPayer(treasury);

		// when:
		subject.postUpdate(exchangeRates, validRates);

		// then:
		verify(postUpdateCb).accept(validRatesObj);
		verify(midnightRates, never()).replaceWith(any());
	}

	@Test
	public void updatesMidnightRatesIfMasterUpdate() {
		givenPayer(master);

		// when:
		subject.postUpdate(exchangeRates, validRates);

		// then:
		verify(postUpdateCb).accept(validRatesObj);
		verify(midnightRates).replaceWith(validRatesObj);
	}

	@Test
	public void allowsLargeValidChangeFromMaster() {
		givenPayer(master);
		givenLargeChange();

		// when:
		var verdict = subject.preUpdate(exchangeRates, validRates);

		// then:
		assertEquals(YES_VERDICT, verdict);
	}

	@Test
	public void allowsLargeValidChangeFromTreasury() {
		givenPayer(treasury);
		givenLargeChange();

		// when:
		var verdict = subject.preUpdate(exchangeRates, validRates);

		// then:
		assertEquals(YES_VERDICT, verdict);
	}

	@Test
	public void rejectsLargeValidChangeFromAdmin() {
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
	public void acceptsSmallValidChangeFromAdmin() {
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
	public void rejectsInvalidBytes() {
		givenPayer(treasury);

		// when:
		var verdict = subject.preUpdate(exchangeRates, invalidBytes);

		// then:
		assertEquals(INVALID_VERDICT, verdict);
	}

	@Test
	public void rubberstampsIrrelevantFiles() {
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
