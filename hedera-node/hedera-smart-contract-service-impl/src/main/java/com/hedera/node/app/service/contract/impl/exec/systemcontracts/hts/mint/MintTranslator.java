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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.mint;

import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.TokenType;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.AbstractHtsCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.utils.ConversionUtils;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import com.hederahashgraph.api.proto.java.AccountID;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.hyperledger.besu.datatypes.Address;

/**
 * Translates {@code mintToken()} calls to the HTS system contract.
 */
@Singleton
public class MintTranslator extends AbstractHtsCallTranslator {
    public static final Function MINT = new Function("mintToken(address,uint64,bytes[])", ReturnTypes.INT);
    public static final Function MINT_V2 = new Function("mintToken(address,int64,bytes[])", ReturnTypes.INT);

    @Inject
    public MintTranslator() {
        // Dagger2
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean matches(@NonNull final HtsCallAttempt attempt) {
        return Arrays.equals(attempt.selector(), MintTranslator.MINT.selector())
                || Arrays.equals(attempt.selector(), MintTranslator.MINT_V2.selector());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable MintCall callFrom(@NonNull final HtsCallAttempt attempt) {
        final var selector = attempt.selector();
        final Tuple call;
        final long amount;
        if (Arrays.equals(selector, MintTranslator.MINT.selector())) {
            call = MintTranslator.MINT.decodeCall(attempt.input().toArrayUnsafe());
            amount = ((BigInteger) call.get(1)).longValueExact();
        } else {
            call = MintTranslator.MINT_V2.decodeCall(attempt.input().toArrayUnsafe());
            amount = call.get(1);
        }
        final var token = attempt.linkedToken(Address.fromHexString(call.get(0).toString()));
        if (token == null) {
            return null;
        } else {
            return token.tokenType() == TokenType.FUNGIBLE_COMMON
                    ? fungibleCallFrom(attempt.senderAddress(), call.get(0), amount, attempt)
                    : nonFungibleCallFrom(attempt.senderAddress(), call.get(0), call.get(2), attempt);
        }
    }

    private FungibleMintCall fungibleCallFrom(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final com.esaulpaugh.headlong.abi.Address token,
            final long amount,
            @NonNull final HtsCallAttempt attempt) {
        return new FungibleMintCall(
                attempt.enhancement(),
                amount,
                ConversionUtils.asTokenId(token),
                attempt.defaultVerificationStrategy(),
                sender,
                attempt.addressIdConverter());
    }

    private NonFungibleMintCall nonFungibleCallFrom(
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            @NonNull final com.esaulpaugh.headlong.abi.Address token,
            @NonNull final byte[][] metadataArray,
            @NonNull final HtsCallAttempt attempt) {
        final List<Bytes> metadata = new ArrayList<>();
        for (final var data : metadataArray) {
            metadata.add(Bytes.wrap(data));
        }

        return new NonFungibleMintCall(
                attempt.enhancement(),
                metadata,
                ConversionUtils.asTokenId(token),
                attempt.defaultVerificationStrategy(),
                sender,
                attempt.addressIdConverter());
    }
}
