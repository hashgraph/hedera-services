/*
 * Copyright (C) 2020-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl;

import static java.util.Objects.requireNonNull;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.HederaFunctionality;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.authorization.Authorizer;
import com.hedera.node.app.spi.authorization.SystemPrivilege;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * A Singleton implementation of signature waivers needed for transactions in {@link TokenService}. NOTE: FUTURE - These
 * will be implemented in the coming PR and this class should be a singleton.
 */
@Singleton
public class CryptoSignatureWaiversImpl implements CryptoSignatureWaivers {
    private final Authorizer authorizer;

    /**
     * Default constructor for injection.
     * @param authorizer the {@link Authorizer} to use for checking authorization
     */
    @Inject
    public CryptoSignatureWaiversImpl(@NonNull final Authorizer authorizer) {
        this.authorizer = requireNonNull(authorizer);
    }

    @Override
    public boolean isTargetAccountSignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        return authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.CRYPTO_UPDATE, cryptoUpdateTxn)
                == SystemPrivilege.AUTHORIZED;
    }

    @Override
    public boolean isNewKeySignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        final var isAuthorized =
                authorizer.hasPrivilegedAuthorization(payer, HederaFunctionality.CRYPTO_UPDATE, cryptoUpdateTxn)
                        == SystemPrivilege.AUTHORIZED;
        if (!isAuthorized) {
            return false;
        } else {
            final var targetNum = cryptoUpdateTxn.cryptoUpdateAccountOrThrow().accountIDToUpdateOrThrow();
            return !authorizer.isTreasury(targetNum);
        }
    }
}
