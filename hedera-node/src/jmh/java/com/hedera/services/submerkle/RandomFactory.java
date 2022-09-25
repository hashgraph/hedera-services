/*
 * Copyright (C) 2022 Hedera Hashgraph, LLC
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
package com.hedera.services.submerkle;

import com.google.common.primitives.Longs;
import com.hedera.services.context.EvmResultRandomParams;
import com.hedera.services.context.FullEvmResult;
import com.hedera.services.contracts.execution.HederaMessageCallProcessor;
import com.hedera.services.contracts.execution.TransactionProcessingResult;
import com.hedera.services.state.merkle.internals.BitPackUtils;
import com.hederahashgraph.api.proto.java.ContractID;
import com.swirlds.common.utility.CommonUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.TreeMap;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;
import org.hyperledger.besu.datatypes.Address;
import org.hyperledger.besu.evm.frame.ExceptionalHaltReason;
import org.hyperledger.besu.evm.log.Log;
import org.hyperledger.besu.evm.log.LogTopic;

public class RandomFactory {
    private final SplittableRandom r;

    public RandomFactory(final SplittableRandom r) {
        this.r = r;
    }

    public FullEvmResult randomEvmResult(final EvmResultRandomParams params) {
        final var doCreation = r.nextDouble() < params.creationProbability();
        if (doCreation) {
            return new FullEvmResult(randomSuccess(params), randomAddress().toArrayUnsafe());
        } else {
            final var doSuccess = r.nextDouble() < params.callSuccessProbability();
            return doSuccess
                    ? new FullEvmResult(randomSuccess(params), null)
                    : new FullEvmResult(randomFailure(params), null);
        }
    }

    private TransactionProcessingResult randomSuccess(final EvmResultRandomParams params) {
        final var logs = randomLogs(params.maxLogs(), params.maxLogData(), params.maxLogTopics());
        final var output = Bytes.wrap(randomBytes(32 * r.nextInt(params.maxOutputWords())));
        // TODO(Nathan): An 8th argument was added to the method but this code was no updated. I
        // added Collections.emptyList() to achieve successful compilation, but this logic may not
        // be correct.
        final var ans =
                TransactionProcessingResult.successful(
                        logs,
                        randomNonNegativeLong(),
                        randomNonNegativeLong(),
                        randomNonNegativeLong(),
                        output,
                        randomAddress(),
                        randomStateChanges(params),
                        Collections.emptyList());
        if (r.nextDouble() < params.creationProbability()) {
            ans.setCreatedContracts(randomCreations(params.maxCreations()));
        }
        return ans;
    }

    private List<ContractID> randomCreations(final int maxCreations) {
        int n = r.nextInt(maxCreations) + 1;
        final List<ContractID> ans = new ArrayList<>();
        while (n-- > 0) {
            ans.add(ContractID.newBuilder().setContractNum(randomNumInScope()).build());
        }
        return ans;
    }

    private TransactionProcessingResult randomFailure(final EvmResultRandomParams params) {
        Optional<Bytes> revertReason = Optional.empty();
        Optional<ExceptionalHaltReason> haltReason = Optional.empty();
        if (r.nextBoolean()) {
            revertReason = Optional.of(HederaMessageCallProcessor.INVALID_TRANSFER);
        } else {
            haltReason = Optional.of(ExceptionalHaltReason.INSUFFICIENT_GAS);
        }
        // TODO(Nathan): An 8th argument was added to the method but this code was no updated. I
        // added Collections.emptyList() to achieve successful compilation, but this logic may not
        // be correct.
        return TransactionProcessingResult.failed(
                randomNonNegativeLong(),
                randomNonNegativeLong(),
                randomNonNegativeLong(),
                revertReason,
                haltReason,
                randomStateChanges(params),
                Collections.emptyList());
    }

    private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> randomStateChanges(
            final EvmResultRandomParams params) {
        return params.enableTraceability()
                ? randomStateChanges(
                        params.numAddressesWithChanges(), params.numStateChangesPerAddress())
                : Collections.emptyMap();
    }

    private List<Log> randomLogs(final int maxLogs, final int maxLogData, final int maxLogTopics) {
        var numLogs = r.nextInt(maxLogs);
        if (numLogs == 0) {
            return Collections.emptyList();
        }
        final List<Log> ans = new ArrayList<>(numLogs);
        while (numLogs-- > 0) {
            ans.add(randomLog(maxLogData, maxLogTopics));
        }
        return ans;
    }

    private Log randomLog(final int maxLogData, final int maxLogTopics) {
        final var data = Bytes.wrap(randomBytes(maxLogData));
        final var topics = randomTopics(maxLogTopics);
        return new Log(randomAddress(), data, topics);
    }

    private List<LogTopic> randomTopics(final int maxLogTopics) {
        if (maxLogTopics == 0) {
            return Collections.emptyList();
        }
        int n = maxLogTopics;
        final List<LogTopic> ans = new ArrayList<>(maxLogTopics);
        while (n-- > 0) {
            ans.add(LogTopic.of(Bytes32.wrap(randomBytes(32))));
        }
        return ans;
    }

    private Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> randomStateChanges(
            final int numAddressesWithChanges, final int changesPerAddress) {
        final Map<Address, Map<Bytes, Pair<Bytes, Bytes>>> ans = new TreeMap<>();
        int n = numAddressesWithChanges;
        while (n-- > 0) {
            final var address = randomAddress();
            final Map<Bytes, Pair<Bytes, Bytes>> changes = new TreeMap<>();
            for (int i = 0; i < changesPerAddress; i++) {
                changes.put(randomEvmWord(), randomStateChangePair());
            }
            ans.put(address, changes);
        }
        return ans;
    }

    private Address randomAddress() {
        byte[] ans = new byte[20];
        if (r.nextBoolean()) {
            r.nextBytes(ans);
        } else {
            System.arraycopy(Longs.toByteArray(randomNumInScope()), 0, ans, 12, 8);
        }
        return Address.fromHexString(CommonUtils.hex(ans));
    }

    private Pair<Bytes, Bytes> randomStateChangePair() {
        if (r.nextBoolean()) {
            return Pair.of(randomEvmWord(), null);
        } else {
            return Pair.of(randomEvmWord(), randomEvmWord());
        }
    }

    private Bytes randomEvmWord() {
        if (r.nextBoolean()) {
            return Bytes.ofUnsignedLong(r.nextLong()).trimLeadingZeros();
        } else {
            return Bytes.wrap(randomBytes(32)).trimLeadingZeros();
        }
    }

    private byte[] randomBytes(final int n) {
        final var ans = new byte[n];
        r.nextBytes(ans);
        return ans;
    }

    private long randomNumInScope() {
        return r.nextLong(BitPackUtils.MAX_NUM_ALLOWED);
    }

    private long randomNonNegativeLong() {
        return r.nextLong(Long.MAX_VALUE);
    }
}
