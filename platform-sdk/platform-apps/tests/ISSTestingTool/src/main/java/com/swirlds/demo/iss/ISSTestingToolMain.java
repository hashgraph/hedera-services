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

package com.swirlds.demo.iss;

import com.swirlds.common.system.BasicSoftwareVersion;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.PlatformWithDeprecatedMethods;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.IssNotification;
import java.util.Random;

/**
 * <p>
 * An application that can be made to ISS in controllable ways.
 * </p>
 *
 * <p>
 * Arguments:
 * <ol>
 * <li>
 * Integer value, the network wide TPS (not TPS per node!)
 * </li>
 * <li>
 * <p>
 * A description of the desired ISS behavior.
 * </p>
 * <p>
 * One or more descriptions of an ISS is formatted like this:
 * </p>
 * <pre>
 * 1234:0,1+2+3
 * </pre>
 * <p>
 * The first number is a time, in seconds, after genesis, when the ISS will be triggered. Here
 * the ISS will be triggered 1234 seconds after genesis (measured by consensus time).
 * </p>
 * <p>
 * The time MUST be followed by a ":".
 * </p>
 * <p>
 * Next is a "-" separated list of ISS partitions. Each ISS partition will agree with other nodes in the same
 * partition, and disagree with any node not in the partition. In this example, node 0 is in an ISS partition by
 * itself, and nodes 1 2 and 3 are in a partition together. Nodes in the same partition should be separated by
 * a "+" symbol.
 * </p>
 * <p>
 * A few more examples:
 * </p>
 * <ul>
 * <li>
 * "60:0-1-2-3": 60 seconds after the app is started, all nodes disagree with all other nodes
 * </li>
 * <li>
 * "600:0+1-2+3": 10 minutes after start, the network splits in half. 0 and 1 agree, 2 and 3 agree.
 * </li>
 * <li>
 * "120:0+1-2+3-4+5+6": a seven node network. The ISS is triggered 120 seconds after start. Nodes 0 and 1
 * agree with each other, nodes 2 and 3 agree with each other, and nodes 4 5 and 6 agree with each other.
 * </li>
 * </ul>
 *
 * <p>
 * Multiple ISS events can be scheduled during the same test run. Each ISS event is its own argument, and should
 * be formatted in the same way.
 * </p>
 *
 * <p>
 * If multiple ISS events are scheduled, it's important that they be arranged in chronological order
 * in the argument. Breaking this rule may cause undefined behavior.
 * </p>
 *
 * </li>
 * </ol>
 */
public class ISSTestingToolMain implements SwirldMain {

    private static final BasicSoftwareVersion softwareVersion = new BasicSoftwareVersion(1);

    private Platform platform;

    private int transactionsPerSecond;

    public ISSTestingToolMain() {}

    /**
     * {@inheritDoc}
     */
    @Override
    public void init(final Platform platform, final NodeId id) {
        this.platform = platform;

        platform.getNotificationEngine().register(IssListener.class, this::issListener);

        parseArguments(((PlatformWithDeprecatedMethods) platform).getParameters());
    }

    private void parseArguments(final String[] args) {
        if (args.length == 0) {
            throw new IllegalArgumentException("Expected 1 or more arguments. See javadocs for details.");
        }
        transactionsPerSecond = Integer.parseInt(args[0]);
    }

    /**
     * Called when there is an ISS.
     */
    private void issListener(final IssNotification notification) {
        // Quan: this is a good place to write logs that the validators catch
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void run() {
        new TransactionGenerator(new Random(), platform, transactionsPerSecond).start();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public SwirldState newState() {
        return new ISSTestingToolState();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BasicSoftwareVersion getSoftwareVersion() {
        return softwareVersion;
    }
}
