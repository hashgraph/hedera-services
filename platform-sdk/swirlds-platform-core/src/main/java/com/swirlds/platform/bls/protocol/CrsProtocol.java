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

package com.swirlds.platform.bls.protocol;

import static com.swirlds.platform.Utilities.isSuperMajority;

import com.hedera.platform.bls.api.BilinearMap;
import com.hedera.platform.bls.api.GroupElement;
import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.addressbook.BlsAddressBook;
import com.swirlds.platform.bls.message.CommitmentMessage;
import com.swirlds.platform.bls.message.OpeningMessage;
import com.swirlds.platform.bls.message.ProtocolMessage;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * A class defining the Common Reference String (CRS) protocol, to be invoked by each party in the genesis address book.
 * The CRS protocol allows members of the genesis committee to collaborate in creating group element generators, which
 * form the foundation of threshold signatures, and IBE encryption.
 * <p>
 * The CRS is intended to only be generated once, by the members of the genesis address book
 * <p>
 * The protocol is of the Commit-Reveal style, which ensures an attacker cannot exert influence on the protocol results
 * by taking the contributions of other parties into account before creating their own contributions.
 * <p>
 * See reference material for more information: T. P. Pedersen. “A Threshold Cryptosystem without a Trusted Party”. In:
 * Advances in Cryptology – EUROCRYPT ’91. Our CRS Protocol is the first protocol of this source.
 */
public class CrsProtocol implements BlsProtocol<Crs> {
    /**
     * All the commitments that were broadcast in round 1
     */
    private final Map<NodeId, byte[]> commitments;

    /**
     * An object containing the random group elements this protocol generates, commits to, and finally opens
     */
    private final RandomGroupElements randomGroupElements;

    /**
     * The bilinear map that will be used throughout the BLS protocols
     */
    private final BilinearMap bilinearMap;

    /**
     * The address book of nodes performing this protocol
     */
    protected final BlsAddressBook addressBook;

    /**
     * The id of this node, where the protocol is running
     */
    private final NodeId nodeId;

    /**
     * The {@link Crs} output object created by the protocol. Only valid if the protocol has completed successfully
     */
    private Crs crsOutput;

    /**
     * The manager for this protocol
     */
    private final BlsProtocolManager<Crs> protocolManager;

    /**
     * Constructor
     *
     * @param addressBook     the address book running this protocol
     * @param nodeId          the id of this node
     * @param protocolManager the manager for the protocol
     * @param bilinearMap     the bilinear map
     */
    public CrsProtocol(
            final BlsAddressBook addressBook,
            final NodeId nodeId,
            final BlsProtocolManager<Crs> protocolManager,
            final BilinearMap bilinearMap) {

        this.addressBook = addressBook;
        this.nodeId = nodeId;

        this.protocolManager = protocolManager;
        this.protocolManager.addRound(this::broadcastCommitment);
        this.protocolManager.addRound(this::verifyAndOpen);
        this.protocolManager.setDisqualificationCleanup(this::cleanupAfterDisqualification);
        this.protocolManager.setViabilityChecker(this::isProtocolViable);
        this.protocolManager.setFinishingMethod(this::performFinish);

        this.commitments = new HashMap<>();
        this.bilinearMap = bilinearMap;

        this.randomGroupElements = new RandomGroupElements(bilinearMap, protocolManager.getRandom());
    }

    /**
     * <strong>ROUND 1</strong>
     * <p>
     * Broadcasts a commitment to the random group elements
     *
     * @return the {@link CommitmentMessage}
     */
    private ProtocolMessage broadcastCommitment(final List<ProtocolMessage> inputMessages) {
        try {
            return new CommitmentMessage(nodeId, randomGroupElements.commit());
        } catch (final NoSuchAlgorithmException e) {
            protocolManager.errorOccurred();
            throw new RuntimeException(e);
        }
    }

    /**
     * <strong>ROUND 2</strong>
     * <p>
     * Verifies that an honest majority of parties broadcast a {@link CommitmentMessage} in round 1. If this is
     * determined to be true, openly reveal random group elements
     *
     * @param inputMessages a list of {@link ProtocolMessage}s received in round 1
     * @return an {@link OpeningMessage}
     */
    private ProtocolMessage verifyAndOpen(final List<ProtocolMessage> inputMessages) {
        final List<CommitmentMessage> commitmentMessages = protocolManager.filterCast(
                inputMessages, addressBook.getSortedNodeIds(), CommitmentMessage.class, true);

        // Gather the commitments from the messages
        commitmentMessages.forEach(message -> commitments.put(message.getSenderId(), message.getCommitment()));

        // Open my random elements
        return new OpeningMessage(nodeId, randomGroupElements);
    }

    /**
     * <p>Throws an error if not enough commitments were received in the last round</p>
     */
    private Crs performFinish(final List<ProtocolMessage> inputMessages) {
        // Cast messages to correct type
        final List<OpeningMessage> openingMessages =
                protocolManager.filterCast(inputMessages, addressBook.getSortedNodeIds(), OpeningMessage.class, true);

        final List<GroupElement> generator1Elements = new ArrayList<>();
        final List<GroupElement> generator2Elements = new ArrayList<>();

        for (final OpeningMessage message : openingMessages) {
            final NodeId dealer = message.getSenderId();

            final byte[] commitment;
            try {
                // Recompute commitment from the opening
                commitment = message.getRandomGroupElements().commit();
            } catch (final NoSuchAlgorithmException e) {
                protocolManager.errorOccurred();
                throw new RuntimeException(e);
            }

            // If the recomputed commitment doesn't match what was originally sent, don't count the commitment
            if (!Arrays.equals(commitment, commitments.get(dealer))) {
                protocolManager.declareMaliciousCounterparty(dealer, "opening doesn't match commitment", message);

                continue;
            }

            // h is product of h_i
            generator1Elements.add(message.getRandomGroupElements().getRandomGroupElement1());
            generator2Elements.add(message.getRandomGroupElements().getRandomGroupElement2());
        }

        crsOutput = new Crs(
                bilinearMap,
                bilinearMap.keyGroup().batchMultiply(generator1Elements),
                bilinearMap.keyGroup().batchMultiply(generator2Elements));

        return crsOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Crs getOutput() {
        return crsOutput;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BlsProtocolManager<Crs> getProtocolManager() {
        return protocolManager;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public BilinearMap getBilinearMap() {
        return bilinearMap;
    }

    /**
     * Method satisfying the {@link DisqualificationCleanup} interface
     *
     * @param nodeId the id of the node that was disqualified
     */
    private void cleanupAfterDisqualification(final NodeId nodeId) {
        commitments.remove(nodeId);
    }

    /**
     * Method satisfying the {@link ProtocolViabilityChecker} interface
     *
     * @return true if the protocol is still viable, false otherwise
     */
    private boolean isProtocolViable() {
        // if disqualified nodes make up a supermajority, the protocol is no longer viable
        return !isSuperMajority(
                addressBook.getCombinedShares(protocolManager.getMaliciousNodes())
                        + addressBook.getCombinedShares(protocolManager.getOfflineNodes()),
                addressBook.getTotalShares());
    }
}
