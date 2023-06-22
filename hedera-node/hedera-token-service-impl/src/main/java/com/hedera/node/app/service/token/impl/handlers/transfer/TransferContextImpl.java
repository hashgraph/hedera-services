/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.hedera.node.app.service.token.impl.handlers.transfer;

import com.google.protobuf.ByteString;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.mono.utils.EntityNum;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_ACCOUNT_ID;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

public class TransferContextImpl implements TransferContext {
    private  WritableAccountStore accountStore;
    private WritableTokenStore tokenStore;
    private AutoAccountCreator autoAccountCreator;
    private HandleContext context;
    private int numAutoCreations;
    private int numLazyCreations;
    private Map<Bytes, AccountID> resolutions = new HashMap<>();

    /* ---- temporary token transfer resolutions map containing the token transfers to alias, is needed to check if
    an alias is repeated. It is allowed to be repeated in multiple token transfer lists, but not in a single
    token transfer list ---- */
    private final Map<ByteString, EntityNum> tokenTransferResolutions = new HashMap<>();
    public TransferContextImpl(final HandleContext context) {
        this.accountStore = context.writableStore(WritableAccountStore.class);
        this.tokenStore = context.writableStore(WritableTokenStore.class);
        this.context = context;
        accountStore = context.writableStore(WritableAccountStore.class);
        tokenStore = context.writableStore(WritableTokenStore.class);
        this.autoAccountCreator = new AutoAccountCreator(accountStore, context);
    }
    @Override
    public void chargeExtraFeeToHapiPayer(final long amount) {

    }

    @Override
    public void chargeCustomFeeTo(final AccountID payer, final long amount, final TokenID denomination) {

    }

    @Override
    public AccountID getFromAlias(final AccountID aliasedId) {
       final var account = accountStore.get(aliasedId);
       validateTrue(account != null, INVALID_ACCOUNT_ID);

       final var id = asAccount(account.accountNumber());
       resolutions.put(aliasedId.alias(), id);
       return id;
    }

    @Override
    public AccountID getOrCreateFromAlias(final AccountID aliasedId, boolean isForToken) {
        final var account = accountStore.get(aliasedId);
        if (account != null) {
            final var id = asAccount(account.accountNumber());
            resolutions.put(aliasedId.alias(), id);
            return id;
        } else {
            if (resolutions.containsKey(aliasedId.alias())) {
                return resolutions.get(aliasedId.alias());
            }
            // TODO: do we need to allow repeated aliases check?
            final var id = autoAccountCreator.create(isForToken, aliasedId.alias());
            resolutions.put(aliasedId.alias(), id);
        }
    }

    private void createFromAlias(Bytes alias) {

    }

    @Override
    public void debitHbarViaApproval(final AccountID owner, final long amount) {

    }

    public int getNumAutoCreations() {
        return numAutoCreations;
    }

    public void setNumAutoCreations(final int numAutoCreations) {
        this.numAutoCreations = numAutoCreations;
    }

    public int getNumLazyCreations() {
        return numLazyCreations;
    }

    public void setNumLazyCreations(final int numLazyCreations) {
        this.numLazyCreations = numLazyCreations;
    }

    public Map<Bytes, AccountID> getResolutions() {
        return resolutions;
    }

    public void setResolutions(final Map<Bytes, AccountID> resolutions) {
        this.resolutions = resolutions;
    }
}
