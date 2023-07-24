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

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.fixtures.state.FakeHederaState;
import com.hedera.node.app.service.contract.impl.ContractServiceImpl;
import com.hedera.node.app.service.contract.impl.state.ContractSchema;
import com.hedera.node.app.service.file.impl.FileServiceImpl;
import com.hedera.node.app.service.token.TokenService;
import com.hedera.node.app.service.token.impl.TokenServiceImpl;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.node.app.spi.state.WritableKVState;
import com.hedera.node.app.spi.state.WritableKVStateBase;
import com.hedera.node.app.spi.workflows.TransactionHandler;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.common.metrics.Metrics;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    private static final long GAS_TO_OFFER = 2_000_000L;
    private static final Bytes[] ACCOUNT_KEYS = {
        Bytes.fromHex("0323350d977f06926ea7522c05392d73bd994eda850225088c1e0f46cc2eec6ab8"),
        Bytes.fromHex("022b80534c9e022c1b8f1b5f053411577242beb595b91039fad4e233df5814e01f"),
        Bytes.fromHex("038b87d8bd6206cdf1d84b054224209a00ae504b81d40218e1929f8ec2fd67ad4e"),
        Bytes.fromHex("dc1e04e0efc0a23cc68f105a2a7bd5b7e72b08d20e8df0bf02f3dfe5e322538b"),
    };
    private static final Bytes[] ACCOUNT_ALIASES = {
        Bytes.fromHex("096959f155eE025b17E5C537b6dCB4a29BBAd8c2"),
        Bytes.fromHex("D893F18B69A06F7ffFfaD77202c2f627CB2C9605"),
        Bytes.fromHex("8CEB1aE3aB4ABfcA08c0BC5CD59DE0Bce7b5554f"),
        Bytes.EMPTY,
    };
    private static final Map<Bytes, Bytes> EXPECTED_STORAGE = Map.ofEntries(
            Map.entry(
                    Bytes.fromHex("4ED80C6A5F6FC6B817594793D5BC01D5AFC46D4DEB2D84AA5499B6BA2A91788B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000002")),
            Map.entry(
                    Bytes.fromHex("0F24D19A172FA39F354DF146E316F49BA39EED2FB244C2D71184E128EE8EA57E"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("1D9A121EAE26CB344361C7A5EC17B9B0DC501335BE929850EA33D9A7A2EA135B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("679795A0195A1B76CDEBB7C51D74E058AEE92919B8C3389AF86EF24535E8A28C"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("7DFE757ECD65CBD7922A9C0161E935DD7FDBCC0E999689C7D31633896B1FC60B"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("67BE87C3FF9960CA1E9CFAC5CAB2FF4747269CF9ED20C9B7306235AC35A491C5"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("4DB623E5C4870B62D3FC9B4E8F893A1A77627D75AB45D9FF7E56BA19564AF99B"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("F7815FCCBF112960A73756E185887FEDCB9FC64CA0A16CC5923B7960ED780800"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("E2689CD4A84E23AD2F564004F1C9013E9589D260BDE6380ABA3CA7E09E4DF40C"),
                    Bytes.fromHex("000000000000000000000000096959f155ee025b17e5c537b6dcb4a29bbad8c2")),
            Map.entry(
                    Bytes.fromHex("DD170DB99724E3ABCC0E44A83A3B5D5F8332989846A2C7346446F717FDA4F32B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000002")),
            Map.entry(
                    Bytes.fromHex("D9D16D34FFB15BA3A3D852F0D403E2CE1D691FB54DE27AC87CD2F993F3EC330F"),
                    Bytes.fromHex("000000000000000000000000096959f155ee025b17e5c537b6dcb4a29bbad8c2")),
            Map.entry(
                    Bytes.fromHex("86B3FA87EE245373978E0D2D334DBDE866C9B8B039036B87C5EB2FD89BCB6BAB"),
                    Bytes.fromHex("000000000000000000000000d893f18b69a06f7ffffad77202c2f627cb2c9605")));
    private static final AccountID TREASURY =
            AccountID.newBuilder().accountNum(1001L).build();
    private static final Bytes TREASURY_ADDRESS = ACCOUNT_ALIASES[0];
    private static final AccountID COUNTERPARTY =
            AccountID.newBuilder().accountNum(1002L).build();
    private static final Bytes COUNTERPARTY_ADDRESS = ACCOUNT_ALIASES[1];
    private static final AccountID OPERATOR =
            AccountID.newBuilder().accountNum(1003L).build();
    private static final Bytes OPERATOR_ADDRESS = ACCOUNT_ALIASES[2];
    private static final AccountID PARTY =
            AccountID.newBuilder().accountNum(1004L).build();
    private static final Bytes PARTY_ADDRESS = Bytes.fromHex("00000000000000000000000000000000000003ec");
    private static final AccountID[] ACCOUNTS = {
        TREASURY, COUNTERPARTY, OPERATOR, PARTY,
    };
    private static final FileID ERC721_FULL_INITCODE_FILE_ID = new FileID(0, 0, 1005);
    private static final AccountID ERC721_FULL =
            AccountID.newBuilder().accountNum(1006).build();
    private static final ContractID ERC721_FULL_CONTRACT =
            ContractID.newBuilder().contractNum(ERC721_FULL.accountNum()).build();
    private static final Function APPROVE = new Function("approve(address,uint256)");
    private static final Function SET_APPROVAL_FOR_ALL = new Function("setApprovalForAll(address,bool)");
    private static final Function SAFE_TRANSFER_FROM = new Function("safeTransferFrom(address,address,uint256)");

    @Mock
    private Metrics metrics;

    private ScaffoldingComponent scaffoldingComponent;

    @BeforeEach
    void setUp() {
        scaffoldingComponent = DaggerScaffoldingComponent.factory().create(metrics);
    }

    @Test
    void erc721OperationsResultInExpectedState() throws IOException {
        // GIVEN:
        setupFakeStates();
        assertDoesNotThrow(this::setupErc721InitcodeAndWellKnownAccounts);
        // TODO - set up the next entity id to be 0.0.1006 as expected

        // TODO - uncomment "WHEN:" and "THEN" below
        // WHEN:
        //                handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        //        handleAndCommit(
        //                        CONTRACT_SERVICE.handlers().contractCallHandler(),
        //                synthApproveTxn(TREASURY, PARTY_ADDRESS, 2),
        //                synthApproveTxn(TREASURY, PARTY_ADDRESS, 3),
        //                synthSetApprovalForAll(TREASURY, OPERATOR_ADDRESS, true),
        //                synthSafeTransferFrom(OPERATOR, TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 13),
        //                synthSafeTransferFrom(PARTY, TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 3),
        //                synthSafeTransferFrom(TREASURY, TREASURY_ADDRESS, PARTY_ADDRESS, 8),
        //                synthSafeTransferFrom(COUNTERPARTY, COUNTERPARTY_ADDRESS, PARTY_ADDRESS, 3));

        // THEN:
        //        assertContractDeployedWithExpectedStorage();
    }

    private void handleAndCommit(@NonNull final TransactionHandler handler, @NonNull final TransactionBody... txns) {
        for (final var txn : txns) {
            final var context = scaffoldingComponent.contextForTransaction().apply(txn);
            handler.handle(context);
            commitStateChanges();
        }
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TREASURY))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .fileID(ERC721_FULL_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthApproveTxn(
            @NonNull final AccountID payer, @NonNull final Bytes spender, long serialNum) {
        final var encoded = APPROVE.encodeCallWithArgs(addressOf(spender), BigInteger.valueOf(serialNum));
        System.out.println(APPROVE.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSafeTransferFrom(
            @NonNull final AccountID payer, @NonNull final Bytes from, @NonNull final Bytes to, final long serialNo) {
        final var encoded =
                SAFE_TRANSFER_FROM.encodeCallWithArgs(addressOf(from), addressOf(to), BigInteger.valueOf(serialNo));
        System.out.println(SAFE_TRANSFER_FROM.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSetApprovalForAll(
            @NonNull final AccountID payer, @NonNull final Bytes operator, final boolean approved) {
        final var encoded = SET_APPROVAL_FOR_ALL.encodeCallWithArgs(addressOf(operator), approved);
        System.out.println(SET_APPROVAL_FOR_ALL.decodeCall(encoded));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private ContractCallTransactionBody callWithParams(@NonNull final ByteBuffer encoded) {
        return ContractCallTransactionBody.newBuilder()
                .functionParameters(Bytes.wrap(encoded.array()))
                .contractID(ERC721_FULL_CONTRACT)
                .gas(GAS_TO_OFFER)
                .build();
    }

    private void assertContractDeployedWithExpectedStorage() throws IOException {
        // Assert the contract exists in state at the expected id number
        final ReadableKVState<AccountID, Account> accounts = scaffoldingComponent
                .hederaState()
                .createReadableStates(TokenServiceImpl.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
        final var contract = accounts.get(ERC721_FULL);
        assertNotNull(contract);
        assertTrue(contract.smartContract());
        assertEquals(contract.contractKvPairsNumber(), EXPECTED_STORAGE.size());

        // Assert its deployed bytecode matches expectations
        final ReadableKVState<EntityNumber, Bytecode> bytecode = scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.BYTECODE_KEY);
        final var actualBytecode = bytecode.get(new EntityNumber(ERC721_FULL.accountNumOrThrow()));
        assertNotNull(actualBytecode);
        assertEquals(expectedBytecode(), actualBytecode.code());

        // Assert its storage matches expectations
        final ReadableKVState<SlotKey, SlotValue> storage = scaffoldingComponent
                .hederaState()
                .createReadableStates(ContractServiceImpl.NAME)
                .get(ContractSchema.STORAGE_KEY);
        EXPECTED_STORAGE.forEach((key, value) -> {
            final var slot = storage.get(new SlotKey(ERC721_FULL.accountNumOrThrow(), key));
            assertNotNull(slot);
            assertEquals(value, slot.value());
        });
    }

    private void setupFakeStates() {
        final var fakeHederaState = (FakeHederaState) scaffoldingComponent.hederaState();
        fakeHederaState.addService("RecordCache", Map.of("TransactionRecordQueue", new ArrayDeque<>()));
        fakeHederaState.addService(
                TokenService.NAME,
                Map.of(
                        TokenServiceImpl.ACCOUNTS_KEY, new HashMap<AccountID, Account>(),
                        TokenServiceImpl.ALIASES_KEY, new HashMap<String, Bytes>()));
        fakeHederaState.addService(
                FileServiceImpl.NAME, Map.of(FileServiceImpl.BLOBS_KEY, new HashMap<FileID, File>()));
        fakeHederaState.addService(
                ContractServiceImpl.NAME,
                Map.of(
                        ContractSchema.BYTECODE_KEY, new HashMap<EntityNumber, Bytecode>(),
                        ContractSchema.STORAGE_KEY, new HashMap<SlotKey, SlotValue>()));
        scaffoldingComponent.workingStateAccessor().setHederaState(fakeHederaState);
    }

    private void setupErc721InitcodeAndWellKnownAccounts() throws IOException {
        final WritableKVState<FileID, File> files = scaffoldingComponent
                .hederaState()
                .createWritableStates(FileServiceImpl.NAME)
                .get(FileServiceImpl.BLOBS_KEY);
        files.put(
                ERC721_FULL_INITCODE_FILE_ID,
                File.newBuilder().contents(erc721FullInitcode()).build());
        commitKvStateChanges(files);

        final WritableKVState<AccountID, Account> accounts = scaffoldingComponent
                .hederaState()
                .createWritableStates(TokenService.NAME)
                .get(TokenServiceImpl.ACCOUNTS_KEY);
        for (int i = 0; i < ACCOUNTS.length; i++) {
            final var cryptoKey = ACCOUNT_KEYS[i];
            final var pbjKey = cryptoKey.length() == 33
                    ? Key.newBuilder().ecdsaSecp256k1(cryptoKey).build()
                    : Key.newBuilder().ed25519(cryptoKey).build();
            final var account =
                    Account.newBuilder().key(pbjKey).alias(ACCOUNT_ALIASES[i]).build();
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
        try (final var in = Erc721OperationsTest.class.getClassLoader().getResourceAsStream(loc)) {
            final var bytes = Objects.requireNonNull(in).readAllBytes();
            return Bytes.wrap(bytes);
        }
    }

    private void commitStateChanges() {
        final var tokenState = scaffoldingComponent.hederaState().createWritableStates(TokenServiceImpl.NAME);
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ACCOUNTS_KEY));
        commitKvStateChanges(tokenState.get(TokenServiceImpl.ALIASES_KEY));

        final var contractState = scaffoldingComponent.hederaState().createWritableStates(ContractServiceImpl.NAME);
        commitKvStateChanges(contractState.get(ContractSchema.BYTECODE_KEY));
        commitKvStateChanges(contractState.get(ContractSchema.STORAGE_KEY));
    }

    private void commitKvStateChanges(final WritableKVState<?, ?> state) {
        ((WritableKVStateBase<?, ?>) state).commit();
    }

    private Address addressOf(@NonNull final Bytes address) {
        return Address.wrap(Address.toChecksumAddress(new BigInteger(1, address.toByteArray())));
    }
}
