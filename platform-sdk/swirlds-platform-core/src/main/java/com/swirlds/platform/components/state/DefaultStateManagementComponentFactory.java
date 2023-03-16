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

package com.swirlds.platform.components.state;

import static com.swirlds.common.formatting.StringFormattingUtils.addLine;

import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.address.AddressBook;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.platform.components.common.output.FatalErrorConsumer;
import com.swirlds.platform.components.common.query.PrioritySystemTransactionSubmitter;
import com.swirlds.platform.components.state.output.IssConsumer;
import com.swirlds.platform.components.state.output.NewLatestCompleteStateConsumer;
import com.swirlds.platform.components.state.output.StateHasEnoughSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateLacksSignaturesConsumer;
import com.swirlds.platform.components.state.output.StateToDiskAttemptConsumer;
import com.swirlds.platform.crypto.PlatformSigner;
import com.swirlds.platform.dispatch.triggers.control.HaltRequestedConsumer;
import com.swirlds.platform.event.preconsensus.PreConsensusEventWriter;

/**
 * Creates instances of {@link DefaultStateManagementComponent}
 */
public class DefaultStateManagementComponentFactory implements StateManagementComponentFactory {

    private final PlatformContext context;
    private final ThreadManager threadManager;
    private final AddressBook addressBook;
    private final PlatformSigner signer;
    private final String mainClassName;
    private final NodeId selfId;
    private final String swirldName;
    private PrioritySystemTransactionSubmitter prioritySystemTransactionSubmitter;
    private StateToDiskAttemptConsumer stateToDiskAttemptConsumer;
    private NewLatestCompleteStateConsumer newLatestCompleteStateConsumer;
    private StateLacksSignaturesConsumer stateLacksSignaturesEventConsumer;
    private StateHasEnoughSignaturesConsumer stateHasEnoughSignaturesConsumer;
    private IssConsumer issConsumer;
    private HaltRequestedConsumer haltRequestedConsumer;
    private FatalErrorConsumer fatalErrorConsumer;
    private PreConsensusEventWriter preConsensusEventWriter;

    public DefaultStateManagementComponentFactory(
            final PlatformContext context,
            final ThreadManager threadManager,
            final AddressBook addressBook,
            final PlatformSigner signer,
            final String mainClassName,
            final NodeId selfId,
            final String swirldName) {

        this.context = context;
        this.threadManager = threadManager;
        this.addressBook = addressBook;
        this.signer = signer;
        this.mainClassName = mainClassName;
        this.selfId = selfId;
        this.swirldName = swirldName;
    }

    @Override
    public StateManagementComponentFactory prioritySystemTransactionConsumer(
            final PrioritySystemTransactionSubmitter submitter) {
        this.prioritySystemTransactionSubmitter = submitter;
        return this;
    }

    @Override
    public StateManagementComponentFactory stateToDiskConsumer(final StateToDiskAttemptConsumer consumer) {
        this.stateToDiskAttemptConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory newLatestCompleteStateConsumer(
            final NewLatestCompleteStateConsumer consumer) {
        this.newLatestCompleteStateConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory stateLacksSignaturesConsumer(final StateLacksSignaturesConsumer consumer) {
        this.stateLacksSignaturesEventConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory newCompleteStateConsumer(final StateHasEnoughSignaturesConsumer consumer) {
        this.stateHasEnoughSignaturesConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory issConsumer(final IssConsumer consumer) {
        this.issConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory haltRequestedConsumer(final HaltRequestedConsumer consumer) {
        this.haltRequestedConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory fatalErrorConsumer(final FatalErrorConsumer consumer) {
        this.fatalErrorConsumer = consumer;
        return this;
    }

    @Override
    public StateManagementComponentFactory setPreConsensusEventWriter(PreConsensusEventWriter preConsensusEventWriter) {
        this.preConsensusEventWriter = preConsensusEventWriter;
        return this;
    }

    @Override
    public StateManagementComponent build() {
        verifyInputs();
        return new DefaultStateManagementComponent(
                context,
                threadManager,
                addressBook,
                signer,
                mainClassName,
                selfId,
                swirldName,
                prioritySystemTransactionSubmitter,
                stateToDiskAttemptConsumer,
                newLatestCompleteStateConsumer,
                stateLacksSignaturesEventConsumer,
                stateHasEnoughSignaturesConsumer,
                issConsumer,
                haltRequestedConsumer,
                fatalErrorConsumer,
                preConsensusEventWriter);
    }

    private void verifyInputs() {
        final StringBuilder errors = new StringBuilder();
        if (prioritySystemTransactionSubmitter == null) {
            addLine(errors, "prioritySystemTransactionSubmitter must not be null");
        }
        if (stateToDiskAttemptConsumer == null) {
            addLine(errors, "stateToDiskConsumer must not be null");
        }
        if (newLatestCompleteStateConsumer == null) {
            addLine(errors, "newLatestCompleteStateConsumer must not be null");
        }
        if (stateLacksSignaturesEventConsumer == null) {
            addLine(errors, "stateLacksSignaturesEventConsumer must not be null");
        }
        if (stateHasEnoughSignaturesConsumer == null) {
            addLine(errors, "newCompleteStateConsumer must not be null");
        }
        if (issConsumer == null) {
            addLine(errors, "issConsumer must not be null");
        }
        if (haltRequestedConsumer == null) {
            addLine(errors, "haltRequestedConsumer must not be null");
        }
        if (fatalErrorConsumer == null) {
            addLine(errors, "fatalErrorConsumer must not be null");
        }
        if (preConsensusEventWriter == null) {
            addLine(errors, "preConsensusEventWriter must not be null");
        }
        if (!errors.isEmpty()) {
            throw new IllegalStateException("Unable to build StateManagementComponent:\n" + errors);
        }
    }
}
