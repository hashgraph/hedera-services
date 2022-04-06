package com.hedera.services.txns.crypto;

/*-
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
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

import com.google.protobuf.BoolValue;
import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.state.enums.TokenType;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.Token;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.crypto.validators.AdjustAllowanceChecks;
import com.hedera.services.utils.PlatformTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoAdjustAllowanceTransactionBody;
import com.hederahashgraph.api.proto.java.CryptoAllowance;
import com.hederahashgraph.api.proto.java.NftAllowance;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.TokenAllowance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.hederahashgraph.api.proto.java.TransactionID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.TreeSet;

import static com.hedera.test.utils.IdUtils.asAccount;
import static com.hedera.test.utils.IdUtils.asToken;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CryptoAdjustAllowanceTransitionLogicTest {
	@Mock
	private TransactionContext txnCtx;
	@Mock
	private AccountStore accountStore;
	@Mock
	private AdjustAllowanceChecks adjustAllowanceChecks;
	@Mock
	private PlatformTxnAccessor accessor;
	@Mock
	private StateView view;
	@Mock
	private AdjustAllowanceLogic adjustAllowanceLogic;

	private TransactionBody cryptoAdjustAllowanceTxn;
	private CryptoAdjustAllowanceTransactionBody op;

	CryptoAdjustAllowanceTransitionLogic subject;

	@BeforeEach
	private void setup() {
		subject = new CryptoAdjustAllowanceTransitionLogic(txnCtx, accountStore,
				adjustAllowanceChecks, adjustAllowanceLogic, view);
		nft1.setOwner(ownerId);
		nft2.setOwner(ownerId);
		nft3.setOwner(ownerId);
		nft4.setOwner(ownerId);
		nft5.setOwner(ownerId);
	}

	@Test
	void hasCorrectApplicability() {
		givenValidTxnCtx();

		assertTrue(subject.applicability().test(cryptoAdjustAllowanceTxn));
		assertFalse(subject.applicability().test(TransactionBody.getDefaultInstance()));
	}

	@Test
	void callsApproveAllowanceLogic() {
		givenValidTxnCtx();

		given(accessor.getTxn()).willReturn(cryptoAdjustAllowanceTxn);
		given(txnCtx.accessor()).willReturn(accessor);

		subject.doStateTransition();

		verify(adjustAllowanceLogic).adjustAllowance(op.getCryptoAllowancesList(),
				op.getTokenAllowancesList(),
				op.getNftAllowancesList(),
				owner);
	}

	@Test
	void semanticCheckDelegatesWorks() {
		givenValidTxnCtx();
		given(adjustAllowanceChecks.allowancesValidation(op.getCryptoAllowancesList(), op.getTokenAllowancesList(),
				op.getNftAllowancesList(), ownerAccount, view)).willReturn(OK);
		given(accountStore.loadAccount(ownerAccount.getId())).willReturn(ownerAccount);
		assertEquals(OK, subject.semanticCheck().apply(cryptoAdjustAllowanceTxn));
	}

	private void givenValidTxnCtx() {
		token1Model.setMaxSupply(5000L);
		token1Model.setType(TokenType.FUNGIBLE_COMMON);
		token2Model.setMaxSupply(5000L);
		token2Model.setType(TokenType.NON_FUNGIBLE_UNIQUE);

		cryptoAllowances.add(cryptoAllowance1);
		tokenAllowances.add(tokenAllowance1);
		nftAllowances.add(nftAllowance1);
		nftAllowances.add(nftAllowance2);

		cryptoAdjustAllowanceTxn = TransactionBody.newBuilder()
				.setTransactionID(ourTxnId())
				.setCryptoAdjustAllowance(
						CryptoAdjustAllowanceTransactionBody.newBuilder()
								.addAllCryptoAllowances(cryptoAllowances)
								.addAllTokenAllowances(tokenAllowances)
								.addAllNftAllowances(nftAllowances)
				).build();
		op = cryptoAdjustAllowanceTxn.getCryptoAdjustAllowance();

		ownerAccount.setApproveForAllNfts(new TreeSet<>());
		ownerAccount.setCryptoAllowances(new HashMap<>());
		ownerAccount.setFungibleTokenAllowances(new HashMap<>());
	}

	private TransactionID ourTxnId() {
		return TransactionID.newBuilder()
				.setAccountID(owner)
				.setTransactionValidStart(
						Timestamp.newBuilder().setSeconds(consensusTime.getEpochSecond()))
				.build();
	}

	private static final long serial1 = 1L;
	private static final long serial2 = 10L;
	private static final long serial3 = 20L;
	private static final AccountID spender1 = asAccount("0.0.123");
	private static final AccountID owner = asAccount("0.0.5000");
	private static final TokenID token1 = asToken("0.0.100");
	private static final TokenID token2 = asToken("0.0.200");
	private static final Id ownerId = Id.fromGrpcAccount(owner);
	private static final Id tokenId1 = Id.fromGrpcToken(token1);
	private static final Id tokenId2 = Id.fromGrpcToken(token2);
	private static final Instant consensusTime = Instant.now();
	private final Account ownerAccount = new Account(ownerId);
	private final Token token1Model = new Token(tokenId1);
	private final Token token2Model = new Token(tokenId2);
	private final UniqueToken nft1 = new UniqueToken(tokenId1, serial1);
	private final UniqueToken nft2 = new UniqueToken(tokenId1, serial2);
	private final UniqueToken nft3 = new UniqueToken(tokenId1, serial3);
	private final UniqueToken nft4 = new UniqueToken(tokenId2, serial1);
	private final UniqueToken nft5 = new UniqueToken(tokenId2, serial2);

	private final CryptoAllowance cryptoAllowance1 = CryptoAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).build();
	private final TokenAllowance tokenAllowance1 = TokenAllowance.newBuilder().setSpender(spender1).setAmount(
			10L).setTokenId(token1).build();
	private final NftAllowance nftAllowance1 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token2).setApprovedForAll(BoolValue.of(true))
			.addAllSerialNumbers(List.of(1L, 10L)).build();
	private final NftAllowance nftAllowance2 = NftAllowance.newBuilder()
			.setSpender(spender1)
			.setTokenId(token1).setApprovedForAll(BoolValue.of(false))
			.addAllSerialNumbers(List.of(1L, 10L, 20L)).build();
	private List<CryptoAllowance> cryptoAllowances = new ArrayList<>();
	private List<TokenAllowance> tokenAllowances = new ArrayList<>();
	private List<NftAllowance> nftAllowances = new ArrayList<>();
}
