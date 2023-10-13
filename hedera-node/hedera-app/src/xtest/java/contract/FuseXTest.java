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
import static contract.AssortedOpsXTestConstants.ONE_HBAR;
import static contract.AssortedOpsXTestConstants.SENDER_ALIAS;
import static contract.Erc721XTestConstants.COINBASE_ID;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FuseXTest extends AbstractContractXTest {
    static final long GAS = 300_000L;
    static final long NEXT_ENTITY_NUM = 1234L;
    private static final FileID FUSE_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();

    @Override
    protected void doScenarioOperations() {
        final var recordBuilder =
                handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
        // We expect 4 new contracts created in Fuse constructor
        assertEquals(
                List.of(
                        ContractID.newBuilder().contractNum(NEXT_ENTITY_NUM).build(),
                        ContractID.newBuilder().contractNum(NEXT_ENTITY_NUM + 1).build(),
                        ContractID.newBuilder().contractNum(NEXT_ENTITY_NUM + 2).build(),
                        ContractID.newBuilder().contractNum(NEXT_ENTITY_NUM + 3).build()),
                recordBuilder.contractFunctionResult().createdContractIDs());
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
}
