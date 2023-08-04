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

package contract;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.Erc721OperationsConstants.COINBASE_ID;
import static contract.Erc721OperationsConstants.COUNTERPARTY_ADDRESS;
import static contract.Erc721OperationsConstants.COUNTERPARTY_ID;
import static contract.Erc721OperationsConstants.EXPECTED_STORAGE;
import static contract.Erc721OperationsConstants.INITIAL_BALANCE;
import static contract.Erc721OperationsConstants.NEXT_ENTITY_NUM;
import static contract.Erc721OperationsConstants.OPERATOR_ADDRESS;
import static contract.Erc721OperationsConstants.OPERATOR_ID;
import static contract.Erc721OperationsConstants.PARTY_ADDRESS;
import static contract.Erc721OperationsConstants.PARTY_ID;
import static contract.Erc721OperationsConstants.STANDARD_AUTO_RENEW_PERIOD;
import static contract.Erc721OperationsConstants.TOKEN_TREASURY_ADDRESS;
import static contract.Erc721OperationsConstants.TOKEN_TREASURY_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.blockrecords.BlockInfo;
import com.hedera.hapi.node.state.blockrecords.RunningHashes;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.ids.EntityIdService;
import com.hedera.node.app.records.BlockRecordService;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.node.app.workflows.handle.stack.SavepointStackImpl;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * <p><b>GIVEN</b>
 * <ol>
 *     <li>A {@link com.hedera.node.app.fixtures.state.FakeHederaState} that contains initcode for an
 *     ERC-721 contract; and,</li>
 *     <li>Several well-known accounts with and without EVM addresses.</li>
 * </ol>
 *
 * <p><b>WHEN</b>
 * <ol>
 *     <li>A {@code ContractCreate} operation creates a contract from the {@code ERC721Full} initcode,
 *     minting NFTs with serial numbers 2, 3, 5, 8, and 13 to the {@code TREASURY}; and,</li>
 *     <li>The {@code TREASURY} approves the {@code PARTY} to spend 2; and,</li>
 *     <li>The {@code TREASURY} approves the {@code PARTY} to spend 3; and,</li>
 *     <li>The {@code TREASURY} approves the {@code OPERATOR} for all; and,</li>
 *     <li>The {@code OPERATOR} transfers 13 from the {@code TREASURY} to the {@code COUNTERPARTY}; and,</li>
 *     <li>The {@code PARTY} transfers 3 from the {@code TREASURY} to the {@code COUNTERPARTY}; and,</li>
 *     <li>The {@code TREASURY} transfers 8 from itself to the {@code PARTY}; and,</li>
 *     <li>The {@code COUNTERPARTY} transfers 3 from itself to the {@code PARTY}; and,</li>
 * </ol>
 *
 * <p><b>THEN</b>
 * <ol>
 *     <li>The created contract exists at the expected id, with the expected bytecode.</li>
 *     <li>The contract's storage has the expected slots.</li>
 * </ol>
 *
 * <p>Please see <a href="https://github.com/hashgraph/hedera-services/issues/7565">this issue</a>
 * for the {@code HapiSpec} run against {@code mono-service} to determine the expected values.
 */
