/*
 * Copyright (C) 2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.handlers.transfer;

import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.node.app.service.mono.utils.EntityIdUtils.EVM_ADDRESS_SIZE;
import static com.hedera.node.app.service.token.impl.handlers.BaseCryptoHandler.asAccount;
import static com.hedera.node.app.service.token.impl.handlers.transfer.Utils.isSerializedProtoKey;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.node.app.service.token.impl.WritableAccountStore;
import com.hedera.node.app.service.token.impl.WritableTokenStore;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.config.data.AutoCreationConfig;
import com.hedera.node.config.data.LazyCreationConfig;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.HashMap;
import java.util.Map;

public class TransferContextImpl implements TransferContext {
    private WritableAccountStore accountStore;
    private WritableTokenStore tokenStore;
    private AutoAccountCreator autoAccountCreator;
    private HandleContext context;
    private int numAutoCreations;
    private int numLazyCreations;
    private Map<Bytes, AccountID> resolutions = new HashMap<>();

    public TransferContextImpl(final HandleContext context) {
        this.accountStore = context.writableStore(WritableAccountStore.class);
        this.tokenStore = context.writableStore(WritableTokenStore.class);
        this.context = context;
        accountStore = context.writableStore(WritableAccountStore.class);
        tokenStore = context.writableStore(WritableTokenStore.class);
        this.autoAccountCreator = new AutoAccountCreator(accountStore, context);
    }

    @Override
    public AccountID getFromAlias(final AccountID aliasedId) {
        final var account = accountStore.get(aliasedId);

        if (account != null) {
            final var id = asAccount(account.accountNumber());
            resolutions.put(aliasedId.alias(), id);
            return id;
        }
        return null;
    }

    @Override
    public void createFromAlias(Bytes alias, boolean isFromTokenTransfer) {
        if (isSerializedProtoKey(alias)) {
            autoAccountCreator.create(alias, isFromTokenTransfer);
            resolutions.put(alias, AccountID.DEFAULT);
            numAutoCreations++;
        } else if (isOfEvmAddressSize(alias)) {
            numLazyCreations++;
        }
    }

    @Override
    public void debitHbarViaApproval(final AccountID owner, final long amount) {}

    @Override
    public int numOfAutoCreations() {
        return numAutoCreations;
    }

    @Override
    public void chargeExtraFeeToHapiPayer(final long amount) {}

    @Override
    public void chargeCustomFeeTo(final AccountID payer, final long amount, final TokenID denomination) {}

    public Map<Bytes, AccountID> resolutions() {
        return resolutions;
    }

    @Override
    public int numOfLazyCreations() {
        return numLazyCreations;
    }

    public static boolean isOfEvmAddressSize(final Bytes alias) {
        return alias.length() == EVM_ADDRESS_SIZE;
    }
}
