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

package recordcache;

import com.hedera.node.app.ServicesMain;
import org.junit.jupiter.api.Test;

final class RecordCacheTest {
    @Test
    void foo() {
        final var main = new ServicesMain();

        //        main.setConfiguration(null); // TODO supply configuration here
        //        ConfigurationHolder.getInstance().setConfiguration(null); // TODO supply configuration here
        //
        //        CryptographyHolder.reset();
        //
        //        Settings.getInstance().loadSettings((Path) null); // TODO supply settings here
        //        Settings.populateSettingsCommon();

        // Update Settings based on config.txt
        //        configurationProperties.tls().ifPresent(tls -> Settings.getInstance()
        //                .setUseTLS(tls));
        //        configurationProperties.maxSyncs().ifPresent(value -> Settings.getInstance()
        //                .setMaxOutgoingSyncs(value));
        //        configurationProperties.transactionMaxBytes().ifPresent(value -> Settings.getInstance()
        //                .setTransactionMaxBytes(value));
        //        configurationProperties.ipTos().ifPresent(ipTos -> Settings.getInstance()
        //                .setSocketIpTos(ipTos));
        //        configurationProperties
        //                .saveStatePeriod()
        //                .ifPresent(value -> Settings.getInstance().getState().saveStatePeriod = value);

        //        final var nodeId = new NodeId(0);
        //        final var platformContext = new DefaultPlatformContext(nodeId, metricsProvider, configuration);
        //        final var swirldName = "Integration Test";
        //        final var appVersion = "0.0.1"; // TBD
        //        final var addressBook = new AddressBook(); // TODO supply addresses here
        //        final var platform = new SwirldsPlatform(crypto.get(addressBook.nodeId()), nodeId, addressBook,
        // platformContext, "main class name", swirldName, appVersion, appMain::newState, loadedSignedState, new
        // EmergencyRecoveryManager());
        //
        //        main.init(platform, nodeId);
        //        platform.start();
    }
}
