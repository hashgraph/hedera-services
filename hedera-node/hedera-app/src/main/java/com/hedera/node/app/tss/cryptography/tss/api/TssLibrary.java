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

package com.hedera.node.app.tss.cryptography.tss.api;

import edu.umd.cs.findbugs.annotations.NonNull;

/**
 * A Threshold Signature Scheme Service.
 * <p>
 * Contract of TssService:
 *   <ul>
 *       <li>Get a {@link TssServiceGenesisStage}: Returns the genesis stage</li>
 *       <li>Get a {@link TssServiceRekeyStage}: Returns the rekey stage.</li>
 *   </ul>
 */
public interface TssLibrary {

    /**
     * In this stage all participants collaborate to discover a shared polynomial.
     * <p>
     * Contract of {@link TssServiceGenesisStage} stage:
     * <ul>
     *     <li>Generate {@link TssMessage} from a random share</li>
     *     <li>Verify {@link TssMessage} with a {@link TssParticipantDirectory}</li>
     *     <li>Obtain the list of {@link TssPrivateShare} with a {@link TssParticipantDirectory}</li>
     *     <li>Obtain the list of {@link TssPublicShare} with a {@link TssParticipantDirectory}</li>
     * </ul>
     * @return the genesis stage.
     */
    @NonNull
    TssServiceGenesisStage genesisStage();

    /**
     * In this stage all participants recover keys belonging to an already established polynomial.
     *  Contract of {@link TssServiceRekeyStage} stage:
     * <ul>
     *     <li>Generate {@link TssMessage} from a {@link TssPrivateShare}</li>
     *     <li>Verify {@link TssMessage} with a {@link TssParticipantDirectory},
     *        and all previous {@link TssPublicShare}</li>
     *     <li>Obtain the list of {@link TssPrivateShare} with a {@link TssParticipantDirectory}</li>
     *     <li>Obtain the list of {@link TssPublicShare} with a {@link TssParticipantDirectory}</li>
     * </ul>
     *
     * @return the rekey stage.
     */
    @NonNull
    TssServiceRekeyStage rekeyStage();

    /**
     * Creates a {@link TssMessage} from a byte array representation.
     * @see TssMessage#toBytes() for the specification that message needs to follow.
     * @param tssParticipantDirectory the candidate tss directory
     * @param message the byte representation of the opaque underlying structure used by the library
     * @return a TssMessage instance
     * @throws TssMessageParsingException in case of error while parsing the TssMessage from its byte array format
     */
    @NonNull
    TssMessage messageFromBytes(@NonNull TssParticipantDirectory tssParticipantDirectory, @NonNull byte[] message)
            throws TssMessageParsingException;
}
