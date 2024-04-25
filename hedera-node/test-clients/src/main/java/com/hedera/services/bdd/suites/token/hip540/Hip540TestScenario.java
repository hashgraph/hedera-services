/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
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

package com.hedera.services.bdd.suites.token.hip540;

import static com.hedera.services.bdd.spec.queries.QueryVerbs.getTokenInfo;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenCreate;
import static com.hedera.services.bdd.spec.transactions.TxnVerbs.tokenUpdate;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.blockingOrder;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.logIt;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.newKeyNamed;
import static com.hedera.services.bdd.spec.utilops.UtilVerbs.withOpContext;
import static com.hedera.services.bdd.suites.HapiSuite.DEFAULT_PAYER;
import static com.hedera.services.bdd.suites.token.hip540.ManagementAction.REMOVE;
import static com.hederahashgraph.api.proto.java.TokenKeyValidation.NO_VALIDATION;
import static java.util.Objects.requireNonNull;

import com.google.protobuf.ByteString;
import com.hedera.services.bdd.spec.HapiSpecOperation;
import com.hedera.services.bdd.spec.queries.token.HapiGetTokenInfo;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenCreate;
import com.hedera.services.bdd.spec.transactions.token.HapiTokenUpdate;
import com.hedera.services.bdd.spec.utilops.mod.ExpectedResponse;
import com.hederahashgraph.api.proto.java.Key;
import com.hederahashgraph.api.proto.java.KeyList;
import com.hederahashgraph.api.proto.java.TokenKeyValidation;
import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public record Hip540TestScenario(
        @NonNull NonAdminTokenKey targetKey,
        @NonNull KeyState adminKeyState,
        @NonNull KeyState targetKeyState,
        @NonNull ManagementAction action,
        @Nullable TokenKeyValidation keyValidation,
        @NonNull Set<AuthorizingSignature> authorizingSignatures,
        @NonNull ExpectedResponse expectedResponse) {

    public Hip540TestScenario {
        requireNonNull(targetKey);
        requireNonNull(adminKeyState);
        requireNonNull(targetKeyState);
        requireNonNull(action);
        requireNonNull(authorizingSignatures);
        requireNonNull(expectedResponse);
    }

    private static final String ADMIN_KEY = "adminKey";
    private static final String ROLE_KEY = "roleKey";
    private static final String NEW_ROLE_KEY = "newRoleKey";
    private static final String TOKEN_UNDER_TEST = "token";
    // This key is structurally valid, but effectively unusable because there is no
    // known way to invert the SHA-512 hash of its associated curve point
    private static final Key ZEROED_OUT_KEY = Key.newBuilder()
            .setEd25519(ByteString.fromHex("0000000000000000000000000000000000000000000000000000000000000000"))
            .build();
    public static final Key IMMUTABILITY_SENTINEL_KEY =
            Key.newBuilder().setKeyList(KeyList.getDefaultInstance()).build();
    // This key is truly invalid, as all Ed25519 public keys must be 32 bytes long
    private static final Key STRUCTURALLY_INVALID_KEY =
            Key.newBuilder().setEd25519(ByteString.fromHex("ff")).build();

    public HapiSpecOperation asOperation() {
        final List<HapiSpecOperation> ops = new ArrayList<>();
        ops.add(logIt("HIP-540 test scenario - " + this));
        if (adminKeyState == KeyState.USABLE) {
            ops.add(newKeyNamed(ADMIN_KEY));
        }
        if (targetKeyState == KeyState.USABLE) {
            ops.add(newKeyNamed(ROLE_KEY));
        } else if (targetKeyState == KeyState.ZEROED_OUT) {
            ops.add(withOpContext((spec, opLog) -> spec.registry().saveKey(ROLE_KEY, ZEROED_OUT_KEY)));
        }
        switch (action) {
            case ADD, REPLACE -> ops.add(newKeyNamed(NEW_ROLE_KEY));
            case REMOVE -> ops.add(
                    withOpContext((spec, opLog) -> spec.registry().saveKey(NEW_ROLE_KEY, IMMUTABILITY_SENTINEL_KEY)));
            case ZERO_OUT -> ops.add(
                    withOpContext((spec, opLog) -> spec.registry().saveKey(NEW_ROLE_KEY, ZEROED_OUT_KEY)));
            case REPLACE_WITH_INVALID -> ops.add(
                    withOpContext((spec, opLog) -> spec.registry().saveKey(NEW_ROLE_KEY, STRUCTURALLY_INVALID_KEY)));
        }
        ops.add(creation());
        ops.add(update());
        if (expectedResponse.isSuccess()) {
            ops.add(confirmation());
        }
        return blockingOrder(ops.toArray(HapiSpecOperation[]::new));
    }

    private HapiGetTokenInfo confirmation() {
        final var op = getTokenInfo(TOKEN_UNDER_TEST);
        if (expectedResponse.isSuccess()) {
            if (action == REMOVE) {
                switch (targetKey) {
                    case WIPE_KEY -> op.hasEmptyWipeKey();
                    case KYC_KEY -> op.hasEmptyKycKey();
                    case SUPPLY_KEY -> op.hasEmptySupplyKey();
                    case FREEZE_KEY -> op.hasEmptyFreezeKey();
                    case FEE_SCHEDULE_KEY -> op.hasEmptyFeeScheduleKey();
                    case PAUSE_KEY -> op.hasEmptyPauseKey();
                    case METADATA_KEY -> op.hasEmptyMetadataKey();
                }
            } else {
                switch (targetKey) {
                    case WIPE_KEY -> op.hasWipeKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case KYC_KEY -> op.hasKycKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case SUPPLY_KEY -> op.hasSupplyKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case FREEZE_KEY -> op.hasFreezeKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case FEE_SCHEDULE_KEY -> op.hasFeeScheduleKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case PAUSE_KEY -> op.hasPauseKey(NEW_ROLE_KEY).searchKeysGlobally();
                    case METADATA_KEY -> op.hasMetadataKey(NEW_ROLE_KEY).searchKeysGlobally();
                }
            }
        }
        return op;
    }

    private HapiTokenUpdate update() {
        final var op = tokenUpdate(TOKEN_UNDER_TEST);
        addReplacementTo(op);
        addSignaturesTo(op);
        if (keyValidation == NO_VALIDATION) {
            op.applyNoValidationToKeys();
        }
        return op;
    }

    private void addReplacementTo(@NonNull final HapiTokenUpdate update) {
        switch (targetKey) {
            case WIPE_KEY -> update.wipeKey(NEW_ROLE_KEY);
            case KYC_KEY -> update.kycKey(NEW_ROLE_KEY);
            case SUPPLY_KEY -> update.supplyKey(NEW_ROLE_KEY);
            case FREEZE_KEY -> update.freezeKey(NEW_ROLE_KEY);
            case FEE_SCHEDULE_KEY -> update.feeScheduleKey(NEW_ROLE_KEY);
            case PAUSE_KEY -> update.pauseKey(NEW_ROLE_KEY);
            case METADATA_KEY -> update.metadataKey(NEW_ROLE_KEY);
        }
    }

    private void addSignaturesTo(@NonNull final HapiTokenUpdate update) {
        final List<String> signatures = new ArrayList<>();
        signatures.add(DEFAULT_PAYER);
        authorizingSignatures.forEach(sig -> {
            switch (sig) {
                case EXTANT_ADMIN -> signatures.add(ADMIN_KEY);
                case EXTANT_NON_ADMIN -> signatures.add(ROLE_KEY);
                case NEW_NON_ADMIN -> signatures.add(NEW_ROLE_KEY);
            }
        });
        update.signedBy(signatures.toArray(String[]::new));
        expectedResponse.customize(update);
    }

    private HapiTokenCreate creation() {
        final var op = tokenCreate(TOKEN_UNDER_TEST);
        if (adminKeyState != KeyState.MISSING) {
            op.adminKey(ADMIN_KEY);
        }
        if (targetKeyState != KeyState.MISSING) {
            switch (targetKey) {
                case WIPE_KEY -> op.wipeKey(ROLE_KEY);
                case KYC_KEY -> op.kycKey(ROLE_KEY);
                case SUPPLY_KEY -> op.supplyKey(ROLE_KEY);
                case FREEZE_KEY -> op.freezeKey(ROLE_KEY);
                case FEE_SCHEDULE_KEY -> op.feeScheduleKey(ROLE_KEY);
                case PAUSE_KEY -> op.pauseKey(ROLE_KEY);
                case METADATA_KEY -> op.metadataKey(ROLE_KEY);
            }
        }
        return op;
    }

    @Override
    public String toString() {
        return "Hip540TestScenario{" + "\n  targetKey="
                + targetKey + ",\n  adminKeyState="
                + adminKeyState + ",\n  targetKeyState="
                + targetKeyState + ",\n  action="
                + action + ",\n  keyValidation="
                + keyValidation + ",\n  authorizingSignatures="
                + authorizingSignatures + ",\n  expectedResponse="
                + expectedResponse + '}';
    }
}
