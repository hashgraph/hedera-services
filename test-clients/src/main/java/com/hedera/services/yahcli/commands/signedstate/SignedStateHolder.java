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
import com.swirlds.platform.state.signed.SignedStateFileReader;
import com.swirlds.virtualmap.VirtualMap;
import java.io.Closeable;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

public class SignedStateHolder implements Closeable {

    @NotNull private final Path swh;
    @NotNull private ServicesState platformState;

    public SignedStateHolder(@NotNull Path swhFile) throws Exception {
        // TODO: Further validity checks on swhfile, e.g., existence
        swh = swhFile;
        dehydrate();
    }

    private void dehydrate() throws Exception {
        // register all applicable classes on classpath before deserializing signed state
        ConstructableRegistry.getInstance().registerConstructables("*");
        platformState =
                (ServicesState)
                        (SignedStateFileReader.readStateFile(swh).signedState().getSwirldState());
        AssertSignedStateComponentExists(platformState, "platform state (Swirlds)");
    }

    @Contract(pure = true)
    public @NotNull ServicesState getPlatformState() {
        return platformState;
    }

    @Contract(pure = true)
    public @NotNull AccountStorageAdapter getAccounts() {
        final var accounts = platformState.accounts();
        AssertSignedStateComponentExists(accounts, "accounts");
        return accounts;
    }


    @Contract(pure = true)
    public @NotNull VirtualMap<VirtualBlobKey, VirtualBlobValue> getFileStore() {
        final var fileStore = platformState.storage();
        AssertSignedStateComponentExists(fileStore, "fileStore");
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
            final VirtualBlobKey vbk = new VirtualBlobKey(Type.CONTRACT_BYTECODE, cid.intValue());
            if (fileStore.containsKey(vbk)) {
                var blob = fileStore.get(vbk);
                if (null != blob) codes.put(cid, blob.getData());
            }
        }
        return codes;
    }

    // (Class doesn't own any true resources, thus doesn't _need_ to be `Closeable` ... but it
    // _does_ hold a ginormous chunk of memory.  So it is `Closeable` just to remind the user
    // that it is expensive and should be released as soon as it is no longer needed.)
    @Override
    public void close() throws IOException {
        platformState = null;
        System.gc(); // hint to JVM
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
    private void AssertSignedStateComponentExists(Object component, @NotNull String componentName) {
        if (null == component) throw new MissingSignedStateComponent(componentName, swh);
    }
}
