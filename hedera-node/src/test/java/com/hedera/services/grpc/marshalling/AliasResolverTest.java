package com.hedera.services.grpc.marshalling;

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

import com.google.protobuf.ByteString;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.utils.EntityNum;
import com.hedera.test.utils.IdUtils;
import com.hederahashgraph.api.proto.java.AccountAmount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.NftTransfer;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static com.hedera.services.utils.EntityNum.MISSING_NUM;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AliasResolverTest {
	@Mock
	private AliasManager aliasManager;

	private AliasResolver subject;

	@BeforeEach
	void setUp() {
		subject = new AliasResolver();
	}

	@Test
	void transformsTokenAdjusts() {
		final var unresolved = aaId(bNum.longValue(), theAmount);
		final var op = CryptoTransferTransactionBody.newBuilder()
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(someToken)
						.addTransfers(aaAlias(anAlias, anAmount))
						.addTransfers(unresolved)
						.addTransfers(aaAlias(someAlias, -anAmount))
						.addNftTransfers(NftTransfer.newBuilder()
								.setSenderAccountID(AccountID.newBuilder().setAlias(anAlias))
								.setReceiverAccountID(bNum.toGrpcAccountId())
								.setSerialNumber(1L)
								.build())
						.addNftTransfers(NftTransfer.newBuilder()
								.setSenderAccountID(AccountID.newBuilder().setAlias(otherAlias))
								.setReceiverAccountID(bNum.toGrpcAccountId())
								.setSerialNumber(2L)
								.build()))
				.build();
		assertTrue(AliasResolver.usesAliases(op));

		given(aliasManager.lookupIdBy(anAlias)).willReturn(aNum);
		given(aliasManager.lookupIdBy(someAlias)).willReturn(MISSING_NUM);
		given(aliasManager.lookupIdBy(otherAlias)).willReturn(MISSING_NUM);

		final var resolvedOp = subject.resolve(op, aliasManager);

		assertEquals(2, subject.perceivedMissingAliases());
		assertEquals(Map.of(anAlias, aNum, someAlias, MISSING_NUM, otherAlias, MISSING_NUM), subject.resolutions());
		final var tokensAdjusts = resolvedOp.getTokenTransfers(0);
		assertEquals(someToken, tokensAdjusts.getToken());
		assertEquals(aNum.toGrpcAccountId(), tokensAdjusts.getTransfers(0).getAccountID());
		assertEquals(unresolved, tokensAdjusts.getTransfers(1));
		final var ownershipChange = tokensAdjusts.getNftTransfers(0);
		assertEquals(aNum.toGrpcAccountId(), ownershipChange.getSenderAccountID());
		assertEquals(bNum.toGrpcAccountId(), ownershipChange.getReceiverAccountID());
		assertEquals(1L, ownershipChange.getSerialNumber());
	}

	@Test
	void transformsHbarAdjustsWithRepeatedMissingAlias() {
		final var creationAdjust = aaAlias(anAlias, theAmount);
		final var badCreationAdjust = aaAlias(someAlias, theAmount);
		final var op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(TransferList.newBuilder()
						.addAccountAmounts(creationAdjust)
						.addAccountAmounts(aaAlias(theAlias, anAmount))
						.addAccountAmounts(aaAlias(someAlias, someAmount))
						.addAccountAmounts(badCreationAdjust)
						.build())
				.build();
		assertTrue(AliasResolver.usesAliases(op));

		given(aliasManager.lookupIdBy(theAlias)).willReturn(aNum);
		given(aliasManager.lookupIdBy(anAlias)).willReturn(MISSING_NUM);
		given(aliasManager.lookupIdBy(someAlias)).willReturn(MISSING_NUM);

		final var resolvedOp = subject.resolve(op, aliasManager);

		assertEquals(1, subject.perceivedAutoCreations());
		assertEquals(1, subject.perceivedInvalidCreations());
		assertEquals(1, subject.perceivedMissingAliases());
		assertEquals(Map.of(anAlias, MISSING_NUM, theAlias, aNum, someAlias, MISSING_NUM), subject.resolutions());
		assertEquals(creationAdjust.getAccountID(), resolvedOp.getTransfers().getAccountAmounts(0).getAccountID());
		assertEquals(aNum.toGrpcAccountId(), resolvedOp.getTransfers().getAccountAmounts(1).getAccountID());
	}

	@Test
	void transformsHbarAdjustsWithInvalidAlias() {
		final var badCreationAdjust = aaAlias(someAlias, theAmount);
		final var op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(TransferList.newBuilder()
						.addAccountAmounts(aaAlias(theAlias, anAmount))
						.addAccountAmounts(badCreationAdjust)
						.build())
				.build();
		assertTrue(AliasResolver.usesAliases(op));

		given(aliasManager.lookupIdBy(theAlias)).willReturn(aNum);
		given(aliasManager.lookupIdBy(someAlias)).willReturn(MISSING_NUM);

		final var resolvedOp = subject.resolve(op, aliasManager);

		assertEquals(0, subject.perceivedAutoCreations());
		assertEquals(1, subject.perceivedInvalidCreations());
		assertEquals(Map.of(theAlias, aNum, someAlias, MISSING_NUM), subject.resolutions());
		assertEquals(aNum.toGrpcAccountId(), resolvedOp.getTransfers().getAccountAmounts(0).getAccountID());
	}

	@Test
	void detectsNoAliases() {
		final var noAliasBody=  CryptoTransferTransactionBody.newBuilder()
				.setTransfers(TransferList.newBuilder()
						.addAccountAmounts(aaId(1_234L, 4_321L))
						.build())
				.addAllTokenTransfers(List.of(
						TokenTransferList.newBuilder()
								.setToken(someToken)
								.addTransfers(aaId(2_345L, 5_432L))
								.build(),
						TokenTransferList.newBuilder()
								.setToken(otherToken)
								.addNftTransfers(NftTransfer.newBuilder()
										.setSenderAccountID(aNum.toGrpcAccountId())
										.setReceiverAccountID(bNum.toGrpcAccountId())
										.setSerialNumber(1L)
										.build())
								.build()))
				.build();
		assertFalse(AliasResolver.usesAliases(noAliasBody));
	}

	@Test
	void detectsFungibleAliases() {
		final var fungibleAliasBody=  CryptoTransferTransactionBody.newBuilder()
				.setTransfers(TransferList.newBuilder()
						.addAccountAmounts(aaId(1_234L, 4_321L))
						.build())
				.addAllTokenTransfers(List.of(
						TokenTransferList.newBuilder()
								.setToken(someToken)
								.addTransfers(aaAlias(anAlias, 5_432L))
								.build()))
				.build();
		assertTrue(AliasResolver.usesAliases(fungibleAliasBody));
	}

	private AccountAmount aaAlias(final ByteString alias, final long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(AccountID.newBuilder().setAlias(alias).build())
				.build();
	}

	private AccountAmount aaId(final long num, final long amount) {
		return AccountAmount.newBuilder()
				.setAmount(amount)
				.setAccountID(AccountID.newBuilder().setAccountNum(num).build())
				.build();
	}

	private static final Key aPrimitiveKey = Key.newBuilder()
			.setEd25519(ByteString.copyFromUtf8("01234567890123456789012345678901"))
			.build();
	private static final long anAmount = 1234;
	private static final long theAmount = 12345;
	private static final long someAmount = -1234;
	private static final EntityNum aNum = EntityNum.fromLong(4321);
	private static final EntityNum bNum = EntityNum.fromLong(5432);
	private static final ByteString anAlias = aPrimitiveKey.toByteString();
	private static final ByteString theAlias = ByteString.copyFromUtf8("second");
	private static final ByteString someAlias = ByteString.copyFromUtf8("third");
	private static final ByteString otherAlias = ByteString.copyFromUtf8("fourth");
	private static final TokenID someToken = IdUtils.asToken("0.0.666");
	private static final TokenID otherToken = IdUtils.asToken("0.0.777");
}
