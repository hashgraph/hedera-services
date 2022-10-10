/*
 * Copyright (C) 2020-2022 Hedera Hashgraph, LLC
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
package com.hedera.services.store;

import com.hedera.services.ledger.HederaLedger;
import com.hedera.services.ledger.TransactionalLedger;
import com.hedera.services.ledger.properties.AccountProperty;
import com.hedera.services.state.merkle.MerkleAccount;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import java.util.function.Consumer;

/** Defines a generic type able to manage arbitrary entities. */
public interface Store<T, K> {
    K get(T id);

    boolean exists(T id);

    void apply(T id, Consumer<K> change);

    void setHederaLedger(HederaLedger ledger);

    void setAccountsLedger(
            TransactionalLedger<AccountID, AccountProperty, MerkleAccount> accountsLedger);

    void commitCreation();

    void rollbackCreation();

    boolean isCreationPending();

    T resolve(T id);

    ResponseCodeEnum delete(T id);
}
