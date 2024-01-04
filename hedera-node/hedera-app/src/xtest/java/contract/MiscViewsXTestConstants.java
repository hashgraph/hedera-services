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
import static contract.AbstractContractXTest.asHeadlongAddress;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.FileID;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import java.math.BigInteger;

public class MiscViewsXTestConstants {
    static final long NEXT_ENTITY_NUM = 1030L;
    static final BigInteger SECRET = BigInteger.valueOf(123456789L);
    static final BigInteger TINYBARS = BigInteger.valueOf(666_666_666L);

    static final FileID VIEWS_INITCODE_FILE_ID = new FileID(0, 0, 1029L);
    static final Bytes RAW_ERC_USER_ADDRESS = Bytes.fromHex("24Afdd97fc25332cf82FC29868293bD8eA24161c");
    static final Address ERC_USER_ADDRESS = asHeadlongAddress(RAW_ERC_USER_ADDRESS.toByteArray());
    static final AccountID ERC_USER_ID =
            AccountID.newBuilder().accountNum(1024L).build();
    static final AccountID OPERATOR_ID =
            AccountID.newBuilder().accountNum(1025L).build();
    static final Address ERC721_OPERATOR_ADDRESS =
            asHeadlongAddress(asLongZeroAddress(OPERATOR_ID.accountNumOrThrow()).toArray());
    static final AccountID COINBASE_ID = AccountID.newBuilder().accountNum(98L).build();
    static final Address ERC20_TOKEN_ADDRESS = asHeadlongAddress(
            asLongZeroAddress(XTestConstants.ERC20_TOKEN_ID.tokenNum()).toArray());
    static final ContractID SPECIAL_QUERIES_X_TEST_ID =
            ContractID.newBuilder().contractNum(1030L).build();

    static final Bytes UNCOVERED_SECRET =
            Bytes.fromHex("00000000000000000000000000000000000000000000000000000000075bcd15");
    static final Bytes PRNG_SEED = Bytes.fromHex("86a7fc8deb21a39818f8dbe107ba4614daa90e5ec539abcb6a89aaf0120f24e5");
    static final Bytes EQUIV_TINYCENTS =
            Bytes.fromHex("00000000000000000000000000000000000000000000000000000001dcd64ff8");
    static final Bytes ERC20_USER_BALANCE =
            Bytes.fromHex("000000000000000000000000000000000000000000000000000000000000006f");
    static final Bytes ERC20_SUPPLY = Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000378");
    static final Bytes ERC20_NAME = Bytes.fromHex(
            "00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000007323020436f696e00000000000000000000000000000000000000000000000000");
    static final Bytes ERC20_SYMBOL = Bytes.fromHex(
            "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000553594d3230000000000000000000000000000000000000000000000000000000");
    static final Bytes ERC20_DECIMALS =
            Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000002");
    static final Bytes ERC721_NAME = Bytes.fromHex(
            "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001137323120556e69717565205468696e6773000000000000000000000000000000");
    static final Bytes ERC721_SYMBOL = Bytes.fromHex(
            "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000653594d3732310000000000000000000000000000000000000000000000000000");
    static final Bytes ERC721_SN2_METADATA = Bytes.fromHex(
            "0000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000001968747470733a2f2f6578616d706c652e636f6d2f3732312f3200000000000000");
    static final Bytes ERC721_USER_BALANCE =
            Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000003");
    static final Bytes ERC721_SN1_OWNER =
            Bytes.fromHex("00000000000000000000000024afdd97fc25332cf82fc29868293bd8ea24161c");
    static final Bytes ERC721_IS_OPERATOR =
            Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001");
    static final Function GET_TINYCENTS_EQUIV = new Function("getTinycentsEquiv(uint)");
    static final Function GET_SECRET = new Function("secret()");
    static final Function GET_PRNG_SEED = new Function("getPrngSeed()");
    static final Function GET_ERC_20_BALANCE = new Function("getErc20Balance(address,address)");
    static final Function GET_ERC_20_SUPPLY = new Function("getErc20Supply(address)");
    static final Function GET_ERC_20_NAME = new Function("getErc20Name(address)");
    static final Function GET_ERC_20_SYMBOL = new Function("getErc20Symbol(address)");
    static final Function GET_ERC_20_DECIMALS = new Function("getErc20Decimals(address)");
    static final Function GET_ERC_721_NAME = new Function("getErc721Name(address)");
    static final Function GET_ERC_721_SYMBOL = new Function("getErc721Symbol(address)");
    static final Function GET_ERC_721_TOKEN_URI = new Function("getErc721TokenUri(address,uint256)");
    static final Function GET_ERC_721_BALANCE = new Function("getErc721Balance(address,address)");
    static final Function GET_ERC_721_OWNER = new Function("getErc721Owner(address,uint256)");
    static final Function GET_ERC721_IS_OPERATOR = new Function("getErc721IsOperator(address,address,address)");
}
