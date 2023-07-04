/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.junit;

import static com.hedera.node.app.service.evm.accounts.HederaEvmContractAliases.EVM_ADDRESS_LEN;
import static com.hedera.node.app.service.mono.ledger.accounts.AliasManager.keyAliasToEVMAddress;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.Streams;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.hapi.utils.ByteStringUtils;
import com.hedera.node.app.hapi.utils.CommonUtils;
import com.hedera.services.bdd.suites.freeze.SimpleFreezeOnly;
import com.hedera.services.cli.signedstate.SignedStateHolder;
import com.hederahashgraph.api.proto.java.ResponseCodeEnum;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class AccountAliasesValidator implements RecordStreamValidator {
    private final Map<ByteString, Long> aliasesFromRecords = new HashMap<>();
    private final Map<ByteString, Long> aliasesFromState = new HashMap<>();

    @Override
    @SuppressWarnings("java:S4507")
    public void validateRecordsAndSidecars(@NonNull final List<RecordWithSidecars> recordsWithSidecars) {
        requireNonNull(recordsWithSidecars);
        getExpectedAliasesFrom(recordsWithSidecars);

        new SimpleFreezeOnly().runSuiteSync(); // freeze the network before reading the signed state file
        try (Stream<Path> paths =
                Files.walk(Paths.get("build/network/itest/saved/node_0/com.hedera.node.app.ServicesMain/0/hedera"))) {
            var signedStateFile = Streams.findLast(
                            paths.filter(p -> p.endsWith("SignedState.swh")).sorted())
                    .orElseThrow(); // get the latest signed state file
            getActualAliasesFrom(signedStateFile);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (!aliasesFromRecords.equals(aliasesFromState)) {
            throw new IllegalStateException("Expected " + aliasesFromRecords.size() + " aliases from records, but got "
                    + aliasesFromState.size() + "from state");
        }
    }

    private void getExpectedAliasesFrom(final List<RecordWithSidecars> recordsWithSidecars) {
        for (final var recordWithSidecars : recordsWithSidecars) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                if (item.getRecord().getReceipt().getStatus() != ResponseCodeEnum.SUCCESS) {
                    continue;
                }

                try {
                    final var txn = CommonUtils.extractTransactionBody(item.getTransaction());
                    var alias = ByteString.EMPTY;
                    if (txn.hasCryptoCreateAccount()) {
                        alias = txn.getCryptoCreateAccount().getAlias();
                    }

                    var accountNum =
                            item.getRecord().getReceipt().getAccountID().getAccountNum();
                    if (!alias.isEmpty()) {
                        aliasesFromRecords.put(alias, accountNum);
                    }

                    var evmAddressAlias = item.getRecord().getEvmAddress();
                    if (!evmAddressAlias.isEmpty()) {
                        aliasesFromRecords.put(evmAddressAlias, accountNum);
                    }
                } catch (final InvalidProtocolBufferException e) {
                    throw new IllegalStateException(e);
                }
            }
        }
    }

    private void getActualAliasesFrom(final Path signedStateFile) throws Exception {
        var state = new SignedStateHolder(signedStateFile).getPlatformState();
        state.accounts().forEach((k, v) -> {
            final var alias = v.getAlias();
            if (!alias.isEmpty()) {
                aliasesFromState.put(alias, k.longValue());
                if (alias.size() > EVM_ADDRESS_LEN) {
                    final var evmAddress = keyAliasToEVMAddress(alias);
                    if (evmAddress != null) {
                        aliasesFromState.put(ByteStringUtils.wrapUnsafely(evmAddress), k.longValue());
                    }
                }
            }
        });
    }
}
