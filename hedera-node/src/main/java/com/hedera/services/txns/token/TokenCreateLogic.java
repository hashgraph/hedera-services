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

import com.hedera.services.context.SideEffectsTracker;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.store.AccountStore;
import com.hedera.services.store.TypedTokenStore;
import com.hedera.services.store.models.Token;
import com.hedera.services.txns.token.process.Creation;
import com.hedera.services.txns.token.process.NewRels;
import com.hedera.services.txns.validation.OptionValidator;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TokenCreateTransactionBody;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class TokenCreateLogic {
	static final Creation.NewRelsListing RELS_LISTING = NewRels::listFrom;
	static final Creation.TokenModelFactory MODEL_FACTORY = Token::fromGrpcOpAndMeta;
	private Creation.CreationFactory creationFactory = Creation::new;

	private final AccountStore accountStore;
	private final TypedTokenStore tokenStore;
	private final GlobalDynamicProperties dynamicProperties;
	private final SigImpactHistorian sigImpactHistorian;
	private final SideEffectsTracker sideEffectsTracker;
	private final EntityIdSource ids;
	private final OptionValidator validator;

	@Inject
	public TokenCreateLogic(
			AccountStore accountStore,
			TypedTokenStore tokenStore,
			GlobalDynamicProperties dynamicProperties,
			SigImpactHistorian sigImpactHistorian,
			SideEffectsTracker sideEffectsTracker,
			EntityIdSource entityIdSource,
			OptionValidator validator) {
		this.accountStore = accountStore;
		this.tokenStore = tokenStore;
		this.dynamicProperties = dynamicProperties;
		this.sigImpactHistorian = sigImpactHistorian;
		this.sideEffectsTracker = sideEffectsTracker;
		this.ids = entityIdSource;
		this.validator = validator;
	}

	public void create(long now, AccountID activePayer, TokenCreateTransactionBody op) {
		final var creation = creationFactory.processFrom(accountStore, tokenStore, dynamicProperties, op);

		/* --- Create the model objects --- */
		creation.loadModelsWith(activePayer, ids, validator);

		/* --- Do the business logic --- */
		creation.doProvisionallyWith(now, MODEL_FACTORY, RELS_LISTING);

		/* --- Persist the created model --- */
		creation.persist();

		creation.newAssociations().forEach(sideEffectsTracker::trackExplicitAutoAssociation);
		sigImpactHistorian.markEntityChanged(creation.newTokenId().num());
	}

	public void create(long now, AccountID activePayer) {
		// TODO: Come up with a way to pass/build a synthetic TokenCreateTransactionBody to the overloaded method
		//  from the EVM Precompiled Contract logical path
		this.create(now, activePayer, null);
	}

	// Only used in unit-tests
	public void setCreationFactory(Creation.CreationFactory creationFactory) {
		this.creationFactory = creationFactory;
	}
}
