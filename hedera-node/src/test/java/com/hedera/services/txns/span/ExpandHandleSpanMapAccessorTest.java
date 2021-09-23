package com.hedera.services.txns.span;

import static com.hedera.services.usage.token.TokenOpsUsageUtils.TOKEN_OPS_USAGE_UTILS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.BDDMockito.given;

import java.util.HashMap;
import java.util.Map;

/*-
 * ‌
 * Hedera Services Node
 *
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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
 * ‍
 */

import com.hedera.services.usage.crypto.CryptoCreateMeta;
import com.hedera.services.usage.crypto.CryptoUpdateMeta;
import com.hedera.services.usage.token.meta.TokenUpdateMeta;
import com.hedera.services.utils.TxnAccessor;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExpandHandleSpanMapAccessorTest {
	private Map<String, Object> span = new HashMap<>();

	@Mock
	private TxnAccessor accessor;

	private ExpandHandleSpanMapAccessor subject;

	@BeforeEach
	void setUp() {
		subject = new ExpandHandleSpanMapAccessor();

		given(accessor.getSpanMap()).willReturn(span);
	}

	@Test
	void testsForImpliedXfersAsExpected() {
		Assertions.assertDoesNotThrow(() -> subject.getImpliedTransfers(accessor));
	}

	@Test
	void testsForTokenCreateMetaAsExpected() {
		Assertions.assertDoesNotThrow(() -> subject.getTokenCreateMeta(accessor));
	}

	@Test
	void testsForTokenBurnMetaAsExpected() {
		Assertions.assertDoesNotThrow(() -> subject.getTokenBurnMeta(accessor));
	}

	@Test
	void testsForTokenWipeMetaAsExpected() {
		Assertions.assertDoesNotThrow(() -> subject.getTokenWipeMeta(accessor));
	}

	@Test
	void testsForTokenFreezeMetaAsExpected() {
		final var tokenFreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenFreezeUsageFrom();

		subject.setTokenFreezeMeta(accessor, tokenFreezeMeta);

		assertEquals(48, subject.getTokenFreezeMeta(accessor).getBpt());
	}

	@Test
	void testsForTokenUnfreezeMetaAsExpected() {
		final var tokenUnfreezeMeta = TOKEN_OPS_USAGE_UTILS.tokenUnfreezeUsageFrom();

		subject.setTokenUnfreezeMeta(accessor, tokenUnfreezeMeta);

		assertEquals(48, subject.getTokenUnfreezeMeta(accessor).getBpt());
	}

	@Test
	void testsForTokenGrantKycMetaAsExpected() {
		final var tokenGrantKycMeta = TOKEN_OPS_USAGE_UTILS.tokenGrantKycUsageFrom();

		subject.setTokenGrantKycMeta(accessor, tokenGrantKycMeta);

		assertEquals(48, subject.getTokenGrantKycMeta(accessor).getBpt());
	}

	@Test
	void testsForTokenRevokeKycMetaAsExpected() {
		final var tokenRevokeKycMeta = TOKEN_OPS_USAGE_UTILS.tokenRevokeKycUsageFrom();

		subject.setTokenRevokeKycMeta(accessor, tokenRevokeKycMeta);

		assertEquals(48, subject.getTokenRevokeKycMeta(accessor).getBpt());
	}

	@Test
	void testsForTokenDeleteMetaAsExpected() {
		final var tokenDeleteMeta = TOKEN_OPS_USAGE_UTILS.tokenDeleteUsageFrom();

		subject.setTokenDeleteMeta(accessor, tokenDeleteMeta);

		assertEquals(24, subject.getTokenDeleteMeta(accessor).getBpt());
	}

	@Test
	void testsForTokenUpdateMetaAsExpected() {
		final var tokenUpdateMeta = TokenUpdateMeta.newBuilder().setNewKeysLen(132).setNewSymLen(5).setNewNameLen(12)
				.setNewEffectiveTxnStartTime(1_234_567L).setRemoveAutoRenewAccount(true).build();
		subject.setTokenUpdateMeta(accessor, tokenUpdateMeta);

		assertEquals(132, subject.getTokenUpdateMeta(accessor).getNewKeysLen());
		assertEquals(12, subject.getTokenUpdateMeta(accessor).getNewNameLen());
	}

	@Test
	void testsForCryptoCreateMetaAsExpected() {
		var opMeta = new CryptoCreateMeta.Builder().baseSize(1_234).lifeTime(1_234_567L).maxAutomaticAssociations(12)
				.build();

		subject.setCryptoCreateMeta(accessor, opMeta);

		assertEquals(1_234, subject.getCryptoCreateMeta(accessor).getBaseSize());
	}

	@Test
	void testsForCryptoUpdateMetaAsExpected() {
		final var opMeta = new CryptoUpdateMeta.Builder().keyBytesUsed(123).msgBytesUsed(1_234).memoSize(100)
				.effectiveNow(1_234_000L).expiry(1_234_567L).hasProxy(false).maxAutomaticAssociations(3)
				.hasMaxAutomaticAssociations(true).build();

		subject.setCryptoUpdate(accessor, opMeta);

		assertEquals(3, subject.getCryptoUpdateMeta(accessor).getMaxAutomaticAssociations());
	}
}
