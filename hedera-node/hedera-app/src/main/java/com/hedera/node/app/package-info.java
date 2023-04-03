/**
 * Main package for the Hedera node application.
 *
 * <p>The Hashgraph Platform today follows a "container-managed" model, where applications extend from
 * {@link com.swirlds.common.system.SwirldMain} to define their main entry point, much like a Java
 * Applet would extend from {@code Applet}. An application also extends from
 * {@link com.swirlds.common.system.SwirldState2} to define its Merkle tree for holding state. The platform
 * then dynamically looks up and creates these objects. The platform is thus in charge of lifecycle
 * management.
 *
 * <p>In the future, the Hashgraph Platform will instead use a "library" model, where the platform exposes
 * a {@code PlatformBuilder}, and the application is responsible for managing the lifecycle of the platform.
 * The platform will then be a library that the application can use to build a node. This makes testing
 * much easier and the code easier to understand.
 *
 * <p>In addition, the Hedera Consensus Node application has two possible code paths: one that leads through
 * the mono-service (this is the current production code path), and one that leads through the "workflows"
 * of the modular application. The {@link com.hedera.node.app.spi.config.PropertyNames#WORKFLOWS_ENABLED}
 * flag indicates whether to use the modular workflows or to use the mono-service.
 *
 * <p>The main entry point for the application today is {@link com.swirlds.platform.Browser}, which is
 * configured to find and load {@link com.hedera.node.app.ServicesMain} as the
 * {@link com.swirlds.common.system.SwirldMain} of the application. If the {@code WORKFLOWS_ENABLED} flag is
 * set, then {@link com.hedera.node.app.ServicesMain} will create an instance of {@link com.hedera.node.app.Hedera}
 * and delegate to it. Otherwise, it will create an instance of {@link com.hedera.node.app.MonoServicesMain}
 * and delegate to it.
 *
 * <p><img src="startup-flow.png" alt="Startup Flow">
 */
package com.hedera.node.app;
