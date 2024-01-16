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
import static contract.AssortedOpsXTestConstants.COINBASE_ID;
import static contract.XTestConstants.AN_ED25519_KEY;
import static contract.XTestConstants.ERC20_TOKEN_ADDRESS;
import static contract.XTestConstants.ERC20_TOKEN_ID;
import static contract.XTestConstants.ONE_HBAR;
import static contract.XTestConstants.SENDER_ADDRESS;
import static contract.XTestConstants.SENDER_HEADLONG_ADDRESS;
import static contract.XTestConstants.SENDER_ID;
import static contract.XTestConstants.THREE_MONTHS_IN_SECONDS;

import com.esaulpaugh.headlong.abi.TupleType;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TokenID;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.primitives.ProtoBytes;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.state.token.Token;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

public class TestApproverXTest extends AbstractContractXTest {
    public static final long APPROVAL_TOKEN_BALANCE = 1000L;
    public static final long CREATE_GAS = 5_000_000L;
    private static final FileID TEST_APPROVER_INITCODE_FILE_ID = new FileID(0, 0, 1003);
    private static final TupleType INPUTS = TupleType.of("address", "address");

    @Override
    protected void doScenarioOperations() {
        handleAndCommitSingleTransaction(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
    }

    private TransactionBody synthCreateTxn() {
        final var params = INPUTS.encodeElements(ERC20_TOKEN_ADDRESS, SENDER_HEADLONG_ADDRESS)
                .array();
        return TransactionBody.newBuilder()
                .transactionID(transactionIdWith(SENDER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .constructorParameters(Bytes.wrap(params))
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(TEST_APPROVER_INITCODE_FILE_ID)
                        .gas(CREATE_GAS)
                        .build())
                .build();
    }

    @Override
    protected long initialEntityNum() {
        return 123456L;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        final var files = new HashMap<FileID, File>();
        files.put(
                TEST_APPROVER_INITCODE_FILE_ID,
                File.newBuilder()
                        .contents(resourceAsBytes("initcode/TestApprover.bin"))
                        .build());
        return files;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        final var accounts = new HashMap<AccountID, Account>();
        accounts.put(
                SENDER_ID,
                Account.newBuilder()
                        .accountId(SENDER_ID)
                        .expirationSecond(Instant.now().getEpochSecond() + THREE_MONTHS_IN_SECONDS)
                        .key(AN_ED25519_KEY)
                        .alias(SENDER_ADDRESS)
                        .tinybarBalance(123 * 100 * ONE_HBAR)
                        .build());
        accounts.put(COINBASE_ID, Account.newBuilder().accountId(COINBASE_ID).build());
        return accounts;
    }

    @Override
    protected Map<ProtoBytes, AccountID> initialAliases() {
        final var aliases = new HashMap<ProtoBytes, AccountID>();
        aliases.put(ProtoBytes.newBuilder().value(SENDER_ADDRESS).build(), SENDER_ID);
        return aliases;
    }

    @Override
    protected Map<TokenID, Token> initialTokens() {
        final var tokens = new HashMap<TokenID, Token>();
        tokens.put(
                ERC20_TOKEN_ID,
                Token.newBuilder()
                        .tokenId(ERC20_TOKEN_ID)
                        .treasuryAccountId(SENDER_ID)
                        .tokenType(TokenType.FUNGIBLE_COMMON)
                        .supplyKey(AN_ED25519_KEY)
                        .totalSupply(APPROVAL_TOKEN_BALANCE)
                        .build());
        return tokens;
    }
}
