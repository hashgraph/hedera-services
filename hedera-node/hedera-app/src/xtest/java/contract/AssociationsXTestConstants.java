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

import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.asLongZeroAddress;

import com.hedera.hapi.node.base.TokenID;

/**
 * Constants used in the {@link AssociationsXTest}, extracted here to improve readability of that file.
 */
public class AssociationsXTestConstants {
    static final TokenID A_TOKEN_ID = TokenID.newBuilder().tokenNum(111111L).build();
    static final com.esaulpaugh.headlong.abi.Address A_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(A_TOKEN_ID.tokenNum()).toArray());
    static final TokenID B_TOKEN_ID = TokenID.newBuilder().tokenNum(222222L).build();
    static final com.esaulpaugh.headlong.abi.Address B_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(B_TOKEN_ID.tokenNum()).toArray());
    static final TokenID C_TOKEN_ID = TokenID.newBuilder().tokenNum(333333L).build();
    static final com.esaulpaugh.headlong.abi.Address C_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(C_TOKEN_ID.tokenNum()).toArray());
    static final TokenID D_TOKEN_ID = TokenID.newBuilder().tokenNum(444444L).build();
    static final com.esaulpaugh.headlong.abi.Address D_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(D_TOKEN_ID.tokenNum()).toArray());
    static final TokenID E_TOKEN_ID = TokenID.newBuilder().tokenNum(555555L).build();
    static final com.esaulpaugh.headlong.abi.Address E_TOKEN_ADDRESS = AbstractContractXTest.asHeadlongAddress(
            asLongZeroAddress(E_TOKEN_ID.tokenNum()).toArray());
}
