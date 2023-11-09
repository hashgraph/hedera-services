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
import static contract.AssortedOpsXTestConstants.ASSORTED_OPS_CONTRACT_ID;
import static contract.AssortedOpsXTestConstants.ASSORTED_OPS_ID;
import static contract.AssortedOpsXTestConstants.ASSORTED_OPS_INITCODE_FILE_ID;
import static contract.AssortedOpsXTestConstants.COINBASE_ID;
import static contract.AssortedOpsXTestConstants.DEPLOY_DETERMINISTIC_CHILD;
import static contract.AssortedOpsXTestConstants.DEPLOY_GOLDBERGESQUE;
import static contract.AssortedOpsXTestConstants.DETERMINISTIC_CHILD_ADDRESS;
import static contract.AssortedOpsXTestConstants.ETH_LAZY_CREATE;
import static contract.AssortedOpsXTestConstants.EXPECTED_ASSORTED_OPS_BALANCE;
import static contract.AssortedOpsXTestConstants.EXPECTED_ASSORTED_OPS_NONCE;
import static contract.AssortedOpsXTestConstants.EXPECTED_CHILD_STORAGE;
import static contract.AssortedOpsXTestConstants.EXPECTED_POINTLESS_INTERMEDIARY_STORAGE;
import static contract.AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_CONTRACT_ID;
import static contract.AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_ID;
import static contract.AssortedOpsXTestConstants.NEXT_ENTITY_NUM;
import static contract.AssortedOpsXTestConstants.ONE_HBAR;
import static contract.AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ADDRESS;
import static contract.AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID;
import static contract.AssortedOpsXTestConstants.RELAYER_ID;
import static contract.AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID;
import static contract.AssortedOpsXTestConstants.SALT;
import static contract.AssortedOpsXTestConstants.SENDER_ALIAS;
import static contract.AssortedOpsXTestConstants.TAKE_FIVE;
import static contract.AssortedOpsXTestConstants.VACATE_ADDRESS;
import static contract.XTestConstants.MISC_PAYER_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.contract.EthereumTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * <p>Please see <a href="https://github.com/hashgraph/hedera-services/pull/7868">this PR</a>
 * for the {@code HapiSpec} scenario run against {@code mono-service} to determine the expected
 * values.
 */
