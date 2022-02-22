package com.hedera.services.queries.crypto;

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

import com.hedera.services.context.primitives.StateView;
import com.hedera.services.ledger.accounts.AliasManager;
import com.hedera.services.queries.AnswerService;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hedera.services.state.merkle.MerkleToken;
import com.hedera.services.utils.EntityNum;
import com.hedera.services.txns.validation.OptionValidator;
import com.hedera.services.utils.SignedTxnAccessor;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceQuery;
import com.hederahashgraph.api.proto.java.CryptoGetAccountBalanceResponse;
import com.hederahashgraph.api.proto.java.HederaFunctionality;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenBalance;
import com.hederahashgraph.api.proto.java.TokenID;
import com.swirlds.merkle.map.MerkleMap;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.Optional;

import static com.hedera.services.state.merkle.MerkleEntityAssociation.fromAccountTokenRel;
import static com.hedera.services.utils.EntityIdUtils.asAccount;
import static com.hedera.services.utils.EntityIdUtils.isAlias;
import static com.hederahashgraph.api.proto.java.HederaFunctionality.CryptoGetAccountBalance;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;

@Singleton
public class GetAccountBalanceAnswer implements AnswerService {
	private final AliasManager aliasManager;
	private final OptionValidator optionValidator;

	@Inject
	public GetAccountBalanceAnswer(final AliasManager aliasManager, final OptionValidator optionValidator) {
		this.aliasManager = aliasManager;
		this.optionValidator = optionValidator;
	}

	@Override
	public ResponseCodeEnum checkValidity(Query query, StateView view) {
		MerkleMap<EntityNum, MerkleAccount> accounts = view.accounts();
		CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();
		return validityOf(op, accounts);
	}

	@Override
	public boolean requiresNodePayment(Query query) {
		return false;
	}

	@Override
	public boolean needsAnswerOnlyCost(Query query) {
		return false;
	}

	@Override
	public Response responseGiven(Query query, StateView view, ResponseCodeEnum validity, long cost) {
		MerkleMap<EntityNum, MerkleAccount> accounts = view.accounts();
		CryptoGetAccountBalanceQuery op = query.getCryptogetAccountBalance();

		final var id = targetOf(op);
		CryptoGetAccountBalanceResponse.Builder opAnswer = CryptoGetAccountBalanceResponse.newBuilder()
				.setHeader(answerOnlyHeader(validity))
				.setAccountID(id);

		if (validity == OK) {
			var key = EntityNum.fromAccountId(id);
			var account = accounts.get(key);
			opAnswer.setBalance(account.getBalance());

			// this tokenIds list can be more than 1000 size now. Limit the token balances and track the index;
			final var tokenIdsIndex = account.getTokenIdsIndex();
			final var limitedTokenIds = account.tokens().asTokenIds(tokenIdsIndex, 1000);
			final var newTokenIdsIndex = limitedTokenIds.getLeft();
			account.setTokenIdsIndex(newTokenIdsIndex);
			accounts.put(key, account);

			for (TokenID tId : limitedTokenIds.getRight()) {
				var relKey = fromAccountTokenRel(id, tId);
				var relationship = view.tokenAssociations().get(relKey);
				var decimals = view.tokenWith(tId).map(MerkleToken::decimals).orElse(0);
				opAnswer.addTokenBalances(TokenBalance.newBuilder()
						.setTokenId(tId)
						.setBalance(relationship.getBalance())
						.setDecimals(decimals)
						.build());
			}
		}

		return Response.newBuilder().setCryptogetAccountBalance(opAnswer).build();
	}

	@Override
	public Optional<SignedTxnAccessor> extractPaymentFrom(Query query) {
		return Optional.empty();
	}

	private ResponseCodeEnum validityOf(
			final CryptoGetAccountBalanceQuery op,
			final MerkleMap<EntityNum, MerkleAccount> accounts
	) {
		if (op.hasContractID()) {
			return optionValidator.queryableContractStatus(op.getContractID(), accounts);
		} else if (op.hasAccountID()) {
			final var effId = resolvedNonContract(op.getAccountID());
			return optionValidator.queryableAccountStatus(effId, accounts);
		} else {
			return INVALID_ACCOUNT_ID;
		}
	}

	private AccountID targetOf(CryptoGetAccountBalanceQuery op) {
		if (op.hasContractID()) {
			return asAccount(op.getContractID());
		} else {
			return resolvedNonContract(op.getAccountID());
		}
	}

	private AccountID resolvedNonContract(final AccountID idOrAlias) {
		if (isAlias(idOrAlias)) {
			final var id = aliasManager.lookupIdBy(idOrAlias.getAlias());
			return id.toGrpcAccountId();
		} else {
			return idOrAlias;
		}
	}

	@Override
	public ResponseCodeEnum extractValidityFrom(Response response) {
		return response.getCryptogetAccountBalance().getHeader().getNodeTransactionPrecheckCode();
	}

	@Override
	public HederaFunctionality canonicalFunction() {
		return CryptoGetAccountBalance;
	}
}
