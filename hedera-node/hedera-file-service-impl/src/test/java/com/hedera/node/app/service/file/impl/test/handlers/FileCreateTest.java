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

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_EXPIRATION_TIME;
import static com.hedera.node.app.spi.fixtures.Assertions.assertThrowsPreCheck;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.RealmID;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.ShardID;
import com.hedera.hapi.node.base.Timestamp;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.file.FileCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.hapi.fees.usage.file.FileOpsUsage;
import com.hedera.node.app.service.file.impl.WritableFileStore;
import com.hedera.node.app.service.file.impl.handlers.FileCreateHandler;
import com.hedera.node.app.service.file.impl.records.CreateFileStreamBuilder;
import com.hedera.node.app.service.file.impl.test.FileTestBase;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.spi.fixtures.workflows.FakePreHandleContext;
import com.hedera.node.app.spi.ids.EntityNumGenerator;
import com.hedera.node.app.spi.metrics.StoreMetricsService;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleContext;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.app.spi.workflows.PreCheckException;
import com.hedera.node.config.data.FilesConfig;
import com.hedera.node.config.data.HederaConfig;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.node.config.types.LongPair;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class FileCreateTest extends FileTestBase {
    static final AccountID ACCOUNT_ID_3 = AccountID.newBuilder().accountNum(3L).build();
    private static final Configuration DEFAULT_CONFIG = HederaTestConfigBuilder.createConfig();

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private AttributeValidator validator;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private Configuration configuration;

    @Mock
    private CreateFileStreamBuilder recordBuilder;

    @Mock
    private HandleContext.SavepointStack stack;

    @Mock
    private FileOpsUsage fileOpsUsage;

    @Mock
    private StoreMetricsService storeMetricsService;

    @Mock
    private EntityNumGenerator entityNumGenerator;

    private FilesConfig config;

    private WritableFileStore fileStore;
    private FileCreateHandler subject;

    private TransactionBody newCreateTxn(KeyList keys, long expirationTime) {
        final var txnId = TransactionID.newBuilder().accountID(ACCOUNT_ID_3).build();
        final var createFileBuilder = FileCreateTransactionBody.newBuilder();
        if (keys != null) {
            createFileBuilder.keys(keys);
        }
        createFileBuilder.memo("memo");
        createFileBuilder.contents(Bytes.wrap(contents));
        createFileBuilder.shardID(ShardID.DEFAULT);
        createFileBuilder.realmID(RealmID.DEFAULT);

        if (expirationTime > 0) {
            createFileBuilder.expirationTime(
                    Timestamp.newBuilder().seconds(expirationTime).build());
        }
        return TransactionBody.newBuilder()
                .transactionID(txnId)
                .fileCreate(createFileBuilder.build())
                .build();
    }

    @BeforeEach
    void setUp() {
        subject = new FileCreateHandler(fileOpsUsage);
        fileStore = new WritableFileStore(writableStates, DEFAULT_CONFIG, storeMetricsService);
        config = HederaTestConfigBuilder.createConfig().getConfigData(FilesConfig.class);
        lenient().when(handleContext.configuration()).thenReturn(configuration);
        lenient().when(configuration.getConfigData(FilesConfig.class)).thenReturn(config);
        lenient().when(storeFactory.writableStore(WritableFileStore.class)).thenReturn(fileStore);
        lenient().when(handleContext.entityNumGenerator()).thenReturn(entityNumGenerator);
    }

    @Test
    @DisplayName("Non-payer keys is added")
    void differentKeys() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var keys = anotherKeys;

        // when:
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(keys, expirationTime));
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.body().fileCreateOrThrow().keys().keys()).isEqualTo(keys.keys());
    }

    @Test
    @DisplayName("empty keys are added")
    void createWithEmptyKeys() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();

        // when:
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(null, expirationTime));

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("no expiration time is added")
    void createAddsDifferentSubmitKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var keys = anotherKeys;

        // when:
        final var txn = newCreateTxn(keys, 0);
        final var context = new FakePreHandleContext(accountStore, txn);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThrowsPreCheck(() -> subject.pureChecks(txn), INVALID_EXPIRATION_TIME);
    }

    @Test
    @DisplayName("Only payer key is always required")
    void requiresPayerKey() throws PreCheckException {
        // given:
        final var payerKey = mockPayerLookup();
        final var context = new FakePreHandleContext(accountStore, newCreateTxn(null, expirationTime));

        // when:
        subject.preHandle(context);

        // then:
        assertThat(context.payerKey()).isEqualTo(payerKey);
        assertThat(context.requiredNonPayerKeys()).isEmpty();
    }

    @Test
    @DisplayName("Handle works as expected")
    void handleWorksAsExpected() {
        final var keys = anotherKeys;
        final var txBody = newCreateTxn(keys, expirationTime);

        given(handleContext.body()).willReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(validator);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), any()))
                .willReturn(new ExpiryMeta(expirationTime, NA, null));
        given(entityNumGenerator.newEntityNum()).willReturn(1_234L);
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(CreateFileStreamBuilder.class)).willReturn(recordBuilder);

        subject.handle(handleContext);

        final FileID createdFileId = FileID.newBuilder().fileNum(1_234L).build();
        final var createdFile = fileStore.get(createdFileId);
        assertTrue(createdFile.isPresent());

        final var actualFile = createdFile.get();
        assertEquals("memo", actualFile.memo());
        assertEquals(keys, actualFile.keys());
        assertEquals(1_234_567L, actualFile.expirationSecond());
        assertEquals(contentsBytes, actualFile.contents());
        assertEquals(fileId, actualFile.fileId());
        assertFalse(actualFile.deleted());
        verify(recordBuilder).fileID(FileID.newBuilder().fileNum(1_234L).build());
        assertTrue(fileStore.get(createdFileId).isPresent());
    }

    @Test
    @DisplayName("Handle works as expected without keys")
    void handleDoesntRequireKeys() {
        final var txBody = newCreateTxn(keys, expirationTime);

        given(configuration.getConfigData(HederaConfig.class))
                .willReturn(DEFAULT_CONFIG.getConfigData(HederaConfig.class));
        given(handleContext.body()).willReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(validator);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), any()))
                .willReturn(new ExpiryMeta(1_234_567L, NA, null));
        given(entityNumGenerator.newEntityNum()).willReturn(1_234L);
        given(handleContext.savepointStack()).willReturn(stack);
        given(stack.getBaseBuilder(CreateFileStreamBuilder.class)).willReturn(recordBuilder);

        subject.handle(handleContext);

        final FileID createdFileId = FileID.newBuilder().fileNum(1_234L).build();
        final var createdFile = fileStore.get(createdFileId);
        assertTrue(createdFile.isPresent());

        final var actualFile = createdFile.get();
        assertEquals("memo", actualFile.memo());
        assertEquals(keys, actualFile.keys());
        assertEquals(1_234_567L, actualFile.expirationSecond());
        assertEquals(contentsBytes, actualFile.contents());
        assertEquals(fileId, actualFile.fileId());
        assertFalse(actualFile.deleted());
        verify(recordBuilder).fileID(FileID.newBuilder().fileNum(1_234L).build());
        assertTrue(fileStore.get(createdFileId).isPresent());
    }

    @Test
    @DisplayName("Translates INVALID_EXPIRATION_TIME to AUTO_RENEW_DURATION_NOT_IN_RANGE")
    void translatesInvalidExpiryException() {
        final var txBody = newCreateTxn(keys, expirationTime);

        given(handleContext.body()).willReturn(txBody);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), any()))
                .willThrow(new HandleException(ResponseCodeEnum.INVALID_EXPIRATION_TIME));

        final var failure = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE, failure.getStatus());
    }

    @Test
    @DisplayName("Memo Validation Failure will throw")
    void handleThrowsIfAttributeValidatorFails() {
        final var keys = anotherKeys;
        final var txBody = newCreateTxn(keys, expirationTime);

        given(handleContext.body()).willReturn(txBody);
        given(handleContext.attributeValidator()).willReturn(validator);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(writableStore);
        given(handleContext.expiryValidator()).willReturn(expiryValidator);
        given(expiryValidator.resolveCreationAttempt(anyBoolean(), any(), any()))
                .willReturn(new ExpiryMeta(1_234_567L, NA, null));

        doThrow(new HandleException(ResponseCodeEnum.MEMO_TOO_LONG))
                .when(validator)
                .validateMemo(txBody.fileCreate().memo());

        assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertTrue(fileStore.get(FileID.newBuilder().fileNum(1234L).build()).isEmpty());
    }

    @Test
    @DisplayName("Fails when the file are already created")
    void failsWhenMaxRegimeExceeds() {
        final var keys = anotherKeys;
        final var txBody = newCreateTxn(keys, expirationTime);
        given(handleContext.body()).willReturn(txBody);
        final var writableState = writableFileStateWithOneKey();

        given(writableStates.<FileID, File>get(FILES)).willReturn(writableState);
        final var fileStore = new WritableFileStore(writableStates, DEFAULT_CONFIG, storeMetricsService);
        given(storeFactory.writableStore(WritableFileStore.class)).willReturn(fileStore);

        assertEquals(2, fileStore.sizeOfState());

        config = new FilesConfig(1L, 1L, 1L, 1L, 1L, 1L, new LongPair(150L, 159L), 1L, 1L, 1);
        given(configuration.getConfigData(any())).willReturn(config);

        final var msg = assertThrows(HandleException.class, () -> subject.handle(handleContext));
        assertEquals(ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED, msg.getStatus());
        assertEquals(0, this.fileStore.modifiedFiles().size());
    }

    public static void assertFailsWith(final ResponseCodeEnum status, final Runnable something) {
        final var ex = assertThrows(PreCheckException.class, something::run);
        assertEquals(status, ex.responseCode());
    }

    private Key mockPayerLookup() throws PreCheckException {
        return mockPayerLookup(A_COMPLEX_KEY);
    }

    private Key mockPayerLookup(Key key) throws PreCheckException {
        final var account = mock(Account.class);
        given(account.key()).willReturn(key);
        given(accountStore.getAccountById(ACCOUNT_ID_3)).willReturn(account);
        return key;
    }
}
