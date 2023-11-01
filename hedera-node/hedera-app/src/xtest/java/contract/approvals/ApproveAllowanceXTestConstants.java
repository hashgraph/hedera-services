package contract.approvals;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TokenID;

/**
 * See also {@code ApproveAllowanceSuite#hapiNftIsApprovedForAll()} in the {@code test-clients} module.
 */
public class ApproveAllowanceXTestConstants {
    static final FileID HTS_APPROVE_ALLOWANCE_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();
    static final long NEXT_ENTITY_NUM = 1234L;
    static final long GAS = 2_000_000L;

    static final AccountID OWNER_ID = AccountID.newBuilder().accountNum(1003L).build();
    static final AccountID RECIPIENT_ID = AccountID.newBuilder().accountNum(1004L).build();
    static final AccountID ACCOUNT_ID = AccountID.newBuilder().accountNum(1005L).build();
    static final AccountID TOKEN_TREASURY_ID = AccountID.newBuilder().accountNum(1006L).build();
    static final TokenID NFT_TOKEN_TYPE_ID = TokenID.newBuilder().tokenNum(1007L).build();
}
