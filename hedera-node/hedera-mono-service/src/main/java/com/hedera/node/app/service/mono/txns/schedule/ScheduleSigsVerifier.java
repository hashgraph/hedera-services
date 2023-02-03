/*
 * Copyright (C) 2022-2023 Hedera Hashgraph, LLC
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
package com.hedera.node.app.service.mono.txns.schedule;
/*
 * ‌
 * Hedera Services Node
 * ​
 * Copyright (C) 2018 - 2022 Hedera Hashgraph, LLC
 * ​
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
 * ‍
 */

import static com.hedera.node.app.service.mono.keys.HederaKeyActivation.INVALID_MISSING_SIG;
import static com.hedera.node.app.service.mono.sigs.order.CodeOrderResultFactory.CODE_ORDER_RESULT_FACTORY;

import com.google.common.annotations.VisibleForTesting;
import com.google.protobuf.InvalidProtocolBufferException;
import com.hedera.node.app.service.mono.keys.CharacteristicsFactory;
import com.hedera.node.app.service.mono.keys.HederaKeyActivation;
import com.hedera.node.app.service.mono.keys.InHandleActivationHelper;
import com.hedera.node.app.service.mono.legacy.core.jproto.JKey;
import com.hedera.node.app.service.mono.sigs.annotations.WorkingStateSigReqs;
import com.hedera.node.app.service.mono.sigs.order.SigRequirements;
import com.hedera.node.app.service.mono.state.virtual.schedule.ScheduleVirtualValue;
import com.hederahashgraph.api.proto.java.TransactionBody;
import com.swirlds.common.crypto.TransactionSignature;
import edu.umd.cs.findbugs.annotations.Nullable;
import java.util.function.BiPredicate;
import java.util.function.Function;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/** Class that encapsulates checking schedule signatures. */
@Singleton
public class ScheduleSigsVerifier {
    private static final Logger log = LogManager.getLogger(ScheduleSigsVerifier.class);

    private final SigRequirements workingSigReqs;
    private final CharacteristicsFactory characteristics;

    @VisibleForTesting
    InHandleActivationHelper.Activation activation = HederaKeyActivation::isActive;

    @Inject
    public ScheduleSigsVerifier(
            final @WorkingStateSigReqs SigRequirements workingSigReqs,
            final CharacteristicsFactory characteristics) {
        this.workingSigReqs = workingSigReqs;
        this.characteristics = characteristics;
    }

    public boolean areAllKeysActive(final ScheduleVirtualValue schedule) {
        final TransactionBody scheduledTxn = getTransactionBody(schedule);

        if (scheduledTxn == null) {
            return false;
        }

        final var reqsResult =
                workingSigReqs.keysForOtherParties(scheduledTxn, CODE_ORDER_RESULT_FACTORY);

        if (reqsResult.hasErrorReport()) {
            return false;
        } else {

            final var activeCharacter = characteristics.inferredFor(scheduledTxn);

            final Function<byte[], TransactionSignature> ignoredSigsFn =
                    publicKey -> INVALID_MISSING_SIG;

            final BiPredicate<JKey, TransactionSignature> activationTest =
                    (key, sig) -> schedule.hasValidSignatureFor(key.primitiveKeyIfPresent());

            for (final var reqKey : reqsResult.getOrderedKeys()) {
                if (reqKey.isForScheduledTxn()
                        && (!activation.test(
                                reqKey, ignoredSigsFn, activationTest, activeCharacter))) {
                    return false;
                }
            }
        }

        return true;
    }

    @Nullable
    @VisibleForTesting
    TransactionBody getTransactionBody(final ScheduleVirtualValue schedule) {
        if (schedule == null || schedule.bodyBytes() == null) {
            return null;
        }
        final TransactionBody scheduledTxn;
        try {
            scheduledTxn = TransactionBody.parseFrom(schedule.bodyBytes());
        } catch (final InvalidProtocolBufferException e) {
            log.error("Could not parse schedule bodyBytes {}", schedule, e);
            return null;
        }
        return scheduledTxn;
    }
}
