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

import com.swirlds.common.system.NodeId;
import com.swirlds.platform.bls.message.ProtocolMessage;

/**
 * Record of an incident, where a counterparty was disqualified
 *
 * @param subject the ID of the node which caused the incident, and was disqualified
 * @param description a brief description of the reason for disqualification
 * @param message the message which resulted in the disqualification. can be either a bad message
 *     from the subject, a complaint from a different party revealing the malice of the subject, or
 *     null if a message was simply missing from the subject
 */
public record IncidentReport(NodeId subject, String description, ProtocolMessage message) {}
