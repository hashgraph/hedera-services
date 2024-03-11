/*
 * Copyright (C) 2022-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.mono.txns.crypto;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.isMirror;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.context.TransactionContext;
import com.hedera.node.app.service.mono.context.primitives.StateView;
import com.hedera.node.app.service.mono.context.properties.GlobalDynamicProperties;
import com.hedera.node.app.service.mono.ledger.accounts.ContractAliases;
import com.hedera.node.app.service.mono.ledger.ids.EntityIdSource;
import com.hedera.node.app.service.mono.state.EntityCreator;
import com.hedera.node.app.service.mono.state.submerkle.ExpirableTxnRecord;
import com.hedera.node.app.service.mono.state.validation.UsageLimits;
import com.hedera.node.app.service.mono.store.contracts.precompile.SyntheticTxnFactory;
import com.hedera.node.app.service.mono.utils.EntityIdUtils;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.TransactionBody.Builder;
import java.util.function.Supplier;
import org.apache.tuweni.bytes.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class EvmAutoCreationLogic extends AbstractAutoCreationLogic {

    private final ContractAliases contractAliases;

    public EvmAutoCreationLogic(
            final UsageLimits usageLimits,
            final SyntheticTxnFactory syntheticTxnFactory,
            final EntityCreator creator,
            final EntityIdSource ids,
            final Supplier<StateView> currentView,
            final TransactionContext txnCtx,
            final GlobalDynamicProperties properties,
            final ContractAliases contractAliases) {
        super(usageLimits, syntheticTxnFactory, creator, ids, currentView, txnCtx, properties);
        this.contractAliases = contractAliases;
    }

    @Override
    protected void trackAlias(final ByteString alias, final AccountID newId) {
        if (alias.size() != EVM_ADDRESS_LEN) {
            throw new UnsupportedOperationException("Stacked alias manager cannot link aliases with size != 20.");
        }
        if (isMirror(alias.toByteArray())) {
            throw new IllegalArgumentException("Cannot link a long-zero address as an alias");
        }
        contractAliases.link(Address.wrap(Bytes.of(alias.toByteArray())), EntityIdUtils.asTypedEvmAddress(newId));
    }

    @Override
    public boolean reclaimPendingAliases() {
        throw new IllegalStateException("Aliases should not be reclaimed through AutoCreationLogic in the EVM!");
    }

    @Override
    protected void trackSigImpactIfNeeded(
            final Builder syntheticCreation, final ExpirableTxnRecord.Builder childRecord) {
        // no-op --- sig impact in the EVM must be tracked outside the AutoCreationLogic
    }
}
