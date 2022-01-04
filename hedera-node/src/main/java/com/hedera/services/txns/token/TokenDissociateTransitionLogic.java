package com.hedera.services.txns.token;

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

import com.hedera.services.context.TransactionContext;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Id;
import com.hedera.services.store.models.TokenRelationship;
import com.hedera.services.txns.TransitionLogic;
import com.hedera.services.txns.token.process.Dissociation;
import com.hedera.services.txns.token.process.DissociationFactory;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import com.hederahashgraph.api.proto.java.TokenDissociateTransactionBody;
import com.hederahashgraph.api.proto.java.TransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import static com.hedera.services.txns.validation.TokenListChecks.repeatsItself;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.OK;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.TOKEN_ID_REPEATED_IN_TOKEN_LIST;

/**
 * Provides the state transition for dissociating tokens from an account.
 */
@Singleton
public class TokenDissociateTransitionLogic implements TransitionLogic {
	private final Function<TransactionBody, ResponseCodeEnum> SEMANTIC_CHECK = this::validate;

	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final TransactionContext txnCtx;
	private final OptionValidator validator;
	private final DissociationFactory dissociationFactory;

	@Inject
	public TokenDissociateTransitionLogic(
			TypedTokenStore tokenStore,
			AccountStore accountStore,
			TransactionContext txnCtx,
			OptionValidator validator,
			DissociationFactory dissociationFactory
	) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.txnCtx = txnCtx;
		this.validator = validator;
		this.dissociationFactory = dissociationFactory;
	}

	@Override
	public void doStateTransition() {
		/* --- Translate from gRPC types --- */
		var op = txnCtx.accessor().getTxn().getTokenDissociate();
		final var grpcId = op.getAccount();
		final var accountNum = accountStore.getAccountNumFromAlias(grpcId.getAlias(), grpcId.getAccountNum());;
		final var accountId = new Id(grpcId.getShardNum(), grpcId.getRealmNum(), accountNum);

		/* --- Load the model objects --- */
		final var account = accountStore.loadAccount(accountId);
		final List<Dissociation> dissociations = new ArrayList<>();
		for (var tokenId : op.getTokensList()) {
			dissociations.add(dissociationFactory.loadFrom(tokenStore, account, Id.fromGrpcToken(tokenId)));
		}

		/* --- Do the business logic --- */
		account.dissociateUsing(dissociations, validator);

		/* --- Persist the updated models --- */
		accountStore.persistAccount(account);
		final List<TokenRelationship> allUpdatedRels = new ArrayList<>();
		for (var dissociation : dissociations) {
			dissociation.addUpdatedModelRelsTo(allUpdatedRels);
		}
		tokenStore.persistTokenRelationships(allUpdatedRels);
	}

	@Override
	public Predicate<TransactionBody> applicability() {
		return TransactionBody::hasTokenDissociate;
	}

	@Override
	public Function<TransactionBody, ResponseCodeEnum> semanticCheck() {
		return SEMANTIC_CHECK;
	}

	public ResponseCodeEnum validate(TransactionBody txnBody) {
		TokenDissociateTransactionBody op = txnBody.getTokenDissociate();

		if (!op.hasAccount()) {
			return INVALID_ACCOUNT_ID;
		}

		if (repeatsItself(op.getTokensList())) {
			return TOKEN_ID_REPEATED_IN_TOKEN_LIST;
		}

		return OK;
	}
}