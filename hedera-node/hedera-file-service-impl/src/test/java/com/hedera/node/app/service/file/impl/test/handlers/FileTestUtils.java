/*
 * Copyright (C) 2023-2024 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.file.impl.test.handlers;

import static com.hedera.test.factories.scenarios.TxnHandlingScenario.CUSTOM_PAYER_ACCOUNT_KT;
import static com.hedera.test.factories.txns.SignedTxnFactory.DEFAULT_PAYER_KT;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.service.file.FileMetadata;
import com.hedera.node.app.service.file.impl.ReadableFileStoreImpl;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.key.HederaKey;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.app.spi.workflows.PreHandleContext;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.assertj.core.api.Assertions;

public final class FileTestUtils {

    static final Key SIMPLE_KEY_A = Key.newBuilder()
            .ed25519(Bytes.wrap("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa".getBytes()))
            .build();
    static final Key SIMPLE_KEY_B = Key.newBuilder()
            .ed25519(Bytes.wrap("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb".getBytes()))
            .build();
    static final HederaKey A_NONNULL_KEY = () -> false;

    static final AccountID ACCOUNT_ID_4 = AccountID.newBuilder().accountNum(4L).build();

    public static final AccountID PARITY_DEFAULT_PAYER =
            AccountID.newBuilder().accountNum(13257).build();

    private FileTestUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    static Key mockPayerLookup(Key key, AccountID accountId, ReadableAccountStore accountStore)
            throws PreCheckException {
        final var account = mock(Account.class);
        lenient().when(accountStore.getAccountById(accountId)).thenReturn(account);
        lenient().when(account.key()).thenReturn(key);
        return key;
    }

    static void assertDefaultPayer(PreHandleContext context) {
        assertPayer(DEFAULT_PAYER_KT.asPbjKey(), context);
    }

    static void assertCustomPayer(PreHandleContext context) {
        assertPayer(CUSTOM_PAYER_ACCOUNT_KT.asPbjKey(), context);
    }

    static void assertPayer(Key expected, PreHandleContext context) {
        Assertions.assertThat(context.payerKey()).isEqualTo(expected);
    }

    static void mockFileLookup(KeyList keys, ReadableFileStoreImpl fileStore) {
        given(fileStore.getFileMetadata(notNull())).willReturn(newFileMeta(keys));
    }

    static FileMetadata newFileMeta(KeyList keys) {
        final FileID fileId = FileID.newBuilder().fileNum(2337).build();
        return new FileMetadata(fileId, null, keys, null, null, false, null);
    }
}
