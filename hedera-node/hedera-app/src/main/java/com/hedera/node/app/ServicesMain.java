/*
 * Copyright (C) 2020-2023 Hedera Hashgraph, LLC
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

package com.hedera.node.app;

import static com.hedera.node.app.service.mono.context.AppsManager.APPS;
import static com.hedera.node.app.service.mono.context.properties.SemanticVersions.SEMANTIC_VERSIONS;
import static com.hedera.node.app.spi.config.PropertyNames.STATES_ENABLED;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.hedera.node.app.service.mono.ServicesApp;
import com.hedera.node.app.service.mono.ServicesState;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.hedera.node.app.state.merkle.MerkleHederaState;
import com.hederahashgraph.api.proto.java.SemanticVersion;
import com.swirlds.common.notification.listeners.PlatformStatusChangeListener;
import com.swirlds.common.notification.listeners.ReconnectCompleteListener;
import com.swirlds.common.notification.listeners.StateWriteToDiskCompleteListener;
import com.swirlds.common.system.NodeId;
import com.swirlds.common.system.Platform;
import com.swirlds.common.system.SoftwareVersion;
import com.swirlds.common.system.SwirldMain;
import com.swirlds.common.system.SwirldState2;
import com.swirlds.common.system.state.notifications.IssListener;
import com.swirlds.common.system.state.notifications.NewSignedStateListener;
import com.swirlds.platform.Browser;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Implements callbacks to bind gRPC services, react to platform status changes, and incorporate new
 * signed states.
 */
public class ServicesMain implements SwirldMain {
    private static final Logger log = LogManager.getLogger(ServicesMain.class);

    /** Stores information related to the running of services in the mono-repo */
    private ServicesApp app;

    /**
     * Stores information related to the running of the Hedera application in the modular app. This
     * is unused when the "hedera.workflows.enabled" flag is false.
     */
    private final Hedera hedera = new Hedera();

    /**
     * Convenience launcher for dev env.
     *
     * @param args ignored
     */
    public static void main(final String... args) {
        Browser.main(null);
    }

    @Override
    public SoftwareVersion getSoftwareVersion() {
        return SEMANTIC_VERSIONS.deployedSoftwareVersion();
    }

    @Override
    public void init(final Platform ignore, final NodeId nodeId) {
        try {
            app = APPS.get(nodeId.getId());
            initApp();
        } catch (final IllegalArgumentException iae) {
            log.error("No app present for {}", nodeId, iae);
            throw new AssertionError("Cannot continue without an app");
        }
    }

    @Override
    public SwirldState2 newState() {
        // TODO - replace this flag with a check whether the set of workflow-enabled
        // operations is non-empty (https://github.com/hashgraph/hedera-services/issues/4945)
        final var workflowsEnabled = new BootstrapProperties(false).getBooleanProperty(STATES_ENABLED);
        return stateWithWorkflowsEnabled(workflowsEnabled);
    }

    @Override
    public void run() {
        /* No-op. */
    }

    SwirldState2 stateWithWorkflowsEnabled(final boolean enabled) {
        return enabled ? newMerkleHederaState(Hedera::registerServiceSchemasForMigration) : new ServicesState();
    }

    MerkleHederaState newMerkleHederaState(
            final Function<SemanticVersion, Consumer<MerkleHederaState>> migrationFactory) {
        final var servicesSemVer = SEMANTIC_VERSIONS.deployedSoftwareVersion().getServices();
        log.info("Registering schemas for migration to {}", servicesSemVer);
        final var migration = migrationFactory.apply(servicesSemVer);
        return new MerkleHederaState(
                migration,
                (event, metadata, provider) -> metadata.app().eventExpansion().expandAllSigs(event, provider),
                (round, dualState, metadata) -> {
                    final var metaApp = metadata.app();
                    metaApp.dualStateAccessor().setDualState(dualState);
                    metaApp.logic().incorporateConsensus(round);
                });
    }

    private void initApp() {
        if (defaultCharsetIsCorrect() && sha384DigestIsAvailable()) {
            try {
                Locale.setDefault(Locale.US);
                consoleLog(String.format("Using context to initialize HederaNode#%s...", app.nodeId()));
                doStagedInit();
            } catch (final Exception e) {
                log.error("Fatal precondition violated in HederaNode#{}", app.nodeId(), e);
                app.systemExits().fail(1);
            }
        } else {
            app.systemExits().fail(1);
        }
    }

    private void doStagedInit() {
        validateLedgerState();
        log.info("Ledger state ok");

        configurePlatform();
        log.info("Platform is configured w/ callbacks and stats registered");

        exportAccountsIfDesired();
        log.info("Accounts exported (if requested)");

        startNettyIfAppropriate();
        log.info("Netty started (if appropriate)");
    }

    private void exportAccountsIfDesired() {
        app.accountsExporter().toFile(app.workingState().accounts());
    }

    private void startNettyIfAppropriate() {
        // The "hedera.workflows.enabled" feature flag indicates whether we enable the new gRPC
        // server and workflows, or use the existing gRPC handlers in mono-service.
        final var props = app.globalStaticProperties();
        if (props.workflowsEnabled()) {
            hedera.start(app, app.nodeLocalProperties().port());
        } else {
            app.grpcStarter().startIfAppropriate();
        }
    }

    private void configurePlatform() {
        final var platform = app.platform();
        app.statsManager().initializeFor(platform);
    }

    private void validateLedgerState() {
        app.ledgerValidator().validate(app.workingState().accounts());
        app.nodeInfo().validateSelfAccountIfStaked();
        final var notifications = app.notificationEngine().get();
        notifications.register(PlatformStatusChangeListener.class, app.statusChangeListener());
        notifications.register(ReconnectCompleteListener.class, app.reconnectListener());
        notifications.register(StateWriteToDiskCompleteListener.class, app.stateWriteToDiskListener());
        notifications.register(NewSignedStateListener.class, app.newSignedStateListener());
        notifications.register(IssListener.class, app.issListener());
    }

    private boolean defaultCharsetIsCorrect() {
        final var charset = app.nativeCharset().get();
        if (!UTF_8.equals(charset)) {
            log.error("Default charset is {}, not UTF-8", charset);
            return false;
        }
        return true;
    }

    private boolean sha384DigestIsAvailable() {
        try {
            app.digestFactory().forName("SHA-384");
            return true;
        } catch (final NoSuchAlgorithmException nsae) {
            log.error(nsae);
            return false;
        }
    }

    private void consoleLog(final String s) {
        log.info(s);
        app.consoleOut().ifPresent(c -> c.println(s));
    }
}
