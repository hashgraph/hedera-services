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
package com.hedera.services.yahcli.commands.signedstate;

import com.hedera.services.ServicesState;
import com.hedera.services.state.migration.AccountStorageAdapter;
import com.hedera.services.state.virtual.VirtualBlobKey;
import com.hedera.services.state.virtual.VirtualBlobKey.Type;
import com.hedera.services.state.virtual.VirtualBlobValue;
import com.hedera.services.utils.EntityNum;
import com.swirlds.common.constructable.ConstructableRegistry;
import com.swirlds.common.constructable.ConstructableRegistryException;
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.virtualmap.VirtualMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class SignedStateHolder {

    @NotNull private final Path swh;
    private ServicesState platformState;

    public SignedStateHolder(@NotNull Path swhFile) throws SignedStateDehydrationException {
        // perhaps to do later: Further validity checks on swhfile, e.g., existence
        swh = swhFile;
        dehydrate();
    }

    public static class SignedStateDehydrationException extends Exception {
        SignedStateDehydrationException(Throwable t) {
            super("Failed to read signed state file", t);
        }
    }

    private void dehydrate() throws SignedStateDehydrationException {
        // register all applicable classes on classpath before deserializing signed state
        try {
            ConstructableRegistry.getInstance().registerConstructables("*");
            platformState =
                    (ServicesState)
                            (SignedStateFileReader.readStateFile(swh)
                                    .signedState()
                                    .getSwirldState());
        } catch (IOException | ConstructableRegistryException ex) {
            throw new SignedStateDehydrationException(ex);
        }
        assertSignedStateComponentExists(platformState, "platform state (Swirlds)");
    }

    @Contract(pure = true)
    public @NotNull ServicesState getPlatformState() {
        return platformState;
    }

    @Contract(pure = true)
    public @NotNull AccountStorageAdapter getAccounts() {
        final var accounts = platformState.accounts();
        assertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }

    @Contract(pure = true)
    public @NotNull VirtualMap<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = platformState.storage();
        assertSignedStateComponentExists(fileStore, "fileStore");
        return fileStore;
    }

    // returns all contracts known via Hedera accounts

    @Contract(pure = true)
    public @NotNull Set<EntityNum> getAllKnownContracts() {
        Set<EntityNum> ids = new HashSet<>();
        getAccounts()
                .forEach(
                        (k, v) -> {
                            if (v.isSmartContract()) ids.add(k);
                        });
        return ids;
    }

    @Contract(pure = true)
    public @NotNull Map<EntityNum, byte[]> getAllContractContents(
            @NotNull Collection<EntityNum> contractIds) {
        Map<EntityNum, byte[]> codes = new HashMap<>();
        final var fileStore = getFileStore();
        for (var cid : contractIds) {
            var blob = getContractById(fileStore, cid);
            blob.ifPresent(bytes -> codes.put(cid, bytes));
        }
        return codes;
    }

    public Optional<byte []> getContractById(@NotNull VirtualMap<VirtualBlobKey,VirtualBlobValue> fileStore,  @NotNull EntityNum contractId) {
        final var vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, contractId.intValue());
        if (fileStore.containsKey(vbk)) {
            var blob = fileStore.get(vbk);
            if (null != blob) return Optional.ofNullable(blob.getData());
        }
        return Optional.empty();
    }

    public static class MissingSignedStateComponent extends NullPointerException {
        public MissingSignedStateComponent(@NotNull String component, @NotNull Path swh) {
            super(
                    String.format(
                            "Expected non-null %s from signed state file %s",
                            component, swh.toString()));
        }
    }

    @Contract(pure = true)
    private void assertSignedStateComponentExists(Object component, @NotNull String componentName) {
        if (null == component) throw new MissingSignedStateComponent(componentName, swh);
    }
}
