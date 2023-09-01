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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.transfer;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;

public class TransferCall implements HtsCall {
    public static final Function CRYPTO_TRANSFER =
            new Function("cryptoTransfer((address,(address,int64)[],(address,address,int64)[])[])", ReturnTypes.INT);
    public static final Function CRYPTO_TRANSFER_V2 = new Function(
            "cryptoTransfer(((address,int64,bool)[]),(address,(address,int64,bool)[],(address,address,int64,bool)[])[])",
            ReturnTypes.INT);
    public static final Function TRANSFER_TOKENS =
            new Function("transferTokens(address,address[],int64[])", ReturnTypes.INT);
    public static final Function TRANSFER_TOKEN =
            new Function("transferToken(address,address,address,int64)", ReturnTypes.INT);
    public static final Function TRANSFER_NFTS =
            new Function("transferNFTs(address,address[],address[],int64[])", ReturnTypes.INT);
    private static final Function TRANSFER_NFT =
            new Function("transferNFT(address,address,address,int64)", ReturnTypes.INT);
    public static final Function HRC_TRANSFER_FROM =
            new Function("transferFrom(address,address,address,uint256)", ReturnTypes.INT);
    public static final Function HRC_TRANSFER_NFT_FROM =
            new Function("transferFromNFT(address,address,address,uint256)", ReturnTypes.INT);
    public static final Function ERC_20_TRANSFER = new Function("transfer(address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_20_TRANSFER_FROM =
            new Function("transferFrom(address,address,uint256)", ReturnTypes.BOOL);
    public static final Function ERC_721_TRANSFER_FROM = new Function("transferFrom(address,address,uint256)");

    public static boolean matches(@NonNull final byte[] selector) {
        requireNonNull(selector);
        return Arrays.equals(selector, CRYPTO_TRANSFER.selector())
                || Arrays.equals(selector, CRYPTO_TRANSFER_V2.selector())
                || Arrays.equals(selector, TRANSFER_TOKENS.selector())
                || Arrays.equals(selector, TRANSFER_TOKEN.selector())
                || Arrays.equals(selector, TRANSFER_NFTS.selector())
                || Arrays.equals(selector, TRANSFER_NFT.selector())
                || Arrays.equals(selector, HRC_TRANSFER_FROM.selector())
                || Arrays.equals(selector, HRC_TRANSFER_NFT_FROM.selector())
                || Arrays.equals(selector, ERC_20_TRANSFER.selector())
                || Arrays.equals(selector, ERC_20_TRANSFER_FROM.selector())
                || Arrays.equals(selector, ERC_721_TRANSFER_FROM.selector());
    }

    public static TransferCall from(@NonNull final HtsCallAttempt attempt, @NonNull final Address senderAddress) {
        // TODO - implement this
        return new TransferCall();
    }

    @Override
    public @NonNull PricedResult execute() {
        throw new AssertionError("Not implemented");
    }
}
