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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.associations;

import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Function;
import com.hedera.hapi.node.token.TokenAssociateTransactionBody;
import com.hedera.hapi.node.token.TokenDissociateTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.DispatchForResponseCodeHtsCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.HtsCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.spi.workflows.record.SingleTransactionRecordBuilder;
import com.swirlds.common.utility.CommonUtils;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.Arrays;
import org.hyperledger.besu.datatypes.Address;

/**
 * Implements the associate and dissociate calls of the HTS contract.
 */
public class AssociationsCall extends DispatchForResponseCodeHtsCall<SingleTransactionRecordBuilder> {
    public static final Function HRC_ASSOCIATE = new Function("associate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_ONE = new Function("associateToken(address,address)", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_ONE = new Function("dissociateToken(address,address)", ReturnTypes.INT_64);
    public static final Function HRC_DISSOCIATE = new Function("dissociate()", ReturnTypes.INT);
    public static final Function ASSOCIATE_MANY =
            new Function("associateTokens(address,address[])", ReturnTypes.INT_64);
    public static final Function DISSOCIATE_MANY =
            new Function("dissociateTokens(address,address[])", ReturnTypes.INT_64);

    public AssociationsCall(
            final boolean onlyDelegatable,
            @NonNull final HtsCallAttempt attempt,
            @NonNull final Address sender,
            @NonNull final TransactionBody syntheticBody) {
        super(onlyDelegatable, attempt, sender, syntheticBody, SingleTransactionRecordBuilder.class);
    }

    /**
     * Indicates if the given call attempt is for {@link AssociationsCall}.
     *
     * @param attempt the attempt to check
     * @return {@code true} if the given {@code selector} is a selector for {@link AssociationsCall}
     */
    public static boolean matches(@NonNull final HtsCallAttempt attempt) {
        requireNonNull(attempt);
        return (attempt.isTokenRedirect() && matchesHrcSelector(attempt.selector()))
                || (!attempt.isTokenRedirect() && matchesClassicSelector(attempt.selector()));
    }

    /**
     * Creates a {@link AssociationsCall} from the given {@code attempt} and {@code senderAddress}.
     *
     * @param attempt         the attempt to create a {@link AssociationsCall} from
     * @param sender          the address of the sender
     * @param onlyDelegatable whether the sender needs delegatable contract keys
     * @return a {@link AssociationsCall} if the given {@code attempt} is a valid {@link AssociationsCall}, otherwise {@code null}
     */
    public static AssociationsCall from(
            @NonNull final HtsCallAttempt attempt,
            @NonNull final org.hyperledger.besu.datatypes.Address sender,
            final boolean onlyDelegatable) {
        requireNonNull(attempt);
        requireNonNull(sender);
        return matchesHrcSelector(attempt.selector())
                ? new AssociationsCall(onlyDelegatable, attempt, sender, bodyForHrc(sender, attempt))
                : new AssociationsCall(onlyDelegatable, attempt, sender, bodyForClassic(attempt));
    }

    private static TransactionBody bodyForHrc(
            @NonNull final org.hyperledger.besu.datatypes.Address sender, @NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), HRC_ASSOCIATE.selector())) {
            return TransactionBody.newBuilder()
                    .tokenAssociate(TokenAssociateTransactionBody.newBuilder()
                            .account(attempt.addressIdConverter().convertSender(sender))
                            .tokens(requireNonNull(attempt.redirectTokenId())))
                    .build();
        } else {
            return TransactionBody.newBuilder()
                    .tokenDissociate(TokenDissociateTransactionBody.newBuilder()
                            .account(attempt.addressIdConverter().convertSender(sender))
                            .tokens(requireNonNull(attempt.redirectTokenId())))
                    .build();
        }
    }

    private static TransactionBody bodyForClassic(@NonNull final HtsCallAttempt attempt) {
        if (Arrays.equals(attempt.selector(), ASSOCIATE_ONE.selector())) {
            return attempt.decodingStrategies()
                    .decodeAssociateOne(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), ASSOCIATE_MANY.selector())) {
            return attempt.decodingStrategies()
                    .decodeAssociateMany(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), DISSOCIATE_ONE.selector())) {
            return attempt.decodingStrategies()
                    .decodeDissociateOne(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else if (Arrays.equals(attempt.selector(), DISSOCIATE_MANY.selector())) {
            return attempt.decodingStrategies()
                    .decodeDissociateMany(attempt.input().toArrayUnsafe(), attempt.addressIdConverter());
        } else {
            throw new IllegalArgumentException(
                    "Selector " + CommonUtils.hex(attempt.selector()) + "is not a classic association/dissociation");
        }
    }

    private static boolean matchesHrcSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, HRC_ASSOCIATE.selector()) || Arrays.equals(selector, HRC_DISSOCIATE.selector());
    }

    private static boolean matchesClassicSelector(@NonNull final byte[] selector) {
        return Arrays.equals(selector, ASSOCIATE_ONE.selector())
                || Arrays.equals(selector, DISSOCIATE_ONE.selector())
                || Arrays.equals(selector, ASSOCIATE_MANY.selector())
                || Arrays.equals(selector, DISSOCIATE_MANY.selector());
    }
}
