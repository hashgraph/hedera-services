package com.hedera.services.usage.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2021 Hedera Hashgraph, LLC
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

import com.hedera.services.test.IdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TransactionBody;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.hedera.services.test.IdUtils.asAccount;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToCryptoMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToNftMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.convertToTokenMapFromGranted;
import static com.hedera.services.usage.crypto.CryptoContextUtils.countSerials;
import static com.hederahashgraph.fee.FeeBuilder.CRYPTO_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.LONG_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.NFT_ALLOWANCE_SIZE;
import static com.hederahashgraph.fee.FeeBuilder.TOKEN_ALLOWANCE_SIZE;
import static org.junit.jupiter.api.Assertions.assertEquals;

class CryptoAdjustAllowanceMetaTest {
	private final AccountID proxy = asAccount("0.0.1234");
	private CryptoAllowance cryptoAllowances = CryptoAllowance.newBuilder().setSpender(proxy).setAmount(10L).build();
	private TokenAllowance tokenAllowances = TokenAllowance.newBuilder()
			.setSpender(proxy).setAmount(10L).setTokenId(IdUtils.asToken("0.0.1000")).build();
	private NftAllowance nftAllowances = NftAllowance.newBuilder().setSpender(proxy)
			.setTokenId(IdUtils.asToken("0.0.1000"))
			.addAllSerialNumbers(List.of(1L, 2L, 3L)).build();
	private Map<Long, Long> cryptoAllowancesMap = new HashMap<>();
	private Map<ExtantCryptoContext.AllowanceMapKey, Long> tokenAllowancesMap = new HashMap<>();
	private Map<ExtantCryptoContext.AllowanceMapKey, ExtantCryptoContext.AllowanceMapValue> nftAllowancesMap =
			new HashMap<>();

	@BeforeEach
	void setUp() {
		cryptoAllowancesMap = CryptoContextUtils.convertToCryptoMap(List.of(cryptoAllowances));
		tokenAllowancesMap = CryptoContextUtils.convertToTokenMap(List.of(tokenAllowances));
		nftAllowancesMap = CryptoContextUtils.convertToNftMap(List.of(nftAllowances));
	}

	@Test
	void allGettersAndToStringWork() {
		final var expected = "CryptoAdjustAllowanceMeta{cryptoAllowances={1234=10}, " +
				"tokenAllowances={AllowanceMapKey[tokenNum=1000, spenderNum=1234]=10}, " +
				"nftAllowances={AllowanceMapKey[tokenNum=1000, " +
				"spenderNum=1234]=AllowanceMapValue[approvedForAll=false," +
				" serialNums=[1, 2, 3]]}, effectiveNow=1234567, msgBytesUsed=112}";
		final var now = 1_234_567;
		final var subject = CryptoAdjustAllowanceMeta.newBuilder()
				.msgBytesUsed(112)
				.cryptoAllowances(cryptoAllowancesMap)
				.tokenAllowances(tokenAllowancesMap)
				.nftAllowances(nftAllowancesMap)
				.effectiveNow(now)
				.build();

		assertEquals(now, subject.getEffectiveNow());
		assertEquals(112, subject.getMsgBytesUsed());
		assertEquals(expected, subject.toString());
	}

	@Test
	void calculatesBaseSizeAsExpected() {
		final var op = CryptoAdjustAllowanceTransactionBody
				.newBuilder()
				.addAllCryptoAllowances(List.of(cryptoAllowances))
				.addAllTokenAllowances(List.of(tokenAllowances))
				.addAllNftAllowances(List.of(nftAllowances))
				.build();
		final var canonicalTxn = TransactionBody.newBuilder()
				.setCryptoAdjustAllowance(op).build();

		var subject = new CryptoAdjustAllowanceMeta(op,
				canonicalTxn.getTransactionID().getTransactionValidStart().getSeconds());

		final var expectedMsgBytes = (op.getCryptoAllowancesCount() * CRYPTO_ALLOWANCE_SIZE)
				+ (op.getTokenAllowancesCount() * TOKEN_ALLOWANCE_SIZE)
				+ (op.getNftAllowancesCount() * NFT_ALLOWANCE_SIZE) +
				countSerials(op.getNftAllowancesList()) * LONG_SIZE;

		assertEquals(expectedMsgBytes, subject.getMsgBytesUsed());

		final var expectedCryptoMap = new HashMap<>();
		final var expectedTokenMap = new HashMap<>();
		final var expectedNftMap = new HashMap<>();
		expectedCryptoMap.put(proxy.getAccountNum(), 10L);
		expectedTokenMap.put(new ExtantCryptoContext.AllowanceMapKey(1000L, proxy.getAccountNum()), 10L);
		expectedNftMap.put(new ExtantCryptoContext.AllowanceMapKey(1000L, proxy.getAccountNum()),
				new ExtantCryptoContext.AllowanceMapValue(false, List.of(1L, 2L, 3L)));
		assertEquals(expectedCryptoMap, subject.getCryptoAllowances());
		assertEquals(expectedTokenMap, subject.getTokenAllowances());
		assertEquals(expectedNftMap, subject.getNftAllowances());
	}

	@Test
	void hashCodeAndEqualsWork() {
		final var now = 1_234_567;
		final var subject1 = CryptoAdjustAllowanceMeta.newBuilder()
				.msgBytesUsed(112)
				.cryptoAllowances(cryptoAllowancesMap)
				.tokenAllowances(tokenAllowancesMap)
				.nftAllowances(nftAllowancesMap)
				.effectiveNow(now)
				.build();

		final var subject2 = CryptoAdjustAllowanceMeta.newBuilder()
				.msgBytesUsed(112)
				.cryptoAllowances(cryptoAllowancesMap)
				.tokenAllowances(tokenAllowancesMap)
				.nftAllowances(nftAllowancesMap)
				.effectiveNow(now)
				.build();

		assertEquals(subject1, subject2);
		assertEquals(subject1.hashCode(), subject2.hashCode());
	}
}
