/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.token.impl.validators;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.spi.workflows.PreCheckException.validateTruePreCheck;

import com.hedera.hapi.node.base.AccountAmount;
import com.hedera.hapi.node.token.TokenAirdropTransactionBody;
import com.hedera.node.app.spi.workflows.PreCheckException;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.List;

@Singleton
public class TokenAirdropValidator extends CryptoTransferValidator {
    private static final int MAX_TOKEN_TRANSFERS = 10;

    /**
     * Default constructor for injection.
     */
    @Inject
    public TokenAirdropValidator() {
        // For Dagger injection
    }

    /**
     * Performs pure checks that validates basic fields in the token airdrop transaction.
     * @param op the token airdrop transaction body
     * @throws PreCheckException if any of the checks fail
     */
    public void pureChecks(@NonNull final TokenAirdropTransactionBody op) throws PreCheckException {
        final var tokenTransfers = op.tokenTransfers();
        validateTruePreCheck(tokenTransfers.size() <= MAX_TOKEN_TRANSFERS, INVALID_TRANSACTION_BODY);
        // If there is more than one negative transfer we throw an exception
        for (var tokenTransfer : tokenTransfers) {
            List<AccountAmount> negativeTransfers = tokenTransfer.transfers()
                    .stream()
                    .filter(fungibleTransfer -> fungibleTransfer.amount() < 0)
                    .toList();

            if (negativeTransfers.size() > 1) {
                throw new PreCheckException(INVALID_TRANSACTION_BODY);
            }
        }
        validateTokenTransfers(op.tokenTransfers());
    }
}
