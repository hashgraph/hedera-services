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

package com.hedera.node.blocknode.core.downloader.provider;

/**
 * A stream block provider abstracts away the source of stream block files provided by cloud or local providers.
 */
public interface BlockProvider {
    //    /**
    //     * Fetches a stream file from a particular node upon subscription.
    //     *
    //     * @param node           the consensus node to download from
    //     * @param streamFilename the stream filename to download
    //     * @return the downloaded stream file data, wrapped in a Mono
    //     */
    //    Mono<StreamFileData> get(ConsensusNode node, StreamFilename streamFilename);
    //
    //    /**
    //     * Lists and downloads signature files for a particular node upon subscription. Uses the provided lastFilename
    // to
    //     * search for files lexicographically and chronologically after the last confirmed stream file.
    //     *
    //     * @param node         the consensus node to search
    //     * @param lastFilename the filename of the last downloaded stream file
    //     * @return The data associated with one or more stream files, wrapped in a Flux
    //     */
    //    Flux<StreamFileData> list(ConsensusNode node, StreamFilename lastFilename);
}
