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

package com.hedera.services.bdd.junit.hedera.embedded;

import com.hedera.node.app.Hedera;
import com.hedera.node.app.fixtures.state.FakeState;
import com.hedera.services.bdd.junit.hedera.embedded.fakes.FakeTssBaseService;
import com.hederahashgraph.api.proto.java.AccountID;
import com.hederahashgraph.api.proto.java.Query;
import com.hederahashgraph.api.proto.java.Response;
import com.hederahashgraph.api.proto.java.Timestamp;
import com.hederahashgraph.api.proto.java.Transaction;
import com.hederahashgraph.api.proto.java.TransactionID;
import com.hederahashgraph.api.proto.java.TransactionResponse;
import com.swirlds.platform.system.SoftwareVersion;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.time.Duration;
import java.time.Instant;

public interface EmbeddedHedera {
    /**
     * Starts the embedded Hedera node.
     */
    void start();

    /**
     * Stops the embedded Hedera node.
     */
    void stop();

    /**
     * Returns the fake state of the embedded Hedera node.
     * @return the fake state of the embedded Hedera node
     */
    FakeState state();

    /**
     * Returns the fake TSS base service of the embedded Hedera node.
     * @return the fake TSS base service of the embedded Hedera node
     */
    FakeTssBaseService tssBaseService();

    /**
     * Returns the software version of the embedded Hedera node.
     * @return the software version of the embedded Hedera node
     */
    SoftwareVersion version();

    /**
     * Returns the next in a repeatable sequence of valid start times that the embedded Hedera's
     * ingest workflow will accept within a {@link TransactionID}.
     *
     * <p>Ensures unique timestamps by incrementing the nanosecond field of the timestamp each time
     * this method is called---which will definitely be fewer than a billion times in a second.
     *
     * @return the next valid start time from a repeatable sequence
     */
    Timestamp nextValidStart();

    /**
     * Returns the current synthetic time in the embedded Hedera node.
     *
     * @return the current synthetic time
     */
    Instant now();

    /**
     * Returns the embedded Hedera.
     * @return the embedded Hedera
     */
    Hedera hedera();

    /**
     * Advances the synthetic time in the embedded Hedera node by a given duration.
     */
    void tick(@NonNull Duration duration);

    /**
     * Submits a transaction to the embedded node.
     *
     * @param transaction the transaction to submit
     * @param nodeAccountId the account ID of the node to submit the transaction to
     * @return the response to the transaction
     */
    default TransactionResponse submit(@NonNull Transaction transaction, @NonNull AccountID nodeAccountId) {
        return submit(transaction, nodeAccountId, SyntheticVersion.PRESENT);
    }

    /**
     * Submits a transaction to the embedded node.
     *
     * @param transaction the transaction to submit
     * @param nodeAccountId the account ID of the node to submit the transaction to
     * @param version the synthetic version of the transaction
     * @return the response to the transaction
     */
    TransactionResponse submit(
            @NonNull Transaction transaction, @NonNull AccountID nodeAccountId, @NonNull SyntheticVersion version);

    /**
     * Sends a query to the embedded node.
     *
     * @param query the query to send
     * @param nodeAccountId the account ID of the node to send the query to
     * @return the response to the query
     */
    Response send(@NonNull Query query, @NonNull AccountID nodeAccountId, final boolean asNodeOperator);
}
