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

package contract;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.Erc721XTestConstants.COUNTERPARTY_ADDRESS;
import static contract.Erc721XTestConstants.COUNTERPARTY_ID;
import static contract.Erc721XTestConstants.ERC721_FULL_CONTRACT_ID;
import static contract.Erc721XTestConstants.ERC721_FULL_ID;
import static contract.Erc721XTestConstants.ERC721_FULL_INITCODE_FILE_ID;
import static contract.Erc721XTestConstants.EXPECTED_STORAGE;
import static contract.Erc721XTestConstants.INITIAL_BALANCE;
import static contract.Erc721XTestConstants.NEXT_ENTITY_NUM;
import static contract.Erc721XTestConstants.OPERATOR_ADDRESS;
import static contract.Erc721XTestConstants.OPERATOR_ID;
import static contract.Erc721XTestConstants.PARTY_ADDRESS;
import static contract.Erc721XTestConstants.PARTY_ID;
import static contract.Erc721XTestConstants.TOKEN_TREASURY_ADDRESS;
import static contract.Erc721XTestConstants.TOKEN_TREASURY_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.COINBASE_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.platform.state.spi.ReadableKVState;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.jetbrains.annotations.NotNull;

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
public class Erc721XTest extends AbstractContractXTest {
    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected void doScenarioOperations() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        handleAndCommit(
                CONTRACT_SERVICE.handlers().contractCallHandler(),
                synthApproveTxn(TOKEN_TREASURY_ID, PARTY_ADDRESS, 2),
                synthApproveTxn(TOKEN_TREASURY_ID, PARTY_ADDRESS, 3),
                synthSetApprovalForAll(TOKEN_TREASURY_ID, OPERATOR_ADDRESS, true),
                synthSafeTransferFrom(OPERATOR_ID, TOKEN_TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 13),
                synthSafeTransferFrom(PARTY_ID, TOKEN_TREASURY_ADDRESS, COUNTERPARTY_ADDRESS, 3),
                synthSafeTransferFrom(TOKEN_TREASURY_ID, TOKEN_TREASURY_ADDRESS, PARTY_ADDRESS, 8),
                synthSafeTransferFrom(COUNTERPARTY_ID, COUNTERPARTY_ADDRESS, PARTY_ADDRESS, 3));
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(TOKEN_TREASURY_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(Erc721XTestConstants.ERC721_FULL_INITCODE_FILE_ID)
                        .gas(AbstractContractXTest.GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthApproveTxn(
            @NonNull final AccountID payer, @NonNull final Bytes spender, long serialNum) {
        final var encoded =
                Erc721XTestConstants.APPROVE.encodeCallWithArgs(addressOf(spender), BigInteger.valueOf(serialNum));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSafeTransferFrom(
            @NonNull final AccountID payer, @NonNull final Bytes from, @NonNull final Bytes to, final long serialNo) {
        final var encoded = Erc721XTestConstants.SAFE_TRANSFER_FROM.encodeCallWithArgs(
                addressOf(from), addressOf(to), BigInteger.valueOf(serialNo));
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private TransactionBody synthSetApprovalForAll(
            @NonNull final AccountID payer, @NonNull final Bytes operator, final boolean approved) {
        final var encoded = Erc721XTestConstants.SET_APPROVAL_FOR_ALL.encodeCallWithArgs(addressOf(operator), approved);
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(payer))
                .contractCall(callWithParams(encoded))
                .build();
    }

    private ContractCallTransactionBody callWithParams(@NonNull final ByteBuffer encoded) {
        return ContractCallTransactionBody.newBuilder()
                .functionParameters(Bytes.wrap(encoded.array()))
                .contractID(ERC721_FULL_CONTRACT_ID)
                .gas(AbstractContractXTest.GAS_TO_OFFER)
                .build();
    }

    @Override
    protected void assertExpectedStorage(
            @NotNull final ReadableKVState<SlotKey, SlotValue> storage,
            @NotNull final ReadableKVState<AccountID, Account> accounts) {
        assertEquals(EXPECTED_STORAGE.size(), storage.size());
        EXPECTED_STORAGE.forEach((key, value) -> {
            final var slot = storage.get(new SlotKey(ERC721_FULL_CONTRACT_ID, key));
            assertNotNull(slot);
            assertEquals(value, slot.value());
        });
        final var contract = Objects.requireNonNull(accounts.get(ERC721_FULL_ID));
        assertEquals(EXPECTED_STORAGE.size(), contract.contractKvPairsNumber());
    }

    @Override
    protected void assertExpectedAliases(@NotNull final ReadableKVState<ProtoBytes, AccountID> aliases) {
        // No aliases change in this test
    }

    @Override
    protected void assertExpectedAccounts(@NotNull final ReadableKVState<AccountID, Account> accounts) {
        final var contract = accounts.get(ERC721_FULL_ID);
        assertNotNull(contract);
        assertTrue(contract.smartContract());
    }

    @Override
    protected void assertExpectedBytecodes(@NotNull final ReadableKVState<EntityNumber, Bytecode> bytecodes) {
        final var actualBytecode = bytecodes.get(new EntityNumber(ERC721_FULL_ID.accountNumOrThrow()));
        assertNotNull(actualBytecode);
        assertEquals(resourceAsBytes("bytecode/ERC721Full.bin"), actualBytecode.code());
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                TOKEN_TREASURY_ID,
                Account.newBuilder()
                        .accountId(TOKEN_TREASURY_ID)
                        .alias(TOKEN_TREASURY_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                COUNTERPARTY_ID,
                Account.newBuilder()
                        .accountId(COUNTERPARTY_ID)
                        .alias(COUNTERPARTY_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                OPERATOR_ID,
                Account.newBuilder()
                        .accountId(OPERATOR_ID)
                        .alias(OPERATOR_ADDRESS)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(
                PARTY_ID,
                Account.newBuilder()
                        .accountId(PARTY_ID)
                        .key(AN_ED25519_KEY)
                        .tinybarBalance(INITIAL_BALANCE)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(TOKEN_TREASURY_ADDRESS).build(), TOKEN_TREASURY_ID);
        aliases.put(ProtoBytes.newBuilder().value(COUNTERPARTY_ADDRESS).build(), COUNTERPARTY_ID);
        aliases.put(ProtoBytes.newBuilder().value(OPERATOR_ADDRESS).build(), OPERATOR_ID);
        return aliases;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                ERC721_FULL_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/ERC721Full.bin"))
                        .build());
        return files;
    }
}
