/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.token.CryptoSignatureWaivers;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.spi.numbers.HederaAccountNumbers;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.apache.commons.lang3.NotImplementedException;

/**
 * A Singleton implementation of signature waivers needed for transactions in {@link TokenService}.
 * NOTE: FUTURE - These will be implemented in the coming PR and this class should be a singleton.
 */
public class CryptoSignatureWaiversImpl implements CryptoSignatureWaivers {
    public CryptoSignatureWaiversImpl(@NonNull final HederaAccountNumbers accountNumbers) {
        // Exists for injection
        requireNonNull(accountNumbers);
    }

    @Override
    public boolean isTargetAccountSignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        throw new NotImplementedException();
    }

    @Override
    public boolean isNewKeySignatureWaived(final TransactionBody cryptoUpdateTxn, final AccountID payer) {
        throw new NotImplementedException();
    }
}
