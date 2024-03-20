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

package com.hedera.services.bdd.spec.infrastructure.providers.ops.hollow;

import static com.hedera.services.bdd.spec.keys.TrieSigMapGenerator.uniqueWithFullPrefixesFor;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.cryptoTransfer;
import static com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer.tinyBarsFromTo;
import static com.hederahashgraph.api.proto.java.ResponseCodeEnum.SUCCESS;

import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.infrastructure.HapiSpecRegistry;
import com.hedera.services.bdd.spec.infrastructure.providers.names.RegistrySourcedNameProvider;
import com.hedera.services.bdd.spec.transactions.HapiTxnOp;
import com.hedera.services.bdd.spec.transactions.crypto.HapiCryptoTransfer;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;

public class RandomTransferFromHollowAccount extends RandomOperationCustom<HapiCryptoTransfer> {
    private final ResponseCodeEnum[] permissibleOutcomes = standardOutcomesAnd(SUCCESS);
    private final String signer;

    public RandomTransferFromHollowAccount(
            HapiSpecRegistry registry, RegistrySourcedNameProvider<AccountID> accounts, String signer) {
        super(registry, accounts);
        this.signer = signer;
    }

    @Override
    protected HapiTxnOp<HapiCryptoTransfer> hapiTxnOp(String keyName) {
        return cryptoTransfer(tinyBarsFromTo(keyName, signer, 1));
    }

    protected HapiSpecOperation generateOpSignedBy(String keyName) {
        return hapiTxnOp(keyName)
                .signedBy(signer)
                .payingWith(signer)
                .sigMapPrefixes(uniqueWithFullPrefixesFor(signer))
                .hasPrecheckFrom(permissiblePrechecks)
                .hasKnownStatusFrom(permissibleOutcomes)
                .noLogging();
    }
}
