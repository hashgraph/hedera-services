/*
 * Copyright (C) 2025 Hedera Hashgraph, LLC
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

/**
 * Main package for the Hedera node application.
 *
 * <p>The Hashgraph Platform today follows a "container-managed" model, where applications extend from
 * {@link com.swirlds.platform.system.SwirldMain} to define their main entry point, much like a Java Applet would extend
 * from {@code Applet}. An application also extends from {@link com.swirlds.state.merkle.MerkleStateRoot} to define its
 * Merkle tree for holding state. The platform then dynamically looks up and creates these objects. The platform is thus
 * in charge of lifecycle management.
 *
 * <p>In the future, the Hashgraph Platform will instead use a "library" model, where the platform exposes
 * a {@code PlatformBuilder}, and the application is responsible for managing the lifecycle of the platform. The
 * platform will then be a library that the application can use to build a node. This makes testing much easier and the
 * code easier to understand.
 *
 * <p>The main entry point for the application today is {@link com.swirlds.platform.Browser}, which is
 * configured to find and load {@link com.hedera.node.app.ServicesMain} as the
 * {@link com.swirlds.platform.system.SwirldMain} of the application.
 *
 * <p><img src="startup-flow.png" alt="Startup Flow">
 */
package com.hedera.node.app;
