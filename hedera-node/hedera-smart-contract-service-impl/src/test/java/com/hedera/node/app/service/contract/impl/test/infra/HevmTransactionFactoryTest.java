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

package com.hedera.node.app.service.contract.impl.test.infra;

import static com.hedera.hapi.node.base.ResponseCodeEnum.AUTORENEW_DURATION_NOT_IN_RANGE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.BAD_ENCODING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_FILE_EMPTY;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_GAS;
import static com.hedera.hapi.node.base.ResponseCodeEnum.CONTRACT_NEGATIVE_VALUE;
import static com.hedera.hapi.node.base.ResponseCodeEnum.ERROR_DECODING_BYTESTRING;
import static com.hedera.hapi.node.base.ResponseCodeEnum.FILE_DELETED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_AUTORENEW_ACCOUNT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_FILE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_RENEWAL_PERIOD;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_STAKING_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_GAS_LIMIT_EXCEEDED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.MEMO_TOO_LONG;
import static com.hedera.hapi.node.base.ResponseCodeEnum.NOT_SUPPORTED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED;
import static com.hedera.hapi.node.base.ResponseCodeEnum.REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT;
import static com.hedera.hapi.node.base.ResponseCodeEnum.SERIALIZATION_FAILED;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AN_ED25519_KEY;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AUTO_ASSOCIATING_CONTRACTS_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.AUTO_ASSOCIATING_LEDGER_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CALL_DATA;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.CONSTRUCTOR_PARAMS;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_CONTRACTS_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_LEDGER_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.DEFAULT_STAKING_CONFIG;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.INITCODE;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.INITCODE_FILE_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_CALL;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.MOCK_ETH;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.NON_SYSTEM_ACCOUNT_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SENDER_ID;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_DURATION;
import static com.hedera.node.app.service.contract.impl.test.TestHelpers.SOME_MEMO;
import static com.hedera.node.app.spi.validation.ExpiryMeta.NA;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.Duration;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.KeyList;
import com.hedera.hapi.node.base.ResponseCodeEnum;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.hevm.HederaEvmTransaction;
import com.hedera.node.app.service.contract.impl.infra.HevmTransactionFactory;
import com.hedera.node.app.service.file.ReadableFileStore;
import com.hedera.node.app.service.token.ReadableAccountStore;
import com.hedera.node.app.service.token.impl.validators.StakingValidator;
import com.hedera.node.app.spi.info.NetworkInfo;
import com.hedera.node.app.spi.validation.AttributeValidator;
import com.hedera.node.app.spi.validation.ExpiryMeta;
import com.hedera.node.app.spi.validation.ExpiryValidator;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Optional;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class HevmTransactionFactoryTest {
    @Mock
    private NetworkInfo networkInfo;

    @Mock
    private ReadableFileStore fileStore;

    @Mock
    private ReadableAccountStore accountStore;

    @Mock
    private ExpiryValidator expiryValidator;

    @Mock
    private StakingValidator stakingValidator;

    @Mock
    private AttributeValidator attributeValidator;

    private HevmTransactionFactory subject;

    @BeforeEach
    void setUp() {
        subject = new HevmTransactionFactory(
                networkInfo,
                DEFAULT_LEDGER_CONFIG,
                DEFAULT_STAKING_CONFIG,
                DEFAULT_CONTRACTS_CONFIG,
                accountStore,
                expiryValidator,
                stakingValidator,
                fileStore,
                attributeValidator);
    }

    @Test
    void fromHapiCreationFailsOnInvalidRenewalPeriod() {
        assertCreateFailsWith(INVALID_RENEWAL_PERIOD, b -> b.autoRenewPeriod(Duration.DEFAULT));
    }

    @Test
    void fromHapiCreationFailsOnOutOfRangeDuration() {
        doThrow(new HandleException(AUTORENEW_DURATION_NOT_IN_RANGE))
                .when(attributeValidator)
                .validateAutoRenewPeriod(SOME_DURATION.seconds());
        assertCreateFailsWith(AUTORENEW_DURATION_NOT_IN_RANGE, b -> b.autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationFailsOnNegativeGas() {
        assertCreateFailsWith(CONTRACT_NEGATIVE_GAS, b -> b.gas(-1).autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationFailsOnNegativeValue() {
        assertCreateFailsWith(
                CONTRACT_NEGATIVE_VALUE, b -> b.initialBalance(-1).gas(1).autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationFailsOnExcessGas() {
        assertCreateFailsWith(MAX_GAS_LIMIT_EXCEEDED, b -> b.initialBalance(1)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec() + 1)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationDoesNotPermitUnsupportedAutoAssociations() {
        assertCreateFailsWith(NOT_SUPPORTED, b -> b.gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .maxAutomaticTokenAssociations(1)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationDoesNotPermitExcessAutoAssociations() {
        givenAutoAssociatingSubject();
        assertCreateFailsWith(REQUESTED_NUM_AUTOMATIC_ASSOCIATIONS_EXCEEDS_ASSOCIATION_LIMIT, b -> b.gas(
                        DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .maxAutomaticTokenAssociations(AUTO_ASSOCIATING_LEDGER_CONFIG.maxAutoAssociations() + 1)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationDoesNotPermitNonDefaultProxyField() {
        assertCreateFailsWith(PROXY_ACCOUNT_ID_FIELD_IS_DEPRECATED, b -> b.gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(NON_SYSTEM_ACCOUNT_ID)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesStaking() {
        doThrow(new HandleException(INVALID_STAKING_ID))
                .when(stakingValidator)
                .validateStakedId(
                        false, "STAKED_NODE_ID", null, 123L, accountStore, DEFAULT_STAKING_CONFIG, networkInfo);
        assertCreateFailsWith(INVALID_STAKING_ID, b -> b.stakedNodeId(123)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesMemo() {
        doThrow(new HandleException(MEMO_TOO_LONG)).when(attributeValidator).validateMemo(SOME_MEMO);
        assertCreateFailsWith(MEMO_TOO_LONG, b -> b.memo(SOME_MEMO)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesKeyWithSerializationFailedStatus() {
        doThrow(new HandleException(BAD_ENCODING)).when(attributeValidator).validateKey(AN_ED25519_KEY);
        assertCreateFailsWith(SERIALIZATION_FAILED, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesAutoRenewId() {
        final var createMeta = new ExpiryMeta(NA, SOME_DURATION.seconds(), NON_SYSTEM_ACCOUNT_ID);
        doThrow(new HandleException(INVALID_AUTORENEW_ACCOUNT))
                .when(expiryValidator)
                .resolveCreationAttempt(true, createMeta);
        assertCreateFailsWith(INVALID_AUTORENEW_ACCOUNT, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesInitcodeFileId() {
        assertCreateFailsWith(INVALID_FILE_ID, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .fileID(INITCODE_FILE_ID)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesInitcodeDeletionStatus() {
        given(fileStore.getFileLeaf(INITCODE_FILE_ID))
                .willReturn(Optional.of(File.newBuilder().deleted(true).build()));
        assertCreateFailsWith(FILE_DELETED, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .fileID(INITCODE_FILE_ID)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationValidatesInitcodeNotEmpty() {
        given(fileStore.getFileLeaf(INITCODE_FILE_ID))
                .willReturn(Optional.of(File.newBuilder().build()));
        assertCreateFailsWith(CONTRACT_FILE_EMPTY, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .fileID(INITCODE_FILE_ID)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationTranslatesHexParsingExcepion() {
        given(fileStore.getFileLeaf(INITCODE_FILE_ID))
                .willReturn(Optional.of(File.newBuilder().contents(CALL_DATA).build()));
        assertCreateFailsWith(ERROR_DECODING_BYTESTRING, b -> b.memo(SOME_MEMO)
                .adminKey(AN_ED25519_KEY)
                .constructorParameters(Bytes.wrap(new byte[] {(byte) 0xab}))
                .fileID(INITCODE_FILE_ID)
                .autoRenewAccountId(NON_SYSTEM_ACCOUNT_ID)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
    }

    @Test
    void fromHapiCreationStillPermitsEmptyKey() {
        final var immutabilitySentinelKey =
                Key.newBuilder().keyList(KeyList.DEFAULT).build();
        final var transaction = getManufacturedCreation(b -> b.memo(SOME_MEMO)
                .initcode(CALL_DATA)
                .initialBalance(123L)
                .adminKey(immutabilitySentinelKey)
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
        assertEquals(SENDER_ID, transaction.senderId());
        assertNull(transaction.contractId());
        assertNull(transaction.relayerId());
        assertFalse(transaction.hasExpectedNonce());
        assertSame(CALL_DATA, transaction.payload());
        assertNull(transaction.chainId());
        assertEquals(123L, transaction.value());
        assertEquals(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec(), transaction.gasLimit());
        assertFalse(transaction.hasOfferedGasPrice());
        assertFalse(transaction.hasMaxGasAllowance());
        assertNotNull(transaction.hapiCreation());
    }

    @Test
    void fromHapiCreationAppendsConstructorArgsIfPresent() {
        given(fileStore.getFileLeaf(INITCODE_FILE_ID))
                .willReturn(Optional.of(File.newBuilder().contents(INITCODE).build()));
        String hexedPayload = new String(INITCODE.toByteArray()) + CommonUtils.hex(CONSTRUCTOR_PARAMS.toByteArray());
        final var expectedPayload = Bytes.wrap(CommonUtils.unhex(hexedPayload));
        final var transaction = getManufacturedCreation(b -> b.memo(SOME_MEMO)
                .fileID(INITCODE_FILE_ID)
                .constructorParameters(CONSTRUCTOR_PARAMS)
                .initialBalance(123L)
                .adminKey(Key.newBuilder().keyList(KeyList.DEFAULT))
                .gas(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec())
                .proxyAccountID(AccountID.DEFAULT)
                .autoRenewPeriod(SOME_DURATION));
        assertEquals(SENDER_ID, transaction.senderId());
        assertNull(transaction.contractId());
        assertNull(transaction.relayerId());
        assertFalse(transaction.hasExpectedNonce());
        assertEquals(expectedPayload, transaction.payload());
        assertNull(transaction.chainId());
        assertEquals(123L, transaction.value());
        assertEquals(DEFAULT_CONTRACTS_CONFIG.maxGasPerSec(), transaction.gasLimit());
        assertFalse(transaction.hasOfferedGasPrice());
        assertFalse(transaction.hasMaxGasAllowance());
        assertNotNull(transaction.hapiCreation());
    }

    @Test
    void fromHapiTransactionThrowsOnNonContractOperation() {
        assertThrows(IllegalArgumentException.class, () -> subject.fromHapiTransaction(TransactionBody.DEFAULT));
    }

    @Test
    void fromHapiCallNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiTransaction(MOCK_CALL));
    }

    @Test
    void fromHapiEthNotImplemented() {
        assertThrows(AssertionError.class, () -> subject.fromHapiTransaction(MOCK_ETH));
    }

    private void assertCreateFailsWith(
            @NonNull final ResponseCodeEnum status,
            @NonNull final Consumer<ContractCreateTransactionBody.Builder> spec) {
        assertFailsWith(
                status,
                () -> subject.fromHapiTransaction(TransactionBody.newBuilder()
                        .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                        .contractCreateInstance(createWith(spec))
                        .build()));
    }

    private HederaEvmTransaction getManufacturedCreation(
            @NonNull final Consumer<ContractCreateTransactionBody.Builder> spec) {
        return subject.fromHapiTransaction(TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCreateInstance(createWith(spec))
                .build());
    }

    public static void assertFailsWith(@NonNull final ResponseCodeEnum status, @NonNull final Runnable something) {
        final var ex = assertThrows(HandleException.class, something::run);
        assertEquals(status, ex.getStatus());
    }

    private ContractCreateTransactionBody createWith(final Consumer<ContractCreateTransactionBody.Builder> spec) {
        final var builder = ContractCreateTransactionBody.newBuilder();
        spec.accept(builder);
        return builder.build();
    }

    private void givenAutoAssociatingSubject() {
        subject = new HevmTransactionFactory(
                networkInfo,
                AUTO_ASSOCIATING_LEDGER_CONFIG,
                DEFAULT_STAKING_CONFIG,
                AUTO_ASSOCIATING_CONTRACTS_CONFIG,
                accountStore,
                expiryValidator,
                stakingValidator,
                fileStore,
                attributeValidator);
    }
}
