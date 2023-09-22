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

package com.hedera.services.bdd.suites.contract.classiccalls;

import com.hedera.services.bdd.spec.HapiSpec;
import com.hederahashgraph.api.proto.java.ContractFunctionResult;
import com.hederahashgraph.api.proto.java.TransactionRecord;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.util.List;

/**
 * Defines a classic HTS call that can (perhaps) fail given for example invalid
 * amounts, account IDs, token IDs, or NFT IDs. See also {@link ClassicFailureMode}.
 *
 * <p>If a call returns that it can experience a failure mode (via
 * {@link FailableClassicCall#hasFailureMode(ClassicFailureMode)}), then the call must
 * failure mode the call can experience, there is a corresponding method
 */
public interface FailableClassicCall {
    /**
     * Does something to report on the observed failure modes.
     */
    void reportOnAssertedFailureModes();

    /**
     * Asserts the given records are as expected, given the failure mode within the given spec.
     */
    void assertExpectedRecords(List<TransactionRecord> records, HapiSpec spec, ClassicFailureMode mode);

    /**
     * Asserts the given static call result is as expected, given the failure mode within the given spec.
     */
    void assertExpectedResult(ContractFunctionResult result, HapiSpec spec, ClassicFailureMode mode);

    /**
     * Returns the name of the call.
     *
     * @return the name of the call
     */
    String name();

    /**
     * Returns whether this call can experience the given failure mode.
     *
     * @param mode the failure mode to check
     * @return whether this call can experience the given failure mode
     */
    boolean hasFailureMode(@NonNull ClassicFailureMode mode);

    /**
     * Returns the encoded failure value for the given failure mode, using
     * the given spec to resolve inventory ids from their names in
     * {@link ClassicInventory}.
     *
     * @param mode the failure mode to encode
     * @param spec the spec to use to resolve inventory ids
     * @return the encoded call that should experience the given failure mode
     */
    byte[] encodedFailureFromInventory(@NonNull ClassicFailureMode mode, @NonNull HapiSpec spec);

    /**
     * Returns whether this call can be made statically.
     *
     * @return whether this call can be made statically
     */
    boolean staticCallOk();
}
