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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.setunlimitedautoassociations;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.token.CryptoUpdateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.has.HasCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.config.data.ContractsConfig;
import edu.umd.cs.findbugs.annotations.NonNull;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class SetUnlimitedAutoAssociationsTranslator extends AbstractCallTranslator<HasCallAttempt> {

    public static final Function SET_UNLIMITED_AUTO_ASSOC =
            new Function("setUnlimitedAutomaticAssociations(bool)", ReturnTypes.INT_64);

    private static final int UNLIMITED_AUTO_ASSOCIATIONS = -1;
    private static final int NO_AUTO_ASSOCIATIONS = 0;

    @Inject
    public SetUnlimitedAutoAssociationsTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HasCallAttempt attempt) {
        final var setUnlimitedAutoAssocEnabled = attempt.configuration()
                .getConfigData(ContractsConfig.class)
                .systemContractSetUnlimitedAutoAssociationsEnabled();
        return attempt.isSelectorIfConfigEnabled(setUnlimitedAutoAssocEnabled, SET_UNLIMITED_AUTO_ASSOC);
    }

    @Override
    public Call callFrom(@NonNull final HasCallAttempt attempt) {
        requireNonNull(attempt);
        final var call = SET_UNLIMITED_AUTO_ASSOC.decodeCall(attempt.inputBytes());
        final var setUnlimitedAutoAssociations = (boolean) call.get(0);
        return new SetUnlimitedAutoAssociationsCall(attempt, bodyFor(attempt, setUnlimitedAutoAssociations));
    }

    @NonNull
    private TransactionBody bodyFor(@NonNull final HasCallAttempt attempt, final boolean setUnlimitedAutoAssociations) {
        final var cryptoUpdate = CryptoUpdateTransactionBody.newBuilder()
                .accountIDToUpdate(attempt.redirectAccountId())
                .maxAutomaticTokenAssociations(
                        setUnlimitedAutoAssociations ? UNLIMITED_AUTO_ASSOCIATIONS : NO_AUTO_ASSOCIATIONS)
                .build();
        return TransactionBody.newBuilder().cryptoUpdateAccount(cryptoUpdate).build();
    }
}
