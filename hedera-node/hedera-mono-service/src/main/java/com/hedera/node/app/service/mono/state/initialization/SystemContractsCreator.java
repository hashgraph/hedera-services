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

package com.hedera.node.app.service.mono.state.initialization;

import static com.hedera.node.app.service.mono.context.properties.StaticPropertiesHolder.STATIC_PROPERTIES;
import static com.hedera.node.app.service.mono.txns.contract.ContractCreateTransitionLogic.STANDIN_CONTRACT_ID_KEY;
import static com.hedera.node.app.spi.config.PropertyNames.BOOTSTRAP_SYSTEM_ENTITY_EXPIRY;
import static com.hedera.node.app.spi.config.PropertyNames.CONTRACTS_SYSTEM_CONTRACTS;

import com.hedera.node.app.service.mono.config.HederaNumbers;
import com.hedera.node.app.service.mono.context.annotations.CompositeProps;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.hedera.node.app.service.mono.ledger.accounts.HederaAccountCustomizer;
import com.hedera.node.app.service.mono.ledger.backing.BackingStore;
import com.hedera.node.app.service.mono.state.migration.HederaAccount;
import com.hedera.node.app.service.mono.store.contracts.EntityAccess;
import com.hedera.node.app.service.mono.store.contracts.precompile.ExchangeRatePrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.HTSPrecompiledContract;
import com.hedera.node.app.service.mono.store.contracts.precompile.PrngSystemPrecompiledContract;
import com.hedera.node.app.spi.config.PropertyNames;
import com.hederahashgraph.api.proto.java.AccountID;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.tuweni.bytes.Bytes;
import org.bouncycastle.util.encoders.Hex;

/**
 * Encapsulates all the logic for the creation of the enabled system precompiled contracts.
 *
 * <p>As of release 0.37.0, there are 3 such contracts:
 * <ul>
 *     <li>{@link HTSPrecompiledContract}</li>
 *     <li>{@link ExchangeRatePrecompiledContract}</li>
 *     <li>{@link PrngSystemPrecompiledContract}</li>
 * </ul>
 */
@Singleton
public class SystemContractsCreator {

    public static final Bytes SYSTEM_CONTRACT_BYTECODE =
            Bytes.concatenate(Bytes.of(Hex.decode("FE00FF")), Bytes.of("SYSTEMCONTRACT".getBytes()));
    public static final String SYSTEM_CONTRACT_MEMO = "System contract";

    private final Supplier<HederaAccount> accountSupplier;
    private final BackingStore<AccountID, HederaAccount> accounts;
    private final List<HederaAccount> contractsCreated = new ArrayList<>();
    private final PropertySource properties;
    private final EntityAccess entityAccess;

    @Inject
    public SystemContractsCreator(
            final Supplier<HederaAccount> accountSupplier,
            final BackingStore<AccountID, HederaAccount> accounts,
            final @CompositeProps PropertySource properties,
            final EntityAccess entityAccess) {
        this.accountSupplier = accountSupplier;
        this.accounts = accounts;
        this.properties = properties;
        this.entityAccess = entityAccess;
    }

    /**
     * Makes sure that all system contracts, marked as enabled in the {@link PropertyNames#CONTRACTS_SYSTEM_CONTRACTS}
     * property are present in state, and creates them if necessary.
     */
    public void ensureSystemContractsExist() {
        final var expiry = properties.getLongProperty(BOOTSTRAP_SYSTEM_ENTITY_EXPIRY);
        final var activeSystemContracts = properties.getAccountNums(CONTRACTS_SYSTEM_CONTRACTS);
        for (long num = HederaNumbers.FIRST_RESERVED_SYSTEM_CONTRACT;
                num <= HederaNumbers.LAST_RESERVED_SYSTEM_CONTRACT;
                num++) {
            final var id = STATIC_PROPERTIES.scopedAccountWith(num);
            if (activeSystemContracts.contains(id.getAccountNum())) {
                if (accounts.contains(id)) {
                    continue;
                }
                final var htsPrecompiledContract = new HederaAccountCustomizer()
                        .isSmartContract(true)
                        .isReceiverSigRequired(true)
                        .isDeclinedReward(true)
                        .isDeleted(false)
                        .expiry(expiry)
                        .memo(SYSTEM_CONTRACT_MEMO)
                        .key(STANDIN_CONTRACT_ID_KEY)
                        .autoRenewPeriod(expiry)
                        .customizing(accountSupplier.get());
                entityAccess.storeCode(id, SYSTEM_CONTRACT_BYTECODE);
                accounts.put(id, htsPrecompiledContract);
                contractsCreated.add(htsPrecompiledContract);
            }
        }
    }

    /**
     * Returns a list of {@link HederaAccount}, denoting all of the system contracts created
     * by a previous call to {@link SystemContractsCreator#ensureSystemContractsExist()}
     *
     * @return a list of all the created system contracts
     */
    public List<HederaAccount> getContractsCreated() {
        return contractsCreated;
    }

    /**
     * Clears the list of created contracts by a previous call to {@link SystemContractsCreator#ensureSystemContractsExist()}
     */
    public void forgetCreatedContracts() {
        contractsCreated.clear();
    }
}
