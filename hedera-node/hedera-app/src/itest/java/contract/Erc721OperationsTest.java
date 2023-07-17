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

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 */
@ExtendWith(MockitoExtension.class)
class Erc721OperationsTest {
    private static final Bytes[] ACCOUNT_KEYS = {
            Bytes.fromHex("000000"),
            Bytes.fromHex("000000"),
            Bytes.fromHex("000000"),
            Bytes.fromHex("000000"),
    };
    private static final Bytes[] ACCOUNT_ALIASES = {
            Bytes.fromHex("096959f155eE025b17E5C537b6dCB4a29BBAd8c2"),
            Bytes.fromHex("D893F18B69A06F7ffFfaD77202c2f627CB2C9605"),
            Bytes.fromHex("8CEB1aE3aB4ABfcA08c0BC5CD59DE0Bce7b5554f"),
            Bytes.EMPTY,
    };
    private static final AccountID TREASURY = AccountID.newBuilder().accountNum(1001L).build();
    private static final AccountID COUNTERPARTY = AccountID.newBuilder().accountNum(1002L).build();
    private static final AccountID OPERATOR = AccountID.newBuilder().accountNum(1003L).build();
    private static final AccountID PARTY = AccountID.newBuilder().accountNum(1004L).build();
    private static final AccountID[] ACCOUNTS = { TREASURY, COUNTERPARTY, OPERATOR, PARTY, };
    private static final FileID ERC721_FULL_INITCODE_FILE_ID = new FileID(0, 0, 1005);
    private static final AccountID ERC721_FULL = AccountID.newBuilder().accountNum(1006).build();

    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void erc721OperationsResultInExpectedState() throws IOException {
        // given:
        setupErc721InitcodeAndWellKnownAccounts();

        // when:
        handleAndCommit(synthCreateTxn(), scaffoldingComponent.contractCreateHandler());

        // then:
        assertContractDeployedWithExpectedStorage();
    }

    private void handleAndCommit(@NonNull final TransactionBody txn, @NonNull final TransactionHandler handler) {
        final var context = scaffoldingComponent.contextForTransaction().apply(txn);
        handler.handle(context);
        commitStateChanges();
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TREASURY))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(ERC721_FULL_INITCODE_FILE_ID)
                        .build())
                .build();
    }

    private void assertContractDeployedWithExpectedStorage() throws IOException {
        // Assert the contract exists in state at the expected id number
        final ReadableKVState<AccountID, Account> accounts =
                scaffoldingComponent
                        .hederaState()
                        .createReadableStates(TokenServiceImpl.NAME)
                        .get(TokenServiceImpl.ACCOUNTS_KEY);
        final var contract = accounts.get(ERC721_FULL);
        assertNotNull(contract);
        assertTrue(contract.smartContract());

        // Assert its deployed bytecode matches expectations
        final ReadableKVState<EntityNumber, Bytecode> bytecode =
                scaffoldingComponent
                        .hederaState()
                        .createReadableStates(ContractServiceImpl.NAME)
                        .get(ContractSchema.BYTECODE_KEY);
        final var actualBytecode = bytecode.get(new EntityNumber(ERC721_FULL.accountNumOrThrow()));
        assertNotNull(actualBytecode);
        assertEquals(expectedBytecode(), actualBytecode.code());
    }

    private void setupErc721InitcodeAndWellKnownAccounts() throws IOException {
        final WritableKVState<FileID, File> files = scaffoldingComponent
                .hederaState()
                .createWritableStates(FileServiceImpl.NAME)
                .get(FileServiceImpl.BLOBS_KEY);
        files.put(ERC721_FULL_INITCODE_FILE_ID, File.newBuilder().contents(erc721FullInitcode()).build());
        commitKvStateChanges(files);

        final WritableKVState<AccountID, Account> accounts = scaffoldingComponent
                .hederaState()
                .createWritableStates("accounts")
                .get(TokenServiceImpl.ACCOUNTS_KEY);
        for (int i = 0; i < ACCOUNTS.length; i++) {
            final var cryptoKey = ACCOUNT_KEYS[i];
            final var pbjKey = cryptoKey.length() == 33
                    ? Key.newBuilder().ecdsaSecp256k1(cryptoKey).build()
                    : Key.newBuilder().ed25519(cryptoKey).build();
            final var account = Account.newBuilder()
                    .key(pbjKey)
                    .alias(ACCOUNT_ALIASES[i])
                    .build();
            accounts.put(ACCOUNTS[i], account);
        }
    }

    private Bytes erc721FullInitcode() throws IOException {
        return resourceAsBytes("initcode/ERC721Full.bin");
    }

    private Bytes expectedBytecode() throws IOException {
        return resourceAsBytes("bytecode/ERC721Full.bin");
    }

    private Bytes resourceAsBytes(@NonNull final String loc) throws IOException {
        try (final var in = Erc721OperationsTest.class.getClassLoader()
                .getResourceAsStream(loc)) {
            final var bytes = Objects.requireNonNull(in).readAllBytes();
            return Bytes.wrap(bytes);
        }
    }

    private void commitStateChanges() {
        final var tokenState = scaffoldingComponent.hederaState().createWritableStates(TokenServiceImpl.NAME);
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ACCOUNTS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ALIASES_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.TOKEN_RELS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.TOKENS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.NFTS_KEY));

        final var contractState = scaffoldingComponent.hederaState().createWritableStates(ContractServiceImpl.NAME);
        commitKvStateChanges(contractState.get(ContractSchema.BYTECODE_KEY));
        commitKvStateChanges(contractState.get(ContractSchema.STORAGE_KEY));
    }

    private void commitKvStateChanges(final WritableKVState<?, ?> state) {
        ((WritableKVStateBase<?, ?>) state).commit();
    }
}