@ExtendWith(MockitoExtension.class)
class Erc721OperationsTest {
    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void recordedErc721OperationsResultInExpectedState() throws IOException {
        // given:
        setupFakeStates();
        setupErc721InitcodeAndWellKnownAccounts();
        scaffoldingComponent.workingStateAccessor().setHederaState(scaffoldingComponent.hederaState());

        // when:
        handle(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        handle(
                CONTRACT_SERVICE.handlers().contractCallHandler(),
                synthApproveTxn(TOKEN_TREASURY_ID, PARTY_ADDRESS, 2),
                synthApproveTxn(TOKEN_TREASURY_ID, PARTY_ADDRESS, 3),
                synthSetApprovalForAll(TOKEN_TREASURY_ID, OPERATOR_ADDRESS, true),
                synthSafeTransferFrom(OPERATOR_ID, TOKEN_TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 13),
                synthSafeTransferFrom(PARTY_ID, TOKEN_TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 3),
                synthSafeTransferFrom(TOKEN_TREASURY_ID, TOKEN_TREASURY_ADDRESS, PARTY_ADDRESS, 8),
                synthSafeTransferFrom(COUNTERPARTY_ID, COUNTERPARTY_ADDRESS, PARTY_ADDRESS, 3));

        // then:
        assertExpectedContract();
        assertExpectedBytecode();
        assertExpectedStorage();
    }

    private void handle(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = scaffoldingComponent.contextFactory().apply(txn);
            handler.handle(context);
            ((SavepointStackImpl) context.savepointStack()).commit();
        }
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TOKEN_TREASURY_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(Erc721OperationsConstants.ERC721_FULL_INITCODE_FILE_ID)
                        .gas(Erc721OperationsConstants.GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthApproveTxn(
            @NonNull final AccountID payer, @NonNull final Bytes spender, long serialNum) {
        final var encoded =
                Erc721OperationsConstants.APPROVE.encodeCallWithArgs(addressOf(spender), BigInteger.valueOf(serialNum));
        System.out.println(Erc721OperationsConstants.APPROVE.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSafeTransferFrom(
            @NonNull final AccountID payer, @NonNull final Bytes from, @NonNull final Bytes to, final long serialNo) {
        final var encoded = Erc721OperationsConstants.SAFE_TRANSFER_FROM.encodeCallWithArgs(
                addressOf(from), addressOf(to), BigInteger.valueOf(serialNo));
        System.out.println(Erc721OperationsConstants.SAFE_TRANSFER_FROM.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSetApprovalForAll(
            @NonNull final AccountID payer, @NonNull final Bytes operator, final boolean approved) {
        final var encoded =
                Erc721OperationsConstants.SET_APPROVAL_FOR_ALL.encodeCallWithArgs(addressOf(operator), approved);
        System.out.println(Erc721OperationsConstants.SET_APPROVAL_FOR_ALL.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private ContractCallTransactionBody callWithParams(@NonNull final ByteBuffer encoded) {
        return ContractCallTransactionBody.newBuilder()
                .functionParameters(Bytes.wrap(encoded.array()))
                .contractID(Erc721OperationsConstants.ERC721_FULL_CONTRACT)
                .gas(Erc721OperationsConstants.GAS_TO_OFFER)
                .build();
    }

    private void assertExpectedContract() {
        // Assert the contract exists in state at the expected id number
        final var contract = expectedDeployedContract();
        assertNotNull(contract);
        assertTrue(contract.smartContract());
    }

    private void assertExpectedBytecode() throws IOException {
        final ReadableKVState<EntityNumber, Bytecode> bytecode = scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.BYTECODE_KEY);
        final var actualBytecode =
                bytecode.get(new EntityNumber(Erc721OperationsConstants.ERC721_FULL.accountNumOrThrow()));
        assertNotNull(actualBytecode);
        assertEquals(expectedBytecode(), actualBytecode.code());
    }

    private void assertExpectedStorage() {
        final ReadableKVState<SlotKey, SlotValue> storage = scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.STORAGE_KEY);
        EXPECTED_STORAGE.forEach((key, value) -> {
            final var slot = storage.get(new SlotKey(Erc721OperationsConstants.ERC721_FULL.accountNumOrThrow(), key));
            assertNotNull(slot);
            assertEquals(value, slot.value());
        });
        final var contract = Objects.requireNonNull(expectedDeployedContract());
        assertEquals(EXPECTED_STORAGE.size(), contract.contractKvPairsNumber());
    }

    private @Nullable Account expectedDeployedContract() {
        final ReadableKVState<AccountID, Account> accounts = scaffoldingComponent
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
        return accounts.get(Erc721OperationsConstants.ERC721_FULL);
    }

    private void setupFakeStates() {
        final var fakeHederaState = (FakeHederaState) scaffoldingComponent.hederaState();

        fakeHederaState.addService(
                EntityIdService.NAME,
                Map.of("ENTITY_ID", new AtomicReference<>(new EntityNumber(NEXT_ENTITY_NUM - 1L))));

        fakeHederaState.addService("RecordCache", Map.of("TransactionRecordQueue", new ArrayDeque<>()));

        fakeHederaState.addService(
                BlockRecordService.NAME,
                Map.of(
                        BlockRecordService.BLOCK_INFO_STATE_KEY, new AtomicReference<>(BlockInfo.DEFAULT),
                        BlockRecordService.RUNNING_HASHES_STATE_KEY, new AtomicReference<>(RunningHashes.DEFAULT)));

        fakeHederaState.addService(
                TokenService.NAME,
                Map.of(
                        TokenServiceImpl.ACCOUNTS_KEY, initialAccounts(),
                        TokenServiceImpl.ALIASES_KEY, initialAliases(),
                        TokenServiceImpl.TOKENS_KEY, new HashMap<>()));
        fakeHederaState.addService(
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, new HashMap<FileID, File>()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));
    }

    private Map<Bytes, AccountID> initialAliases() {
        final var aliases = new HashMap<Bytes, AccountID>();
        aliases.put(TOKEN_TREASURY_ADDRESS, TOKEN_TREASURY_ID);
        aliases.put(COUNTERPARTY_ADDRESS, COUNTERPARTY_ID);
        aliases.put(OPERATOR_ADDRESS, OPERATOR_ID);
        return aliases;
    }

    private Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                TOKEN_TREASURY_ID,
                Account.newBuilder()
                        .accountId(TOKEN_TREASURY_ID)
                        .alias(TOKEN_TREASURY_ADDRESS)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                COUNTERPARTY_ID,
                Account.newBuilder()
                        .accountId(COUNTERPARTY_ID)
                        .alias(COUNTERPARTY_ADDRESS)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                OPERATOR_ID,
                Account.newBuilder()
                        .accountId(OPERATOR_ID)
                        .alias(OPERATOR_ADDRESS)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                PARTY_ID,
                Account.newBuilder()
                        .accountId(PARTY_ID)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    private void setupErc721InitcodeAndWellKnownAccounts() throws IOException {
        final WritableKVState<FileID, File> files = scaffoldingComponent
                .hederaState()
                .createWritableStates(FileServiceImpl.NAME)
                .get(FileServiceImpl.BLOBS_KEY);
        files.put(
                Erc721OperationsConstants.ERC721_FULL_INITCODE_FILE_ID,
                File.newBuilder().contents(erc721FullInitcode()).build());
        commitKvStateChanges(files);

        final WritableKVState<AccountID, Account> accounts = scaffoldingComponent
                .hederaState()
                .createWritableStates(TokenService.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
        for (int i = 0; i < Erc721OperationsConstants.ACCOUNTS.length; i++) {
            final var cryptoKey = Erc721OperationsConstants.ACCOUNT_KEYS[i];
            final var pbjKey = cryptoKey.length() == 33
                    ? Key.newBuilder().ecdsaSecp256k1(cryptoKey).build()
                    : Key.newBuilder().ed25519(cryptoKey).build();
            final var account = Account.newBuilder()
                    .key(pbjKey)
                    .alias(Erc721OperationsConstants.ACCOUNT_ALIASES[i])
                    .build();
            accounts.put(Erc721OperationsConstants.ACCOUNTS[i], account);
        }
    }

    private Bytes erc721FullInitcode() throws IOException {
        return resourceAsBytes("initcode/ERC721Full.bin");
    }

    private Bytes expectedBytecode() throws IOException {
        return resourceAsBytes("bytecode/ERC721Full.bin");
    }

    private Bytes resourceAsBytes(@NonNull final String loc) throws IOException {
        try (final var in = Erc721OperationsTest.class.getClassLoader().getResourceAsStream(loc)) {
            final var bytes = Objects.requireNonNull(in).readAllBytes();
            return Bytes.wrap(bytes);
        }
    }

    private void commitKvStateChanges(final WritableKVState<?, ?> state) {
        ((WritableKVStateBase<?, ?>) state).commit();
    }

    private Address addressOf(@NonNull final Bytes address) {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(1, address.toByteArray())));
    }
}
