/*
 * Copyright (C) 2024-2025 Hedera Hashgraph, LLC
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

package com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.signschedule;

import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_SCHEDULE_ID;
import static com.hedera.hapi.node.base.ResponseCodeEnum.INVALID_TRANSACTION_BODY;
import static com.hedera.node.app.service.contract.impl.exec.scope.HederaNativeOperations.MISSING_ENTITY_NUMBER;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.explicitFromHeadlong;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.maybeMissingNumberOf;
import static com.hedera.node.app.service.contract.impl.utils.ConversionUtils.numberOfLongZero;
import static com.hedera.node.app.spi.workflows.HandleException.validateTrue;
import static com.hedera.pbj.runtime.io.buffer.Bytes.wrap;
import static java.util.Collections.emptySet;
import static java.util.Objects.requireNonNull;

import com.esaulpaugh.headlong.abi.Address;
import com.esaulpaugh.headlong.abi.Function;
import com.esaulpaugh.headlong.abi.Tuple;
import com.hedera.hapi.node.base.AccountID;
import com.hedera.hapi.node.base.ContractID;
import com.hedera.hapi.node.base.Key;
import com.hedera.hapi.node.base.ScheduleID;
import com.hedera.hapi.node.base.SignatureMap;
import com.hedera.hapi.node.scheduled.ScheduleSignTransactionBody;
import com.hedera.hapi.node.transaction.TransactionBody;
import com.hedera.node.app.service.contract.impl.exec.gas.DispatchType;
import com.hedera.node.app.service.contract.impl.exec.gas.SystemContractGasCalculator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.AbstractCallTranslator;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.common.Call;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.DispatchForResponseCodeHssCall;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hss.HssCallAttempt;
import com.hedera.node.app.service.contract.impl.exec.systemcontracts.hts.ReturnTypes;
import com.hedera.node.app.service.contract.impl.hevm.HederaWorldUpdater;
import com.hedera.node.app.spi.signatures.SignatureVerifier.MessageType;
import com.hedera.node.app.spi.signatures.SignatureVerifier.SimpleKeyStatus;
import com.hedera.node.app.spi.workflows.HandleException;
import com.hedera.node.config.data.ContractsConfig;
import com.hedera.pbj.runtime.ParseException;
import com.hedera.pbj.runtime.io.buffer.Bytes;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Translates {@code signSchedule()} calls to the HSS system contract.
 */
@Singleton
public class SignScheduleTranslator extends AbstractCallTranslator<HssCallAttempt> {
    public static final Function SIGN_SCHEDULE = new Function("signSchedule(address,bytes)", ReturnTypes.INT_64);
    public static final Function SIGN_SCHEDULE_PROXY = new Function("signSchedule()", ReturnTypes.INT_64);
    public static final Function AUTHORIZE_SCHEDULE = new Function("authorizeSchedule(address)", ReturnTypes.INT_64);
    private static final int SCHEDULE_ID_INDEX = 0;
    private static final int SIGNATURE_MAP_INDEX = 1;

    @Inject
    public SignScheduleTranslator() {
        // Dagger2
    }

    @Override
    public boolean matches(@NonNull final HssCallAttempt attempt) {
        requireNonNull(attempt);
        final var signScheduleEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractSignScheduleEnabled();
        final var authorizeScheduleEnabled =
                attempt.configuration().getConfigData(ContractsConfig.class).systemContractAuthorizeScheduleEnabled();
        return attempt.isSelectorIfConfigEnabled(signScheduleEnabled, SIGN_SCHEDULE_PROXY, SIGN_SCHEDULE)
                || attempt.isSelectorIfConfigEnabled(authorizeScheduleEnabled, AUTHORIZE_SCHEDULE);
    }

    @Override
    public Call callFrom(@NonNull HssCallAttempt attempt) {
        final var body = bodyFor(scheduleIdFor(attempt));
        return new DispatchForResponseCodeHssCall(
                attempt, body, SignScheduleTranslator::gasRequirement, attempt.keySetFor());
    }

