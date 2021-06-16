package com.hedera.services.ledger;

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

import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.grpc.marshalling.ImpliedTransfers;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMarshal;
import com.hedera.services.grpc.marshalling.ImpliedTransfersMeta;
import com.hedera.services.store.models.Id;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoTransferTransactionBody;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TokenTransferList;
import com.hederahashgraph.api.proto.java.TransferList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static com.hedera.test.utils.IdUtils.adjustFrom;
import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_WAS_DELETED;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TRANSFER_LIST_SIZE_LIMIT_EXCEEDED;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class ImpliedTransfersMarshalTest {
	private CryptoTransferTransactionBody op;

	@Mock
	private GlobalDynamicProperties dynamicProperties;
	@Mock
	private PureTransferSemanticChecks transferSemanticChecks;

	private ImpliedTransfersMarshal subject;

	private final Id aModel = new Id(1, 2, 3);
	private final Id bModel = new Id(2, 3, 4);
	private final Id cModel = new Id(3, 4, 5);
	private final Id token = new Id(0, 0, 75231);
	private final Id anotherToken = new Id(0, 0, 75232);
	private final Id yetAnotherToken = new Id(0, 0, 75233);
	private final TokenID anId = asToken("0.0.75231");
	private final TokenID anotherId = asToken("0.0.75232");
	private final TokenID yetAnotherId = asToken("0.0.75233");
	private final AccountID a = asAccount("1.2.3");
	private final AccountID b = asAccount("2.3.4");
	private final AccountID c = asAccount("3.4.5");

	private final long aHbarChange = -100L;
	private final long bHbarChange = +50L;
	private final long cHbarChange = +50L;
	private final long aAnotherTokenChange = -50L;
	private final long bAnotherTokenChange = +25L;
	private final long cAnotherTokenChange = +25L;
	private final long bTokenChange = -100L;
	private final long cTokenChange = +100L;
	private final long aYetAnotherTokenChange = -15L;
	private final long bYetAnotherTokenChange = +15L;

	private final int maxExplicitHbarAdjusts = 5;
	private final int maxExplicitTokenAdjusts = 50;

	@BeforeEach
	void setUp() {
		subject = new ImpliedTransfersMarshal(dynamicProperties, transferSemanticChecks);
	}

	@Test
	void validatesXfers() {
		setupFixtureOp();
		final var expectedMeta = new ImpliedTransfersMeta(
				maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);
		// and:
		given(transferSemanticChecks.fullPureValidation(
				maxExplicitHbarAdjusts,
				maxExplicitTokenAdjusts,
				op.getTransfers(),
				op.getTokenTransfersList())).willReturn(TRANSFER_LIST_SIZE_LIMIT_EXCEEDED);

		// when:
		final var result = subject.marshalFromGrpc(op);

		// then:
		assertEquals(result.getMeta(), expectedMeta);
	}

	@Test
	void getsExpectedList() {
		setupFixtureOp();
		// and:
		final List<BalanceChange> expectedChanges = List.of(new BalanceChange[] {
						BalanceChange.hbarAdjust(aModel, aHbarChange),
						BalanceChange.hbarAdjust(bModel, bHbarChange),
						BalanceChange.hbarAdjust(cModel, cHbarChange),
						BalanceChange.tokenAdjust(anotherToken, aModel, aAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, bModel, bAnotherTokenChange),
						BalanceChange.tokenAdjust(anotherToken, cModel, cAnotherTokenChange),
						BalanceChange.tokenAdjust(token, bModel, bTokenChange),
						BalanceChange.tokenAdjust(token, cModel, cTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, aModel, aYetAnotherTokenChange),
						BalanceChange.tokenAdjust(yetAnotherToken, bModel, bYetAnotherTokenChange),
				}
		);
		for (var change : expectedChanges) {
			if (!change.isForHbar()) {
				change.setExplicitTokenId(change.tokenId());
			}
			change.setExplicitAccountId(change.accountId());
		}
		// and:
		final var expectedMeta = new ImpliedTransfersMeta(maxExplicitHbarAdjusts, maxExplicitTokenAdjusts, OK);

		given(dynamicProperties.maxTransferListSize()).willReturn(maxExplicitHbarAdjusts);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(maxExplicitTokenAdjusts);

		// when:
		final var result = subject.marshalFromGrpc(op);

		// then:
		assertEquals(result.getMeta(), expectedMeta);
		assertEquals(result.getChanges(), expectedChanges);
	}

	@Test
	void metaObjectContractSanityChecks() {
		// given:
		final var oneMeta = new ImpliedTransfersMeta(3, 4, OK);
		final var twoMeta = new ImpliedTransfersMeta(1, 2, TOKEN_WAS_DELETED);
		// and:
		final var oneRepr = "ImpliedTransfersMeta{code=OK, " +
				"maxExplicitHbarAdjusts=3, maxExplicitTokenAdjusts=4}";
		final var twoRepr = "ImpliedTransfersMeta{code=TOKEN_WAS_DELETED, " +
				"maxExplicitHbarAdjusts=1, maxExplicitTokenAdjusts=2}";

		// expect:
		assertNotEquals(oneMeta, twoMeta);
		assertNotEquals(oneMeta.hashCode(), twoMeta.hashCode());
		// and:
		assertEquals(oneRepr, oneMeta.toString());
		assertEquals(twoRepr, twoMeta.toString());
	}

	@Test
	void impliedXfersObjectContractSanityChecks() {
		// given:
		final var twoChanges = List.of(BalanceChange.tokenAdjust(
				new Id(1, 2, 3),
				new Id(4, 5, 6),
				7));
		final var oneImpliedXfers = ImpliedTransfers.invalid(3, 4, TOKEN_WAS_DELETED);
		final var twoImpliedXfers = ImpliedTransfers.valid(1, 100, twoChanges);
		// and:
		final var oneRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=TOKEN_WAS_DELETED, " +
				"maxExplicitHbarAdjusts=3, maxExplicitTokenAdjusts=4}, changes=[]}";
		final var twoRepr = "ImpliedTransfers{meta=ImpliedTransfersMeta{code=OK, maxExplicitHbarAdjusts=1, " +
				"maxExplicitTokenAdjusts=100}, changes=[BalanceChange{token=Id{shard=1, realm=2, num=3}, " +
				"account=Id{shard=4, realm=5, num=6}, units=7}]}";

		// expect:
		assertNotEquals(oneImpliedXfers, twoImpliedXfers);
		assertNotEquals(oneImpliedXfers.hashCode(), twoImpliedXfers.hashCode());
		// and:
		assertEquals(oneRepr, oneImpliedXfers.toString());
		assertEquals(twoRepr, twoImpliedXfers.toString());
	}

	@Test
	void metaRecognizesIdenticalConditions() {
		// given:
		final var meta = new ImpliedTransfersMeta(3, 4, OK);

		given(dynamicProperties.maxTransferListSize()).willReturn(3);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(4);

		// expect:
		assertTrue(meta.wasDerivedFrom(dynamicProperties));

		// and:
		given(dynamicProperties.maxTransferListSize()).willReturn(2);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(4);

		// expect:
		assertFalse(meta.wasDerivedFrom(dynamicProperties));

		// and:
		given(dynamicProperties.maxTransferListSize()).willReturn(3);
		given(dynamicProperties.maxTokenTransferListSize()).willReturn(3);

		// expect:
		assertFalse(meta.wasDerivedFrom(dynamicProperties));
	}

	private void setupFixtureOp() {
		var hbarAdjusts = TransferList.newBuilder()
				.addAccountAmounts(adjustFrom(a, -100))
				.addAccountAmounts(adjustFrom(b, 50))
				.addAccountAmounts(adjustFrom(c, 50))
				.build();
		op = CryptoTransferTransactionBody.newBuilder()
				.setTransfers(hbarAdjusts)
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -50),
								adjustFrom(b, 25),
								adjustFrom(c, 25)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(anId)
						.addAllTransfers(List.of(
								adjustFrom(b, -100),
								adjustFrom(c, 100)
						)))
				.addTokenTransfers(TokenTransferList.newBuilder()
						.setToken(yetAnotherId)
						.addAllTransfers(List.of(
								adjustFrom(a, -15),
								adjustFrom(b, 15)
						)))
				.build();
	}
}
