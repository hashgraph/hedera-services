package contract;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public class AssortedOpsXTestConstants {
    static final long NEXT_ENTITY_NUM = 1004L;
    static final long ONE_HBAR = 100_000_000L;
    static final long LAZY_CREATION_AMOUNT = ONE_HBAR;
    static final long FINALIZATION_AMOUNT = ONE_HBAR;
    static final long SENDER_START_BALANCE = 100 * ONE_HBAR;
    static final long TAKE_FIVE_AMOUNT = 5;
    static final long EXPECTED_ASSORTED_OPS_NONCE = 4;
    static final Bytes SENDER_ALIAS = Bytes.fromHex("3a21030edcc130e13fb5102e7c883535af8c2b0a5a617231f77fd127ce5f3b9a620591");
    static final Bytes POINTLESS_INTERMEDIARY_ADDRESS = Bytes.fromHex("f9f3aa959ec3a248f8ff8ea1602e6714ae9cc4e3");
    static final Bytes DETERMINISTIC_CHILD_ADDRESS = Bytes.fromHex("fee687d5088faff48013a6767505c027e2742536");
    static final AccountID COINBASE_ID = AccountID.newBuilder().accountNum(98L).build();
    static final AccountID MISC_PAYER_ID = AccountID.newBuilder().accountNum(2L).build();
    static final AccountID RELAYER_ID = AccountID.newBuilder().accountNum(1001L).build();
    static final AccountID SENDER_ID = AccountID.newBuilder().accountNum(1002L).build();
    static final AccountID ASSORTED_OPS_ID = AccountID.newBuilder().accountNum(1004L).build();
    static final AccountID FINALIZED_AND_DESTRUCTED_ID = AccountID.newBuilder().accountNum(1005L).build();
    static final AccountID POINTLESS_INTERMEDIARY_ID = AccountID.newBuilder().accountNum(1007L).build();
    static final AccountID RUBE_GOLDBERG_CHILD_ID = AccountID.newBuilder().accountNum(1008L).build();
    static final FileID ASSORTED_OPS_INITCODE_FILE_ID = new FileID(0, 0, 1003);
}
