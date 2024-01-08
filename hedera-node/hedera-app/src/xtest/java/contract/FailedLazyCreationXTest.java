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

import static com.hedera.hapi.node.base.ResponseCodeEnum.MAX_CHILD_RECORDS_EXCEEDED;
import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.XTestConstants.COINBASE_ID;
import static contract.XTestConstants.LAZY_CREATE_TARGET_1_HEADLONG_ADDRESS;
import static contract.XTestConstants.LAZY_CREATE_TARGET_2_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCallTransactionBody;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.processors.CustomMessageCallProcessor;
import com.hedera.node.config.testfixtures.HederaTestConfigBuilder;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.swirlds.config.api.Configuration;
import java.util.HashMap;
import java.util.Map;

/**
 * Test verifying behavior of the {@link CustomMessageCallProcessor} when a lazy-creation fails
 * due to insufficient preceding child records.
 */
public class FailedLazyCreationXTest extends AbstractContractXTest {
    private static final long NEXT_ENTITY_NUM = 1234L;
    private static final long GAS = 6_000_000L;
    private static final FileID NESTED_LAZY_CREATE_CONTRACT_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();
    private static final Function CREATE_TOO_MANY_HOLLOW_ACCOUNTS =
            new Function("createTooManyHollowAccounts(address[])");

    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        handleAndCommitSingleTransaction(
                CONTRACT_SERVICE.handlers().contractCallHandler(), synthCallTxn(), MAX_CHILD_RECORDS_EXCEEDED);
    }

    private TransactionBody synthCallTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCall(ContractCallTransactionBody.newBuilder()
                        .contractID(ContractID.newBuilder()
                                .contractNum(NEXT_ENTITY_NUM)
                                .build())
                        .gas(GAS)
                        .amount(1000L)
                        .functionParameters(Bytes.wrap(CREATE_TOO_MANY_HOLLOW_ACCOUNTS
                                .encodeCallWithArgs((Object) new Address[] {
                                    LAZY_CREATE_TARGET_1_HEADLONG_ADDRESS, LAZY_CREATE_TARGET_2_HEADLONG_ADDRESS
                                })
                                .array()))
                        .build())
                .build();
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(SENDER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(NESTED_LAZY_CREATE_CONTRACT_INITCODE_ID)
                        .gas(GAS)
                        .build())
                .build();
    }

    @Override
    protected Configuration configuration() {
        // Override to set the max number of preceding children to 1
        return HederaTestConfigBuilder.create()
                .withValue("consensus.handle.maxPrecedingRecords", "1")
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
                NESTED_LAZY_CREATE_CONTRACT_INITCODE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/NestedLazyCreateContract.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        return withSenderAddress(new HashMap<>());
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = withSenderAccount(new HashMap<>());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }
}
