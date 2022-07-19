/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
 */
package com.hedera.services.txns.token;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.context.properties.GlobalDynamicProperties;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.ledger.ids.EntityIdSource;
import com.hedera.services.state.validation.UsageLimits;
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
public class CreateLogic {
    static final Creation.NewRelsListing RELS_LISTING = NewRels::listFrom;
    static final Creation.TokenModelFactory MODEL_FACTORY = Token::fromGrpcOpAndMeta;
    private Creation.CreationFactory creationFactory = Creation::new;

    private final UsageLimits usageLimits;
    private final AccountStore accountStore;
    private final TypedTokenStore tokenStore;
    private final GlobalDynamicProperties dynamicProperties;
    private final SigImpactHistorian sigImpactHistorian;
    private final EntityIdSource ids;
    private final OptionValidator validator;

    @Inject
    public CreateLogic(
            final UsageLimits usageLimits,
            final AccountStore accountStore,
            final TypedTokenStore tokenStore,
            final GlobalDynamicProperties dynamicProperties,
            final SigImpactHistorian sigImpactHistorian,
            final EntityIdSource entityIdSource,
            final OptionValidator validator) {
        this.usageLimits = usageLimits;
        this.accountStore = accountStore;
        this.tokenStore = tokenStore;
        this.dynamicProperties = dynamicProperties;
        this.sigImpactHistorian = sigImpactHistorian;
        this.ids = entityIdSource;
        this.validator = validator;
    }

    public void create(
            final long now, final AccountID activePayer, final TokenCreateTransactionBody op) {
        usageLimits.assertCreatableTokens(1);

        final var creation =
                creationFactory.processFrom(accountStore, tokenStore, dynamicProperties, op);

        // --- Create the model objects ---
        creation.loadModelsWith(activePayer, ids, validator);

        // --- Do the business logic ---
        creation.doProvisionallyWith(now, MODEL_FACTORY, RELS_LISTING);

        // --- Persist the created model ---
        creation.persist();

        // --- Externalize side-effects ---
        sigImpactHistorian.markEntityChanged(creation.newTokenId().num());
    }

    @VisibleForTesting
    public void setCreationFactory(final Creation.CreationFactory creationFactory) {
        this.creationFactory = creationFactory;
    }
}
