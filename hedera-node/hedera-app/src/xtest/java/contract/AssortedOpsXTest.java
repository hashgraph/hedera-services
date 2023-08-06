package contract;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TransactionID;
import com.hedera.hapi.node.contract.ContractCreateTransactionBody;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;

import java.util.HashMap;
import java.util.Map;

import static com.hedera.node.app.service.contract.impl.ContractServiceImpl.CONTRACT_SERVICE;
import static contract.AssortedOpsXTestConstants.ASSORTED_OPS_ID;
import static contract.AssortedOpsXTestConstants.ASSORTED_OPS_INITCODE_FILE_ID;
import static contract.AssortedOpsXTestConstants.COINBASE_ID;
import static contract.AssortedOpsXTestConstants.NEXT_ENTITY_NUM;
import static contract.AssortedOpsXTestConstants.ONE_HBAR;
import static contract.AssortedOpsXTestConstants.MISC_PAYER_ID;
import static contract.AssortedOpsXTestConstants.RELAYER_ID;
import static contract.AssortedOpsXTestConstants.SENDER_ALIAS;
import static contract.AssortedOpsXTestConstants.SENDER_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class AssortedOpsXTest extends AbstractContractXTest {
    @Override
    protected void handleAndCommitScenarioTransactions() {
        handleAndCommit(CONTRACT_SERVICE.handlers().contractCreateHandler(), synthCreateTxn());
    }

    private TransactionBody synthCreateTxn() {
        return TransactionBody.newBuilder()
                .transactionID(TransactionID.newBuilder().accountID(MISC_PAYER_ID))
                .contractCreateInstance(ContractCreateTransactionBody.newBuilder()
                        .autoRenewPeriod(STANDARD_AUTO_RENEW_PERIOD)
                        .fileID(ASSORTED_OPS_INITCODE_FILE_ID)
                        .gas(GAS_TO_OFFER)
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
                ASSORTED_OPS_INITCODE_FILE_ID,
                File.newBuilder().contents(resourceAsBytes("initcode/AssortedXTest.bin")).build());
        return files;
    }

    @Override
    protected Map<Bytes, AccountID> initialAliases() {
        final var aliases = new HashMap<Bytes, AccountID>();
        aliases.put(SENDER_ALIAS, SENDER_ID);
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
    protected void assertExpectedStorage(@NonNull final ReadableKVState<SlotKey, SlotValue> storage, @NonNull final ReadableKVState<AccountID, Account> accounts) {

    }

    @Override
    protected void assertExpectedAliases(@NonNull final ReadableKVState<Bytes, AccountID> aliases) {

    }

    @Override
    protected void assertExpectedAccounts(@NonNull final ReadableKVState<AccountID, Account> accounts) {

    }

    @Override
    protected void assertExpectedBytecodes(@NonNull final ReadableKVState<EntityNumber, Bytecode> bytecodes) {
        final var actualAssortedBytecode = bytecodes.get(new EntityNumber(ASSORTED_OPS_ID.accountNumOrThrow()));
        assertNotNull(actualAssortedBytecode);
        assertEquals(resourceAsBytes("bytecode/AssortedXTest.bin"), actualAssortedBytecode.code());
    }
}
