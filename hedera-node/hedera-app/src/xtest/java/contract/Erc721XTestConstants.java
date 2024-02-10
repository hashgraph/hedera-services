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
import java.util.Map;

/**
 * Constants used in the {@link Erc721XTest}, extracted here to improve readability of that file.
 */
class Erc721XTestConstants {
    static final long NEXT_ENTITY_NUM = 1006L;
    static final Bytes[] ACCOUNT_ALIASES = {
        Bytes.fromHex("096959f155eE025b17E5C537b6dCB4a29BBAd8c2"),
        Bytes.fromHex("D893F18B69A06F7ffFfaD77202c2f627CB2C9605"),
        Bytes.fromHex("8CEB1aE3aB4ABfcA08c0BC5CD59DE0Bce7b5554f"),
        Bytes.EMPTY,
    };
    static final Bytes OPERATOR_ADDRESS = ACCOUNT_ALIASES[2];
    static final Bytes COUNTERPARTY_ADDRESS = ACCOUNT_ALIASES[1];
    static final Bytes TOKEN_TREASURY_ADDRESS = ACCOUNT_ALIASES[0];
    static final Map<Bytes, Bytes> EXPECTED_STORAGE = Map.ofEntries(
            Map.entry(
                    Bytes.fromHex("4ED80C6A5F6FC6B817594793D5BC01D5AFC46D4DEB2D84AA5499B6BA2A91788B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000002")),
            Map.entry(
                    Bytes.fromHex("0F24D19A172FA39F354DF146E316F49BA39EED2FB244C2D71184E128EE8EA57E"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("1D9A121EAE26CB344361C7A5EC17B9B0DC501335BE929850EA33D9A7A2EA135B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("679795A0195A1B76CDEBB7C51D74E058AEE92919B8C3389AF86EF24535E8A28C"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("7DFE757ECD65CBD7922A9C0161E935DD7FDBCC0E999689C7D31633896B1FC60B"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("67BE87C3FF9960CA1E9CFAC5CAB2FF4747269CF9ED20C9B7306235AC35A491C5"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("4DB623E5C4870B62D3FC9B4E8F893A1A77627D75AB45D9FF7E56BA19564AF99B"),
                    Bytes.fromHex("00000000000000000000000000000000000000000000000000000000000003ec")),
            Map.entry(
                    Bytes.fromHex("F7815FCCBF112960A73756E185887FEDCB9FC64CA0A16CC5923B7960ED780800"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000001")),
            Map.entry(
                    Bytes.fromHex("E2689CD4A84E23AD2F564004F1C9013E9589D260BDE6380ABA3CA7E09E4DF40C"),
                    Bytes.fromHex("000000000000000000000000096959f155ee025b17e5c537b6dcb4a29bbad8c2")),
            Map.entry(
                    Bytes.fromHex("DD170DB99724E3ABCC0E44A83A3B5D5F8332989846A2C7346446F717FDA4F32B"),
                    Bytes.fromHex("0000000000000000000000000000000000000000000000000000000000000002")),
            Map.entry(
                    Bytes.fromHex("D9D16D34FFB15BA3A3D852F0D403E2CE1D691FB54DE27AC87CD2F993F3EC330F"),
                    Bytes.fromHex("000000000000000000000000096959f155ee025b17e5c537b6dcb4a29bbad8c2")),
            Map.entry(
                    Bytes.fromHex("86B3FA87EE245373978E0D2D334DBDE866C9B8B039036B87C5EB2FD89BCB6BAB"),
                    Bytes.fromHex("000000000000000000000000d893f18b69a06f7ffffad77202c2f627cb2c9605")));
    static final AccountID TOKEN_TREASURY_ID =
            AccountID.newBuilder().accountNum(1001L).build();
    static final AccountID COUNTERPARTY_ID =
            AccountID.newBuilder().accountNum(1002L).build();
    static final AccountID OPERATOR_ID =
            AccountID.newBuilder().accountNum(1003L).build();
    static final AccountID PARTY_ID = AccountID.newBuilder().accountNum(1004L).build();
    static final Bytes PARTY_ADDRESS = Bytes.fromHex("00000000000000000000000000000000000003ec");
    static final FileID ERC721_FULL_INITCODE_FILE_ID = new FileID(0, 0, 1005);
    static final AccountID ERC721_FULL_ID =
            AccountID.newBuilder().accountNum(1006).build();
    static final ContractID ERC721_FULL_CONTRACT_ID = ContractID.newBuilder()
            .contractNum(ERC721_FULL_ID.accountNumOrThrow())
            .build();
    static final Function APPROVE = new Function("approve(address,uint256)");
    static final Function SET_APPROVAL_FOR_ALL = new Function("setApprovalForAll(address,bool)");
    static final Function SAFE_TRANSFER_FROM = new Function("safeTransferFrom(address,address,uint256)");
    static final long HBAR_SUPPLY = 50_000_000_000L * 100_000_000L;
    static final long INITIAL_BALANCE = HBAR_SUPPLY / 1_000;
}
