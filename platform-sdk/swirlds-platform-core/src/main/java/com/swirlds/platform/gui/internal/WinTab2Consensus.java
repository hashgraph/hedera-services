/*
 * Copyright (C) 2016-2024 Hedera Hashgraph, LLC
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

package com.swirlds.platform.gui.internal;

import static com.swirlds.platform.gui.GuiUtils.wrap;

import com.swirlds.common.platform.NodeId;
import com.swirlds.platform.Consensus;
import com.swirlds.platform.gui.GuiPlatformAccessor;
import com.swirlds.platform.gui.GuiUtils;
import com.swirlds.platform.gui.components.PrePaintableJPanel;
import com.swirlds.platform.state.nexus.SignedStateNexus;
import com.swirlds.platform.state.signed.ReservedSignedState;
import com.swirlds.platform.system.Platform;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.awt.Font;
import java.awt.Graphics;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.swing.JTextArea;

/**
 * The tab in the Browser window that shows network speed, transactions per second, etc.
 */
class WinTab2Consensus extends PrePaintableJPanel {
    /** this is needed for serializing */
    private static final long serialVersionUID = 1L;

    /** the entire table is in this single Component */
    private JTextArea text;

    private final Consensus consensus;
    private final NodeId firstNodeId;

    /**
     * Instantiate and initialize content of this tab.
     */
    public WinTab2Consensus(@NonNull final Consensus consnesus, @NonNull final NodeId firstNodeId) {
        text = GuiUtils.newJTextArea("");
        this.consensus = Objects.requireNonNull(consnesus);
        this.firstNodeId = Objects.requireNonNull(firstNodeId);
        add(text);
    }

    /** {@inheritDoc} */
    @Override
    public void prePaint() {
        try {
            if (WinBrowser.memberDisplayed == null) {
                return;
            }
            Platform platform = WinBrowser.memberDisplayed.getPlatform();
            String s = "";
            s += "Node" + firstNodeId.id();
            long r1 = consensus.getDeleteRound();
            long r2 = consensus.getFameDecidedBelow();
            long r3 = consensus.getMaxRound();
            final SignedStateNexus latestImmutableStateComponent =
                    GuiPlatformAccessor.getInstance().getLatestImmutableStateComponent(firstNodeId);
            final SignedStateNexus latestCompleteStateComponent =
                    GuiPlatformAccessor.getInstance().getLatestCompleteStateComponent(firstNodeId);
            long r0 = latestCompleteStateComponent.getRound();

            if (r1 == -1) {
                s += "\n           = latest deleted round-created";
            } else {
                s += String.format("\n%,10d = latest deleted round-created", r1);
            }
            if (r0 == -1) {
                s += String.format("\n           = latest supermajority signed state round-decided");
            } else {
                s += String.format(
                        "\n%,10d = latest supermajority signed state round-decided (deleted round +%,d)", r0, r0 - r1);
            }
            s += String.format("\n%,10d = latest round-decided (delete round +%,d)", r2, r2 - r1);
            s += String.format("\n%,10d = latest round-created (deleted round +%,d)", r3, r3 - r1);

            final List<GuiStateInfo> stateInfo = new ArrayList<>();
            for (final SignedStateNexus nexus : List.of(latestCompleteStateComponent, latestImmutableStateComponent)) {
                try (final ReservedSignedState state = nexus.getState("GUI")) {
                    if (state == null) {
                        continue;
                    }
                    stateInfo.add(GuiStateInfo.from(state));
                }
            }

            GuiStateInfo first = null;
            if (!stateInfo.isEmpty()) {
                first = stateInfo.get(0);
            }
            long d = first == null ? 0 : first.round();
            // count of digits in round number
            d = String.format("%,d", d).length();
            // add 2 because earlier rounds might be 2 shorter, like 998 vs 1,002
            d += 2;

            s += "\n     Signed state for round:            ";
            for (final GuiStateInfo state : stateInfo) {
                s += String.format("%," + d + "d ", state.round());
            }

            s += "\n     Signatures collected:              ";
            for (final GuiStateInfo state : stateInfo) {
                int c = state.numSigs();
                s += String.format("%," + d + "d ", c);
            }

            s += "\n                                        ";
            for (GuiStateInfo state : stateInfo) {
                int c = state.numSigs();
                int size = platform.getAddressBook().getSize();

                s += String.format("%" + d + "s ", c == size ? "___" : state.isComplete() ? "ooo" : "###");
            }

            s += wrap(
                    70,
                    "\n\n"
                            + "After each round, there is a consensus state, which is the result "
                            + "of all the transactions so far, in their consensus order. Each "
                            + "member signs that state, and sends out their signature. \n\n"
                            + "The above "
                            + "shows how one member (shown at the top of this window) is collecting "
                            + "those signatures. It shows how many transactions have achieved "
                            + "consensus so far, and the latest round number that has its "
                            + " events discarded, the latest that has collected signatures from members "
                            + "with at least 1/3 of the total weight, the latest that has its "
                            + "famous witnesses decided (which is the core of the hashgraph consensus "
                            + "algorithm), and the latest that has at least one known event. \n\n"
                            + "For each round, the table shows the round number, then the "
                            + "count of how many signatures are collected so far, then an indication "
                            + "of whether this represents everyone (___), or not everyone but at least "
                            + "one third of weight (ooo), or even less than that (###).");
            text.setFont(new Font("monospaced", Font.PLAIN, 14));

            text.setText(s);
        } catch (java.util.ConcurrentModificationException err) {
            // We started displaying before all the platforms were added. That's ok.
        }
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
    }
}
