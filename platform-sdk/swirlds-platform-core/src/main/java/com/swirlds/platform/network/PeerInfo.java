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

package com.swirlds.platform.network;

import com.swirlds.common.platform.NodeId;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.security.cert.Certificate;

/**
 * A record representing a peer's network information.
 *
 * @param nodeId             the ID of the peer
 * @param nodeName           the name of the peer
 * @param hostname           the hostname (or IP address) of the peer
 * @param signingCertificate the certificate used to validate the peer's TLS certificate
 */
public record PeerInfo(
        @NonNull NodeId nodeId,
        @NonNull String nodeName,
        @NonNull String hostname,
        @NonNull Certificate signingCertificate) {}
