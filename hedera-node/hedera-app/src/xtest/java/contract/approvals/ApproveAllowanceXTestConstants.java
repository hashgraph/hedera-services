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

package contract.approvals;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.TokenID;

/**
 * See also {@code ApproveAllowanceSuite#hapiNftIsApprovedForAll()} in the {@code test-clients} module.
 */
public class ApproveAllowanceXTestConstants {
    static final FileID HTS_APPROVE_ALLOWANCE_INITCODE_ID =
            FileID.newBuilder().fileNum(1002L).build();
    static final long NEXT_ENTITY_NUM = 1234L;
    static final ContractID EXPECTED_HTS_APPROVE_ALLOWANCE_CONTRACT_ID =
            ContractID.newBuilder().contractNum(NEXT_ENTITY_NUM).build();
    static final long GAS = 2_000_000L;

    static final AccountID OWNER_ID = AccountID.newBuilder().accountNum(1003L).build();
    static final AccountID RECIPIENT_ID =
            AccountID.newBuilder().accountNum(1004L).build();
    static final AccountID ACCOUNT_ID = AccountID.newBuilder().accountNum(1005L).build();
    static final AccountID TOKEN_TREASURY_ID =
            AccountID.newBuilder().accountNum(1006L).build();
    static final TokenID NFT_TOKEN_TYPE_ID =
            TokenID.newBuilder().tokenNum(1007L).build();

    static final Function IS_APPROVED_FOR_ALL_FUNCTION =
            new Function("htsIsApprovedForAll(address,address,address)", "(bool)");
}
