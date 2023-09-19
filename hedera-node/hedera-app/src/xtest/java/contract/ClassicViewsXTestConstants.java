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

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;

public class ClassicViewsXTestConstants {
    static final FileID CLASSIC_VIEWS_INITCODE_FILE_ID = new FileID(0, 0, 1029L);
    static final ContractID CLASSIC_QUERIES_X_TEST_ID =
            ContractID.newBuilder().contractNum(1030L).build();
    static final String SUCCESS_RESPONSE_CODE = "0000000000000000000000000000000000000000000000000000000000000016";
    static final Bytes TOKEN_IS_FROZEN =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    static final Bytes TOKEN_IS_KYC =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000001");
    static final Bytes TOKEN_IS_TOKEN =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000001");
    static final Bytes TOKEN_TYPE_FUNGIBLE =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    static final Bytes TOKEN_DEFAULT_FREEZE_STATUS =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    static final Bytes TOKEN_DEFAULT_KYC_STATUS =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    static final Function GET_IS_FROZEN = new Function("isFrozenPublic(address,address)");
    static final Function GET_IS_KYC = new Function("isKycPublic(address,address)");
    static final Function GET_IS_TOKEN = new Function("isTokenPublic(address)");
    static final Function GET_TOKEN_TYPE = new Function("getTokenTypePublic(address)");
    static final Function GET_DEFAULT_FREEZE_STATUS = new Function("getTokenDefaultFreezeStatusPublic(address)");
    static final Function GET_DEFAULT_KYC_STATUS = new Function("getTokenDefaultKycStatusPublic(address)");
}
