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

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.AssortedOpsXTestConstants.ONE_HBAR;
import static contract.AssortedOpsXTestConstants.SENDER_ALIAS;
import static contract.Erc721XTestConstants.COINBASE_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_ID;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomContractCreationProcessor;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.Map;

/**
 * Small test verifying behavior of the {@link CustomContractCreationProcessor} when the number of
 * bytecodes in state already equals the configured limit.
 */
public class ContractLimitsXTest extends AbstractContractXTest {
    static final long GAS = 300_000L;
    static final long NEXT_ENTITY_NUM = 1234L;
    private static final FileID FUSE_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();

    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(
                CONTRACT_SERVICE.handlers().contractCreateHandler(),
                synthCreateTxn(),
                MAX_ENTITIES_IN_PRICE_REGIME_HAVE_BEEN_CREATED);
    }

    @Override
    protected Configuration configuration() {
        // Override to set the max number of contracts to 2 (since we already have 2 bytecode, no more can be created)
        return HederaTestConfigBuilder.create()
                .withValue("contracts.chainId", "298")
                .withValue("contracts.maxNumber", "2")
                .getOrCreateConfig();
    }

    @Override
    protected long initialEntityNum() {
        return NEXT_ENTITY_NUM - 1;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                FUSE_INITCODE_ID,
                File.newBuilder().contents(resourceAsBytes("initcode/Fuse.bin")).build());
        return files;
    }

    @Override
    protected Map<EntityNumber, Bytecode> initialBytecodes() {
        final var bytecodes = super.initialBytecodes();
        bytecodes.put(new EntityNumber(234567890), new Bytecode(Bytes.wrap("NONSENSICAL")));
        bytecodes.put(new EntityNumber(123456789), new Bytecode(Bytes.wrap("PLACEHOLDER")));
        return bytecodes;
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
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(FUSE_INITCODE_ID)
                        .gas(GAS)
                        .build())
                .build();
    }
}
