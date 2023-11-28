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

package com.hedera.node.app.xtest.contract;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
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
import org.junit.jupiter.api.Assertions;

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
                .transactionID(TransactionID.newBuilder().accountID(AssortedOpsXTestConstants.RELAYER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(AssortedOpsXTestConstants.ASSORTED_OPS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
                        .build())
                .build();
    }

    private TransactionBody synthLazyCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(AssortedOpsXTestConstants.RELAYER_ID))
                .ethereumTransaction(EthereumTransactionBody.newBuilder()
                        .ethereumData(AssortedOpsXTestConstants.ETH_LAZY_CREATE)
                        .maxGasAllowance(10 * XTestConstants.ONE_HBAR)
                        .build())
                .build();
    }

    private TransactionBody synthDeterministicDeploy() {
        return createCallTransactionBody(
                XTestConstants.MISC_PAYER_ID,
                XTestConstants.ONE_HBAR,
                AssortedOpsXTestConstants.ASSORTED_OPS_CONTRACT_ID,
                AssortedOpsXTestConstants.DEPLOY_DETERMINISTIC_CHILD.encodeCallWithArgs(
                        AssortedOpsXTestConstants.SALT));
    }

    private TransactionBody synthGoldbergesqueDeploy() {
        return createCallTransactionBody(
                XTestConstants.MISC_PAYER_ID,
                2 * XTestConstants.ONE_HBAR,
                AssortedOpsXTestConstants.ASSORTED_OPS_CONTRACT_ID,
                AssortedOpsXTestConstants.DEPLOY_GOLDBERGESQUE.encodeCallWithArgs(AssortedOpsXTestConstants.SALT));
    }

    private TransactionBody synthVacateAddress() {
        return createCallTransactionBody(
                XTestConstants.MISC_PAYER_ID,
                0,
                AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_CONTRACT_ID,
                AssortedOpsXTestConstants.VACATE_ADDRESS.encodeCallWithArgs());
    }

    private TransactionBody synthTakeFive() {
        return createCallTransactionBody(
                XTestConstants.MISC_PAYER_ID,
                0,
                AssortedOpsXTestConstants.ASSORTED_OPS_CONTRACT_ID,
                AssortedOpsXTestConstants.TAKE_FIVE.encodeCallWithArgs());
    }

    @Override
    protected long initialEntityNum() {
        return AssortedOpsXTestConstants.NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                AssortedOpsXTestConstants.ASSORTED_OPS_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/AssortedXTest.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = withSenderAlias(new HashMap<>());
        aliases.put(ProtoBytes.newBuilder().value(XTestConstants.SENDER_ADDRESS).build(), XTestConstants.SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderAccount(new HashMap<>());
        accounts.put(
                AssortedOpsXTestConstants.RELAYER_ID,
                Account.newBuilder()
                        .accountId(AssortedOpsXTestConstants.RELAYER_ID)
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .build());
        accounts.put(
                XTestConstants.MISC_PAYER_ID,
                Account.newBuilder()
                        .accountId(XTestConstants.MISC_PAYER_ID)
                        .tinybarBalance(100 * XTestConstants.ONE_HBAR)
                        .key(XTestConstants.AN_ED25519_KEY)
                        .build());
        accounts.put(
                AssortedOpsXTestConstants.COINBASE_ID,
                Account.newBuilder()
                        .accountId(AssortedOpsXTestConstants.COINBASE_ID)
                        .build());
        return accounts;
    }

    @Override
    protected void assertExpectedStorage(
            @NonNull final ReadableKVState<SlotKey, SlotValue> storage,
            @NonNull final ReadableKVState<AccountID, Account> accounts) {
        final var numExpectedSlots = AssortedOpsXTestConstants.EXPECTED_CHILD_STORAGE.size() * 2
                + AssortedOpsXTestConstants.EXPECTED_POINTLESS_INTERMEDIARY_STORAGE.size();
        assertEquals(numExpectedSlots, storage.size());

        AssortedOpsXTestConstants.EXPECTED_CHILD_STORAGE.forEach((key, value) -> {
            final var destructedSlot = storage.get(
                    new SlotKey(AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_ID.accountNumOrThrow(), key));
            assertNotNull(destructedSlot);
            assertEquals(value, destructedSlot.value());
            final var survivingSlot =
                    storage.get(new SlotKey(AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID.accountNumOrThrow(), key));
            assertNotNull(survivingSlot);
            assertEquals(value, survivingSlot.value());
        });
        AssortedOpsXTestConstants.EXPECTED_POINTLESS_INTERMEDIARY_STORAGE.forEach((key, value) -> {
            final var slot = storage.get(
                    new SlotKey(AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID.accountNumOrThrow(), key));
            assertNotNull(slot);
            assertEquals(value, slot.value());
        });

        final var assortedOps = Objects.requireNonNull(accounts.get(AssortedOpsXTestConstants.ASSORTED_OPS_ID));
        assertEquals(0, assortedOps.contractKvPairsNumber());

        final var finalizedAndDestructed =
                Objects.requireNonNull(accounts.get(AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_ID));
        assertEquals(1, finalizedAndDestructed.contractKvPairsNumber());

        final var pointlessIntermediary =
                Objects.requireNonNull(accounts.get(AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID));
        assertEquals(2, pointlessIntermediary.contractKvPairsNumber());

        final var survivingChild =
                Objects.requireNonNull(accounts.get(AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID));
        assertEquals(1, survivingChild.contractKvPairsNumber());
    }

    @Override
    protected void assertExpectedAliases(@NonNull final ReadableKVState<ProtoBytes, AccountID> aliases) {
        Assertions.assertEquals(
                AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID,
                aliases.get(ProtoBytes.newBuilder()
                        .value(AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ADDRESS)
                        .build()));
        Assertions.assertEquals(
                AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID,
                aliases.get(ProtoBytes.newBuilder()
                        .value(AssortedOpsXTestConstants.DETERMINISTIC_CHILD_ADDRESS)
                        .build()));
    }

    @Override
    protected void assertExpectedAccounts(@NonNull final ReadableKVState<AccountID, Account> accounts) {
        final var assortedOps = accounts.get(AssortedOpsXTestConstants.ASSORTED_OPS_ID);
        assertNotNull(assortedOps);
        assertTrue(assortedOps.smartContract());
        Assertions.assertEquals(AssortedOpsXTestConstants.EXPECTED_ASSORTED_OPS_BALANCE, assortedOps.tinybarBalance());
        Assertions.assertEquals(AssortedOpsXTestConstants.EXPECTED_ASSORTED_OPS_NONCE, assortedOps.ethereumNonce());

        final var finalizedAndDestructed = accounts.get(AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_ID);
        assertNotNull(finalizedAndDestructed);
        assertTrue(finalizedAndDestructed.smartContract());
        assertTrue(finalizedAndDestructed.deleted());
        assertEquals(Bytes.EMPTY, finalizedAndDestructed.alias());
        assertEquals(0, finalizedAndDestructed.tinybarBalance());
        assertEquals(1, finalizedAndDestructed.ethereumNonce());

        final var pointlessIntermediary = accounts.get(AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID);
        assertNotNull(pointlessIntermediary);
        assertTrue(pointlessIntermediary.smartContract());
        Assertions.assertEquals(
                AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ADDRESS, pointlessIntermediary.alias());
        assertEquals(0, pointlessIntermediary.tinybarBalance());
        assertEquals(1, pointlessIntermediary.ethereumNonce());

        final var secondChild = accounts.get(AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID);
        assertNotNull(secondChild);
        assertTrue(secondChild.smartContract());
        assertFalse(secondChild.deleted());
        Assertions.assertEquals(AssortedOpsXTestConstants.DETERMINISTIC_CHILD_ADDRESS, secondChild.alias());
        Assertions.assertEquals(2 * XTestConstants.ONE_HBAR, secondChild.tinybarBalance());
        assertEquals(1, secondChild.ethereumNonce());
    }

    @Override
    protected void assertExpectedBytecodes(@NonNull final ReadableKVState<EntityNumber, Bytecode> bytecodes) {
        final var actualAssortedBytecode =
                bytecodes.get(new EntityNumber(AssortedOpsXTestConstants.ASSORTED_OPS_ID.accountNumOrThrow()));
        assertNotNull(actualAssortedBytecode);
        assertEquals(resourceAsBytes("bytecode/AssortedXTest.bin"), actualAssortedBytecode.code());

        final var actualDestructed = bytecodes.get(
                new EntityNumber(AssortedOpsXTestConstants.FINALIZED_AND_DESTRUCTED_ID.accountNumOrThrow()));
        assertNotNull(actualDestructed);
        assertEquals(resourceAsBytes("bytecode/DeterministicChild.bin"), actualDestructed.code());

        final var secondChild =
                bytecodes.get(new EntityNumber(AssortedOpsXTestConstants.RUBE_GOLDBERG_CHILD_ID.accountNumOrThrow()));
        assertNotNull(secondChild);
        assertEquals(resourceAsBytes("bytecode/DeterministicChild.bin"), secondChild.code());

        final var pointlessIntermediary = bytecodes.get(
                new EntityNumber(AssortedOpsXTestConstants.POINTLESS_INTERMEDIARY_ID.accountNumOrThrow()));
        assertNotNull(pointlessIntermediary);
        assertEquals(resourceAsBytes("bytecode/PointlessIntermediary.bin"), pointlessIntermediary.code());
    }
}
