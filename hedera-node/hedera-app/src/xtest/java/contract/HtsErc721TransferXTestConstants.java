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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asHeadlongAddress;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.pbjToBesuAddress;

import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.state.token.Account;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import org.hyperledger.besu.datatypes.Address;

public class HtsErc721TransferXTestConstants {
    static final AccountID UNAUTHORIZED_SPENDER_ID =
            AccountID.newBuilder().accountNum(999999L).build();
    static final Bytes UNAUTHORIZED_SPENDER_ADDRESS = Bytes.fromHex("b284224b8b83a724438cc3cc7c0d333a2b6b3222");
    static final com.esaulpaugh.headlong.abi.Address UNAUTHORIZED_SPENDER_HEADLONG_ADDRESS =
            asHeadlongAddress(UNAUTHORIZED_SPENDER_ADDRESS.toByteArray());
    static final Address UNAUTHORIZED_SPENDER_BESU_ADDRESS = pbjToBesuAddress(UNAUTHORIZED_SPENDER_ADDRESS);
    static final AccountID APPROVED_ID =
            AccountID.newBuilder().accountNum(8888888L).build();
    static final Bytes APPROVED_ADDRESS = Bytes.fromHex("aa1e6a49898ea7a44e81599a7c0deeeaa969e990");
    static final com.esaulpaugh.headlong.abi.Address APPROVED_HEADLONG_ADDRESS =
            asHeadlongAddress(APPROVED_ADDRESS.toByteArray());
    static final Address APPROVED_BESU_ADDRESS = pbjToBesuAddress(APPROVED_ADDRESS);

    static final Account spenderAccount =
            Account.newBuilder().accountId(APPROVED_ID).build();

    static final AccountID OPERATOR_ID =
            AccountID.newBuilder().accountNum(7773777L).build();
    static final Bytes OPERATOR_ADDRESS = Bytes.fromHex("3b1ef340808e37344e8150037c0deee33060e123");
    static final com.esaulpaugh.headlong.abi.Address OPERATOR_HEADLONG_ADDRESS =
            asHeadlongAddress(OPERATOR_ADDRESS.toByteArray());
    static final Address OPERATOR_BESU_ADDRESS = pbjToBesuAddress(OPERATOR_ADDRESS);
}