    /**
     * Calculates the gas requirement for a {@code signSchedule()} call.
     *
     * @param body the transaction body
     * @param systemContractGasCalculator the gas calculator
     * @param enhancement the enhancement
     * @param payerId the payer ID
     * @return the gas requirement
     */
    public static long gasRequirement(
            @NonNull final TransactionBody body,
            @NonNull final SystemContractGasCalculator systemContractGasCalculator,
            @NonNull final HederaWorldUpdater.Enhancement enhancement,
            @NonNull final AccountID payerId) {
        return systemContractGasCalculator.gasRequirement(body, DispatchType.SCHEDULE_SIGN, payerId);
    }

    /**
     * Creates a transaction body for a {@code signSchedule()} or call.
     *
     * @param scheduleID the schedule ID
     * @return the transaction body
     */
    private TransactionBody bodyFor(@NonNull ScheduleID scheduleID) {
        requireNonNull(scheduleID);
        return TransactionBody.newBuilder()
                .scheduleSign(ScheduleSignTransactionBody.newBuilder()
                        .scheduleID(scheduleID)
                        .build())
                .build();
    }

    /**
     * Extracts the schedule ID from a {@code authorizeSchedule()} call or return the redirect schedule ID
     * if the call via the proxy contract
     *
     * @param attempt the call attempt
     * @return the schedule ID
     */
    private ScheduleID scheduleIdFor(@NonNull HssCallAttempt attempt) {
        requireNonNull(attempt);
        if (attempt.isSelector(SIGN_SCHEDULE_PROXY)) {
            return getScheduleIDForSignScheduleProxy(attempt);
        } else if (attempt.isSelector(SIGN_SCHEDULE)) {
            return getScheduleIDForSignSchedule(attempt);
        } else if (attempt.isSelector(AUTHORIZE_SCHEDULE)) {
            return getScheduleIDForAuthorizeSchedule(attempt);
        }
        throw new IllegalStateException("Unexpected function selector");
    }

    private static ScheduleID getScheduleIDForSignSchedule(@NonNull HssCallAttempt attempt) {
        final var call = SIGN_SCHEDULE.decodeCall(attempt.inputBytes());
        return getScheduleIDFromCall(attempt, call);
    }

    private static ScheduleID getScheduleIDForAuthorizeSchedule(@NonNull HssCallAttempt attempt) {
        final var call = AUTHORIZE_SCHEDULE.decodeCall(attempt.inputBytes());
        return getScheduleIDFromCall(attempt, call);
    }

    private static @Nullable ScheduleID getScheduleIDFromCall(@NotNull HssCallAttempt attempt, Tuple call) {
        final Address scheduleAddress = call.get(SCHEDULE_ID_INDEX);
        final var number = numberOfLongZero(explicitFromHeadlong(scheduleAddress));
        final var schedule = attempt.enhancement().nativeOperations().getSchedule(number);
        validateTrue(schedule != null, INVALID_SCHEDULE_ID);
        return schedule.scheduleId();
    }

    private static ScheduleID getScheduleIDForSignScheduleProxy(@NonNull HssCallAttempt attempt) {
        final var scheduleID = attempt.redirectScheduleId();
        validateTrue(scheduleID != null, INVALID_SCHEDULE_ID);
        return attempt.redirectScheduleId();
    }

    /**
     * Extracts the key set for a {@code signSchedule()} call.
     *
     * @param attempt the call attempt
     * @return the key set
     */
    private Set<Key> keySetFor(@NonNull HssCallAttempt attempt) {
        requireNonNull(attempt);

        if (attempt.isSelector(SIGN_SCHEDULE)) {
            return getKeyForSignSchedule(attempt);
        }
        final var sender = attempt.enhancement().nativeOperations().getAccount(attempt.senderId());
        requireNonNull(sender);
        if (sender.smartContract()) {
            return getKeysForContractSender(attempt);
        } else {
            return getKeysForEOASender(attempt);
        }
    }

