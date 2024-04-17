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

package com.hedera.services.bdd.junit.validators;

import com.google.protobuf.ByteString;
import com.hedera.node.app.service.mono.statedumpers.accounts.BBMHederaAccount;
import com.hedera.services.bdd.junit.RecordStreamValidator;
import com.hedera.services.bdd.junit.RecordWithSidecars;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.identityconnectors.common.CollectionUtil;

public class AccountAliasValidator implements RecordStreamValidator {

    @Override
    public void validateAccountAliases(Set<BBMHederaAccount> accountsFromState, List<RecordWithSidecars> records)
            throws InvocationTargetException, IllegalAccessException {

        Set<BBMHederaAccount> nonSystemAccounts = accountsFromState
                .stream()
                .filter(a -> a.accountId() != null && a.accountId().accountNum() > 1000L)
                .collect(Collectors.toSet());

        Set<ByteString> bytes = accountsFromState.stream().map(a -> a.alias()).filter(Objects::nonNull)
                .map(a -> ByteString.copyFrom(a.toByteArray()))
                .collect(Collectors.toSet());

        Set<ByteString> recordStreamBytes = new HashSet<>();
        for (final var recordWithSidecars : records) {
            final var items = recordWithSidecars.recordFile().getRecordStreamItemsList();
            for (final var item : items) {
                final var grpcRecord = item.getRecord();
                recordStreamBytes.add(grpcRecord.getEvmAddress());
                recordStreamBytes.add(grpcRecord.getAlias());
                recordStreamBytes.add(grpcRecord.getEthereumHash());
            }
        }
        // here if we debug we can see that recordStreamBytes has one empty record we probably don't want that
        if (CollectionUtil.isEmpty(bytes)) {
            System.out.println("Skip account alias validator since no accounts were read from state");
        }

        // do validations
        for (ByteString byteString : bytes) {
            if (!recordStreamBytes.contains(byteString)) {
                System.out.println("Missing alias in recordStream");
            }
        }
    }
}
