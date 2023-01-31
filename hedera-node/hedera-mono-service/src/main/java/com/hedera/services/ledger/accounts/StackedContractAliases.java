/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.ledger.accounts;

import com.google.common.annotations.VisibleForTesting;
import com.hedera.services.evm.accounts.HederaEvmContractAliases;
import com.hedera.services.ledger.SigImpactHistorian;
import com.hedera.services.legacy.proto.utils.ByteStringUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hyperledger.besu.datatypes.Address;
import org.jetbrains.annotations.Nullable;

public class StackedContractAliases extends HederaEvmContractAliases implements ContractAliases {
    private static final Logger log = LogManager.getLogger(StackedContractAliases.class);

    private final ContractAliases wrappedAliases;

    private Set<Address> removedLinks = null;
    private Map<Address, Address> changedLinks = null;

    public StackedContractAliases(final ContractAliases wrappedAliases) {
        this.wrappedAliases = wrappedAliases;
    }

    public static StackedContractAliases wrapping(final ContractAliases aliases) {
        return new StackedContractAliases(aliases);
    }

    @Override
    public void revert() {
        removedLinks = null;
        changedLinks = null;
    }

    @Override
    public void filterPendingChanges(final Predicate<Address> filter) {
        if (changedLinks != null) {
            changedLinks().entrySet().removeIf(entry -> !filter.test(entry.getValue()));
        }
    }

    @Override
    public void commit(final @Nullable SigImpactHistorian observer) {
        if (removedLinks != null) {
            removedLinks.forEach(
                    alias -> {
                        wrappedAliases.unlink(alias);
                        if (observer != null) {
                            observer.markAliasChanged(
                                    ByteStringUtils.wrapUnsafely(alias.toArrayUnsafe()));
                        }
                        log.debug("Committing deletion of CREATE2 address {}", alias);
                    });
        }
        if (changedLinks != null) {
            changedLinks.forEach(
                    (alias, address) -> {
                        wrappedAliases.link(alias, address);
                        log.debug(
                                "Committing (re-)creation of CREATE2 address {} @ {}",
                                alias,
                                address);
                        if (observer != null) {
                            observer.markAliasChanged(
                                    ByteStringUtils.wrapUnsafely(alias.toArrayUnsafe()));
                        }
                    });
        }
    }

    @Override
    public void unlink(final Address alias) {
        if (isMirror(alias)) {
            throw new IllegalArgumentException("Cannot unlink mirror address " + alias);
        }
        if (isChanged(alias)) {
            changedLinks.remove(alias);
        }
        removedLinks().add(alias);
    }

    @Override
    public void link(final Address alias, final Address address) {
        if (isMirror(alias)) {
            throw new IllegalArgumentException(
                    "Cannot link mirror address " + alias + " to " + address);
        }
        if (!isMirror(address)) {
            throw new IllegalArgumentException(
                    "Cannot link alias " + alias + " to non-mirror address " + address);
        }
        if (isRemoved(alias)) {
            removedLinks.remove(alias);
        }
        changedLinks().put(alias, address);
    }

    @Override
    public Address resolveForEvm(final Address addressOrAlias) {
        if (isMirror(addressOrAlias)) {
            return addressOrAlias;
        }
        if (isChanged(addressOrAlias)) {
            return changedLinks.get(addressOrAlias);
        } else if (isRemoved(addressOrAlias)) {
            return null;
        } else {
            return wrappedAliases.resolveForEvm(addressOrAlias);
        }
    }

    @Override
    public boolean isInUse(final Address address) {
        if (isMirror(address)) {
            return false;
        }
        if (isChanged(address)) {
            return true;
        } else if (isRemoved(address)) {
            return false;
        } else {
            return wrappedAliases.isInUse(address);
        }
    }

    private boolean isChanged(final Address alias) {
        return changedLinks != null && changedLinks.containsKey(alias);
    }

    private boolean isRemoved(final Address alias) {
        return removedLinks != null && removedLinks.contains(alias);
    }

    @VisibleForTesting
    public ContractAliases wrappedAliases() {
        return wrappedAliases;
    }

    @VisibleForTesting
    Map<Address, Address> changedLinks() {
        if (changedLinks == null) {
            changedLinks = new HashMap<>();
        }
        return changedLinks;
    }

    @VisibleForTesting
    Set<Address> removedLinks() {
        if (removedLinks == null) {
            removedLinks = new HashSet<>();
        }
        return removedLinks;
    }
}
