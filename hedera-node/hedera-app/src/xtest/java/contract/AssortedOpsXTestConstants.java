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

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;
import java.util.Map;

/**
 * Constants used in the {@link AssortedOpsXTest}, extracted here to improve readability of that file.
 */
public class AssortedOpsXTestConstants {
    static final long NEXT_ENTITY_NUM = 1004L;
    static final long EXPECTED_ASSORTED_OPS_NONCE = 4;
    static final long EXPECTED_ASSORTED_OPS_BALANCE = 2 * XTestConstants.ONE_HBAR - 5;
    static final Bytes POINTLESS_INTERMEDIARY_ADDRESS = Bytes.fromHex("f9f3aa959ec3a248f8ff8ea1602e6714ae9cc4e3");
    static final Bytes DETERMINISTIC_CHILD_ADDRESS = Bytes.fromHex("fee687d5088faff48013a6767505c027e2742536");
    static final AccountID COINBASE_ID = AccountID.newBuilder().accountNum(98L).build();
    static final AccountID RELAYER_ID = AccountID.newBuilder().accountNum(1001L).build();
    static final AccountID ASSORTED_OPS_ID =
            AccountID.newBuilder().accountNum(1004L).build();
    static final ContractID ASSORTED_OPS_CONTRACT_ID =
            ContractID.newBuilder().contractNum(1004L).build();
    static final AccountID FINALIZED_AND_DESTRUCTED_ID =
            AccountID.newBuilder().accountNum(1005L).build();
    static final ContractID FINALIZED_AND_DESTRUCTED_CONTRACT_ID =
            ContractID.newBuilder().contractNum(1005L).build();
    static final AccountID POINTLESS_INTERMEDIARY_ID =
            AccountID.newBuilder().accountNum(1007L).build();
    static final AccountID RUBE_GOLDBERG_CHILD_ID =
            AccountID.newBuilder().accountNum(1008L).build();
    static final FileID ASSORTED_OPS_INITCODE_FILE_ID = new FileID(0, 0, 1003);
    static final Bytes ETH_LAZY_CREATE = Bytes.fromHex(
            "02f8ad82012a80a000000000000000000000000000000000000000000000000000000000000003e8a0000000000000000000000000000000000000000000000000000000746a528800831e848094fee687d5088faff48013a6767505c027e2742536880de0b6b3a764000080c080a0f5ddf2394311e634e2147bf38583a017af45f4326bdf5746cac3a1110f973e4fa025bad52d9a9f8b32eb983c9fb8959655258bd75e2826b2c6a48d4c26ec30d112");
    static final BigInteger SALT = BigInteger.valueOf(1_234_567_890L);
    static final Map<Bytes, Bytes> EXPECTED_CHILD_STORAGE = Map.ofEntries(Map.entry(
            Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000000"),
            Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")));
    static final Map<Bytes, Bytes> EXPECTED_POINTLESS_INTERMEDIARY_STORAGE = Map.ofEntries(
            Map.entry(
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000000"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000499602d2")));

    static final Function DEPLOY_DETERMINISTIC_CHILD = new Function("deployDeterministicChild(uint)");
    static final Function DEPLOY_GOLDBERGESQUE = new Function("deployRubeGoldbergesque(uint)");
    static final Function VACATE_ADDRESS = new Function("vacateAddress()");
    static final Function TAKE_FIVE = new Function("takeFive()");
}
