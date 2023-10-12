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

import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes.ZERO_CONTRACT_ID;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenexpiry.TokenExpiryTranslator.TOKEN_EXPIRY;
import static com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.tokenkey.TokenKeyTranslator.TOKEN_KEY;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.headlongAddressOf;
import static contract.MiscViewsXTestConstants.OPERATOR_ID;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.hapi.node.base.Key;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;

public class ClassicViewsXTestConstants {
    private static int SUCCESS_INT = 22;
    static final FileID CLASSIC_VIEWS_INITCODE_FILE_ID = new FileID(0, 0, 1029L);
    static final ContractID CLASSIC_QUERIES_X_TEST_ID =
            ContractID.newBuilder().contractNum(1030L).build();
    static final String SUCCESS_RESPONSE_CODE = "0000000000000000000000000000000000000000000000000000000000000016";
    // IS_FROZEN flag for token is false
    static final Bytes TOKEN_IS_FROZEN =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    // IS_KYC flag for token is true - set in ClassicViewsXTest.initialTokenRelationships()
    static final Bytes TOKEN_IS_KYC =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000001");
    // the token is truly a token
    static final Bytes TOKEN_IS_TOKEN =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000001");
    // the token is fungible
    static final Bytes TOKEN_TYPE_FUNGIBLE =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    // default freeze status is false
    static final Bytes TOKEN_DEFAULT_FREEZE_STATUS =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    // default kyc status is false
    static final Bytes TOKEN_DEFAULT_KYC_STATUS =
            Bytes.fromHex(SUCCESS_RESPONSE_CODE + "0000000000000000000000000000000000000000000000000000000000000000");
    static final long EXPIRATION_SECONDS = 100L;
    static final long AUTORENEW_SECONDS = 200L;
    static final ByteBuffer EXPECTED_TOKEN_EXPIRY = TOKEN_EXPIRY
            .getOutputs()
            .encodeElements(
                    SUCCESS_INT, Tuple.of(EXPIRATION_SECONDS, headlongAddressOf(OPERATOR_ID), AUTORENEW_SECONDS));
    static final ContractID A_CONTRACT_ID =
            ContractID.newBuilder().contractNum(666L).build();
    static final ContractID B_CONTRACT_ID =
            ContractID.newBuilder().contractNum(777L).build();
    static final Key ADMIN_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0101010101010101010101010101010101010101010101010101010101010101"))
            .build();
    static final Key KYC_KEY = Key.newBuilder()
            .ecdsaSecp256k1(Bytes.fromHex("0202020202020202020202020202020202020202020202020202020202020202"))
            .build();
    static final Key FREEZE_KEY = Key.newBuilder().contractID(A_CONTRACT_ID).build();
    static final Key WIPE_KEY =
            Key.newBuilder().delegatableContractId(B_CONTRACT_ID).build();
    static final Key SUPPLY_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0303030303030303030303030303030303030303030303030303030303030303"))
            .build();
    static final Key FEE_SCHEDULE_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0404040404040404040404040404040404040404040404040404040404040404"))
            .build();
    static final Key PAUSE_KEY = Key.newBuilder()
            .ed25519(Bytes.fromHex("0505050505050505050505050505050505050505050505050505050505050505"))
            .build();

    static final ByteBuffer returnExpectedKey(@NonNull final Key key) {
        return TOKEN_KEY
                .getOutputs()
                .encodeElements(
                        SUCCESS_INT,
                        Tuple.of(
                                false,
                                headlongAddressOf(key.contractIDOrElse(ZERO_CONTRACT_ID)),
                                key.ed25519OrElse(Bytes.EMPTY).toByteArray(),
                                key.ecdsaSecp256k1OrElse(Bytes.EMPTY).toByteArray(),
                                headlongAddressOf(key.delegatableContractIdOrElse(ZERO_CONTRACT_ID))));
    }

    static final ByteBuffer EXPECTED_ADMIN_KEY = TOKEN_KEY
            .getOutputs()
            .encodeElements(
                    SUCCESS_INT,
                    Tuple.of(
                            false,
                            headlongAddressOf(ADMIN_KEY.contractIDOrElse(ZERO_CONTRACT_ID)),
                            ADMIN_KEY.ed25519OrElse(Bytes.EMPTY).toByteArray(),
                            ADMIN_KEY.ecdsaSecp256k1OrElse(Bytes.EMPTY).toByteArray(),
                            headlongAddressOf(ADMIN_KEY.delegatableContractIdOrElse(ZERO_CONTRACT_ID))));
    static final Function GET_IS_FROZEN = new Function("isFrozenPublic(address,address)");
    static final Function GET_IS_KYC = new Function("isKycPublic(address,address)");
    static final Function GET_IS_TOKEN = new Function("isTokenPublic(address)");
    static final Function GET_TOKEN_TYPE = new Function("getTokenTypePublic(address)");
    static final Function GET_DEFAULT_FREEZE_STATUS = new Function("getTokenDefaultFreezeStatusPublic(address)");
    static final Function GET_DEFAULT_KYC_STATUS = new Function("getTokenDefaultKycStatusPublic(address)");
    static final Function GET_TOKEN_EXPIRY = new Function("getTokenExpiryInfoPublic(address)");
    static final Function GET_TOKEN_KEY = new Function("getTokenKeyPublic(address,uint)");
}
