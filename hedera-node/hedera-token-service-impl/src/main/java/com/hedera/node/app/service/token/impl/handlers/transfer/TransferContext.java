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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.util.Map;

public interface TransferContext {
    // Throw if the fee cannot be charged for whatever reason
    void chargeExtraFeeToHapiPayer(long amount);

    void chargeCustomFeeTo(AccountID payer, long amount, TokenID denomination);

    AccountID getFromAlias(AccountID aliasedId);

    void createFromAlias(Bytes alias, boolean isFromTokenTransfer);

    // Debit an account based on the HAPI payer having an approved allowance from the given owner
    default void debitHbarViaApproval(AccountID owner, long amount) {}

    int numOfAutoCreations();

    Map<Bytes, AccountID> resolutions();

    int numOfLazyCreations();
}
