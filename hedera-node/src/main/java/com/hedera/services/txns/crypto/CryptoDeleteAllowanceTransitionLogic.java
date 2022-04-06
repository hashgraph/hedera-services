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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.context.primitives.StateView;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Account;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.UniqueToken;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.crypto.validators.DeleteAllowanceChecks;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.NftRemoveAllowance;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.exceptions.ValidationUtils.validateTrue;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.fetchOwnerAccount;
import static com.hedera.services.txns.crypto.helpers.AllowanceHelpers.validOwner;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SENDER_DOES_NOT_OWN_NFT_SERIAL_NO;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

/**
 * Implements the {@link TransitionLogic} for a HAPI CryptoDeleteAllowance transaction,
 * and the conditions under which such logic is syntactically correct.
 */
public class CryptoDeleteAllowanceTransitionLogic implements TransitionLogic {
	private final TransactionContext txnCtx;
	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final DeleteAllowanceChecks deleteAllowanceChecks;
	private final List<UniqueToken> nftsTouched;
	private final StateView workingView;

	@Inject
	public CryptoDeleteAllowanceTransitionLogic(
			final TransactionContext txnCtx,
			final AccountStore accountStore,
			final DeleteAllowanceChecks deleteAllowanceChecks,
			final TypedTokenStore tokenStore,
			final StateView workingView) {
		this.txnCtx = txnCtx;
		this.accountStore = accountStore;
		this.deleteAllowanceChecks = deleteAllowanceChecks;
		this.tokenStore = tokenStore;
		this.nftsTouched = new ArrayList<>();
		this.workingView = workingView;
	}

	@Override
	public void doStateTransition() {
		/* --- Extract gRPC --- */
		final TransactionBody cryptoDeleteAllowanceTxn = txnCtx.accessor().getTxn();
		final AccountID payer = cryptoDeleteAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
		nftsTouched.clear();

		/* --- Use models --- */
		final Id payerId = Id.fromGrpcAccount(payer);
		final var payerAccount = accountStore.loadAccount(payerId);

		/* --- Do the business logic --- */
		deleteNftSerials(op.getNftAllowancesList(), payerAccount);

		/* --- Persist the owner accounts and nfts --- */
		for (final var nft : nftsTouched) {
			tokenStore.persistNft(nft);
		}

		txnCtx.setStatus(SUCCESS);
	}

	/**
	 * Clear spender on the provided nft serials. If the owner is not provided in any allowance,
	 * considers payer of the transaction as owner while checking if nft is owned by owner.
	 *
	 * @param nftAllowances
	 * 		given nftAllowances
	 * @param payerAccount
	 * 		payer for the transaction
	 */
	private void deleteNftSerials(final List<NftRemoveAllowance> nftAllowances, final Account payerAccount) {
		if (nftAllowances.isEmpty()) {
			return;
		}

		final var nfts = new ArrayList<UniqueToken>();
		for (var allowance : nftAllowances) {
			final var serialNums = allowance.getSerialNumbersList();
			final var tokenId = Id.fromGrpcToken(allowance.getTokenId());
			final var accountToWipe = fetchOwnerAccount(allowance.getOwner(), payerAccount, accountStore);
			final var token = tokenStore.loadPossiblyPausedToken(tokenId);

			for (var serial : serialNums) {
				final var nft = tokenStore.loadUniqueToken(tokenId, serial);
				validateTrue(validOwner(nft, accountToWipe.getId(), token), SENDER_DOES_NOT_OWN_NFT_SERIAL_NO);
				nft.clearSpender();
				nfts.add(nft);
			}
			nftsTouched.addAll(nfts);
			nfts.clear();
		}
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasCryptoDeleteAllowance;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return this::validate;
	}

	private ResponseCodeEnum validate(TransactionBody cryptoDeleteAllowanceTxn) {
		final AccountID payer = cryptoDeleteAllowanceTxn.getTransactionID().getAccountID();
		final var op = cryptoDeleteAllowanceTxn.getCryptoDeleteAllowance();
		final var payerAccount = accountStore.loadAccount(Id.fromGrpcAccount(payer));

		return deleteAllowanceChecks.deleteAllowancesValidation(
				op.getNftAllowancesList(),
				payerAccount,
				workingView);
	}
}