    /**
     * Returns the key set for a {@code signSchedule(address, bytes)} call.
     *
     * @param attempt the call attempt
     * @return the verified key set
     */
    @NonNull
    private static Set<Key> getKeyForSignSchedule(@NonNull HssCallAttempt attempt) {
        requireNonNull(attempt);
        final Set<Key> keys = new HashSet<>();
        final var call = SIGN_SCHEDULE.decodeCall(attempt.inputBytes());
        final var scheduleId = requireNonNull(getScheduleIDFromCall(attempt, call));

        // compute the message as the concatenation of the realm, shard, and schedule numbers
        final ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES * 3);
        buffer.putLong(scheduleId.shardNum());
        buffer.putLong(scheduleId.realmNum());
        buffer.putLong(scheduleId.scheduleNum());
        final Bytes message = Bytes.wrap(buffer.array());

        final var signatureBlob = (byte[]) call.get(SIGNATURE_MAP_INDEX);
        SignatureMap sigMap;
        try {
            sigMap = requireNonNull(SignatureMap.PROTOBUF.parse(wrap(signatureBlob)));
            for (var sigPair : sigMap.sigPair()) {
                // For ED25519 and ECDSA keys, verify the key and add it to the key set as
                if (sigPair.hasEd25519()) {
                    var key = Key.newBuilder().ed25519(sigPair.ed25519OrThrow()).build();
                    if (isVerifiedSignature(attempt, key, message, sigMap)) {
                        keys.add(key);
                    }
                }
                if (sigPair.hasEcdsaSecp256k1()) {
                    var key = Key.newBuilder()
                            .ecdsaSecp256k1(sigPair.ecdsaSecp256k1OrThrow())
                            .build();
                    if (isVerifiedSignature(attempt, key, message, sigMap)) {
                        keys.add(key);
                    }
                }
            }
        } catch (@NonNull final ParseException | NullPointerException ex) {
            throw new HandleException(INVALID_TRANSACTION_BODY);
        }
        return keys;
    }

    /**
     * Verifies the signature for a given key.
     * @param attempt the call attempt
     * @param key the key to verify
     * @param message the message to verify - concatenation of realm, shard, and schedule numbers
     * @param sigMap the signature map used for verification
     * @return true if the signature is verified, false otherwise
     */
    private static boolean isVerifiedSignature(
            @NotNull HssCallAttempt attempt, Key key, Bytes message, SignatureMap sigMap) {
        return attempt.signatureVerifier()
                .verifySignature(key, message, MessageType.RAW, sigMap, ky -> SimpleKeyStatus.ONLY_IF_CRYPTO_SIG_VALID);
    }

    @NonNull
    private static Set<Key> getKeysForEOASender(@NonNull HssCallAttempt attempt) {
        // If an Eth sender key is present, use it. Otherwise, use the account key if present.
        Key key = attempt.enhancement().systemOperations().maybeEthSenderKey();
        if (key != null) {
            return Set.of(key);
        }
        return attempt.enhancement().nativeOperations().authorizingSimpleKeys();
    }

    @NonNull
    private static Set<Key> getKeysForContractSender(@NonNull HssCallAttempt attempt) {
        final var contractNum = maybeMissingNumberOf(attempt.senderAddress(), attempt.nativeOperations());
        if (contractNum == MISSING_ENTITY_NUMBER) {
            return emptySet();
        }
        if (attempt.isOnlyDelegatableContractKeysActive()) {
            return Set.of(Key.newBuilder()
                    .delegatableContractId(
                            ContractID.newBuilder().contractNum(contractNum).build())
                    .build());
        } else {
            return Set.of(Key.newBuilder()
                    .contractID(ContractID.newBuilder().contractNum(contractNum).build())
                    .build());
        }
    }
}
