package contract;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.state.common.EntityNumber;
import com.hedera.hapi.node.state.contract.Bytecode;
import com.hedera.hapi.node.state.contract.SlotKey;
import com.hedera.hapi.node.state.contract.SlotValue;
import com.hedera.hapi.node.state.file.File;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.node.app.spi.state.ReadableKVState;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

public class AssortedOpsXTest extends AbstractContractXTest {
    @Override
    protected long initialEntityNum() {
        return 0;
    }

    @Override
    protected Map<FileID, File> initialFiles() {
        return null;
    }

    @Override
    protected Map<Bytes, AccountID> initialAliases() {
        return null;
    }

    @Override
    protected Map<AccountID, Account> initialAccounts() {
        return null;
    }

    @Override
    protected void handleAndCommitScenarioTransactions() {

    }

    @Override
    protected void assertExpectedStorage(@NotNull ReadableKVState<SlotKey, SlotValue> storage, @NotNull ReadableKVState<AccountID, Account> accounts) {

    }

    @Override
    protected void assertExpectedAliases(@NotNull ReadableKVState<Bytes, AccountID> aliases) {

    }

    @Override
    protected void assertExpectedAccounts(@NotNull ReadableKVState<AccountID, Account> accounts) {

    }

    @Override
    protected void assertExpectedBytecodes(@NotNull ReadableKVState<EntityNumber, Bytecode> bytecodes) {

    }
}