public class AssortedOpsXTest extends AbstractContractXTest {
    @Override
    protected void doScenarioOperations() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        handleAndCommit(CONTRACT_SERVICE.handlers().ethereumTransactionHandler(), synthLazyCreateTxn());
        handleAndCommit(
                CONTRACT_SERVICE.handlers().contractCallHandler(),
                synthDeterministicDeploy(),
                synthVacateAddress(),
                synthGoldbergesqueDeploy(),
                synthTakeFive());
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(RELAYER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(ASSORTED_OPS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthLazyCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(RELAYER_ID))
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .ethereumData(ETH_LAZY_CREATE)
                        .maxGasAllowance(10 * ONE_HBAR)
                        .build())
                .build();
    }

    private TransactionBody synthDeterministicDeploy() {
        return createCallTransactionBody(
                MISC_PAYER_ID, ONE_HBAR, ASSORTED_OPS_CONTRACT_ID, DEPLOY_DETERMINISTIC_CHILD.encodeCallWithArgs(SALT));
    }

    private TransactionBody synthGoldbergesqueDeploy() {
        return createCallTransactionBody(
                MISC_PAYER_ID, 2 * ONE_HBAR, ASSORTED_OPS_CONTRACT_ID, DEPLOY_GOLDBERGESQUE.encodeCallWithArgs(SALT));
    }

    private TransactionBody synthVacateAddress() {
        return createCallTransactionBody(
                MISC_PAYER_ID, 0, FINALIZED_AND_DESTRUCTED_CONTRACT_ID, VACATE_ADDRESS.encodeCallWithArgs());
    }

    private TransactionBody synthTakeFive() {
        return createCallTransactionBody(MISC_PAYER_ID, 0, ASSORTED_OPS_CONTRACT_ID, TAKE_FIVE.encodeCallWithArgs());
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                ASSORTED_OPS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/AssortedXTest.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ALIAS).build(), SENDER_ID);
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(SENDER_ID)
                        .alias(SENDER_ALIAS)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(
                RELAYER_ID,
                Account.newBuilder()
                        .accountId(RELAYER_ID)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(
                MISC_PAYER_ID,
                Account.newBuilder()
                        .accountId(MISC_PAYER_ID)
                        .tinybarBalance(100 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    @Override
    protected void assertExpectedStorage(
            @NonNull final ReadableKVState<SlotKey, SlotValue> storage,
            @NonNull final ReadableKVState<AccountID, Account> accounts) {
        final var numExpectedSlots = EXPECTED_CHILD_STORAGE.size() * 2 + EXPECTED_POINTLESS_INTERMEDIARY_STORAGE.size();
        assertEquals(numExpectedSlots, storage.size());

        EXPECTED_CHILD_STORAGE.forEach((key, value) -> {
            final var destructedSlot = storage.get(new SlotKey(FINALIZED_AND_DESTRUCTED_ID.accountNumOrThrow(), key));
            assertNotNull(destructedSlot);
            assertEquals(value, destructedSlot.value());
            final var survivingSlot = storage.get(new SlotKey(RUBE_GOLDBERG_CHILD_ID.accountNumOrThrow(), key));
            assertNotNull(survivingSlot);
            assertEquals(value, survivingSlot.value());
        });
        EXPECTED_POINTLESS_INTERMEDIARY_STORAGE.forEach((key, value) -> {
            final var slot = storage.get(new SlotKey(POINTLESS_INTERMEDIARY_ID.accountNumOrThrow(), key));
            assertNotNull(slot);
            assertEquals(value, slot.value());
        });

        final var assortedOps = Objects.requireNonNull(accounts.get(ASSORTED_OPS_ID));
        assertEquals(0, assortedOps.contractKvPairsNumber());

        final var finalizedAndDestructed = Objects.requireNonNull(accounts.get(FINALIZED_AND_DESTRUCTED_ID));
        assertEquals(1, finalizedAndDestructed.contractKvPairsNumber());

        final var pointlessIntermediary = Objects.requireNonNull(accounts.get(POINTLESS_INTERMEDIARY_ID));
        assertEquals(2, pointlessIntermediary.contractKvPairsNumber());

        final var survivingChild = Objects.requireNonNull(accounts.get(RUBE_GOLDBERG_CHILD_ID));
        assertEquals(1, survivingChild.contractKvPairsNumber());
    }

    @Override
    protected void assertExpectedAliases(@NonNull final ReadableKVState<ProtoBytes, AccountID> aliases) {
        assertEquals(
                POINTLESS_INTERMEDIARY_ID,
                aliases.get(ProtoBytes.newBuilder()
                        .value(POINTLESS_INTERMEDIARY_ADDRESS)
                        .build()));
        assertEquals(
                RUBE_GOLDBERG_CHILD_ID,
                aliases.get(ProtoBytes.newBuilder()
                        .value(DETERMINISTIC_CHILD_ADDRESS)
                        .build()));
    }

    @Override
    protected void assertExpectedAccounts(@NonNull final ReadableKVState<AccountID, Account> accounts) {
        final var assortedOps = accounts.get(ASSORTED_OPS_ID);
        assertNotNull(assortedOps);
        assertTrue(assortedOps.smartContract());
        assertEquals(EXPECTED_ASSORTED_OPS_BALANCE, assortedOps.tinybarBalance());
        assertEquals(EXPECTED_ASSORTED_OPS_NONCE, assortedOps.ethereumNonce());

        final var finalizedAndDestructed = accounts.get(FINALIZED_AND_DESTRUCTED_ID);
        assertNotNull(finalizedAndDestructed);
        assertTrue(finalizedAndDestructed.smartContract());
        assertTrue(finalizedAndDestructed.deleted());
        assertEquals(Bytes.EMPTY, finalizedAndDestructed.alias());
        assertEquals(0, finalizedAndDestructed.tinybarBalance());
        assertEquals(1, finalizedAndDestructed.ethereumNonce());

        final var pointlessIntermediary = accounts.get(POINTLESS_INTERMEDIARY_ID);
        assertNotNull(pointlessIntermediary);
        assertTrue(pointlessIntermediary.smartContract());
        assertEquals(POINTLESS_INTERMEDIARY_ADDRESS, pointlessIntermediary.alias());
        assertEquals(0, pointlessIntermediary.tinybarBalance());
        assertEquals(1, pointlessIntermediary.ethereumNonce());

        final var secondChild = accounts.get(RUBE_GOLDBERG_CHILD_ID);
        assertNotNull(secondChild);
        assertTrue(secondChild.smartContract());
        assertFalse(secondChild.deleted());
        assertEquals(DETERMINISTIC_CHILD_ADDRESS, secondChild.alias());
        assertEquals(2 * ONE_HBAR, secondChild.tinybarBalance());
        assertEquals(1, secondChild.ethereumNonce());
    }

    @Override
    protected void assertExpectedBytecodes(@NonNull final ReadableKVState<EntityNumber, Bytecode> bytecodes) {
        final var actualAssortedBytecode = bytecodes.get(new EntityNumber(ASSORTED_OPS_ID.accountNumOrThrow()));
        assertNotNull(actualAssortedBytecode);
        assertEquals(resourceAsBytes("bytecode/AssortedXTest.bin"), actualAssortedBytecode.code());

        final var actualDestructed = bytecodes.get(new EntityNumber(FINALIZED_AND_DESTRUCTED_ID.accountNumOrThrow()));
        assertNotNull(actualDestructed);
        assertEquals(resourceAsBytes("bytecode/DeterministicChild.bin"), actualDestructed.code());

        final var secondChild = bytecodes.get(new EntityNumber(RUBE_GOLDBERG_CHILD_ID.accountNumOrThrow()));
        assertNotNull(secondChild);
        assertEquals(resourceAsBytes("bytecode/DeterministicChild.bin"), secondChild.code());

        final var pointlessIntermediary =
                bytecodes.get(new EntityNumber(POINTLESS_INTERMEDIARY_ID.accountNumOrThrow()));
        assertNotNull(pointlessIntermediary);
        assertEquals(resourceAsBytes("bytecode/PointlessIntermediary.bin"), pointlessIntermediary.code());
    }
}
