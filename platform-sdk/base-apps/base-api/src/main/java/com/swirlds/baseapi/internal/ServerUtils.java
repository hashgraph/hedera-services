/*
 * Copyright (C) 2024 Hedera Hashgraph, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.swirlds.baseapi.internal;

import com.swirlds.baseapi.config.BaseApiConfig;
import com.swirlds.baseapi.service.BalanceService;
import com.swirlds.baseapi.service.TransactionsService;
import com.swirlds.baseapi.service.WalletsService;
import com.swirlds.common.context.PlatformContext;
import io.undertow.Undertow;
import io.undertow.server.handlers.PathHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerUtils {
    private static final Logger log = LogManager.getLogger(ServerUtils.class);

    public static void createServer(BaseApiConfig config, PlatformContext swirldsContext) {
        // Create a path handler to associate handlers with different paths
        PathHandler pathHandler = new PathHandler();

        new AdapterHandler<>(swirldsContext, new BalanceService(), config.apiBasePath() + "/balances").into(
                pathHandler);
        new AdapterHandler<>(swirldsContext, new TransactionsService(swirldsContext),
                config.apiBasePath() + "/transactions").into(pathHandler);
        new AdapterHandler<>(swirldsContext, new WalletsService(swirldsContext),
                config.apiBasePath() + "/wallets").into(pathHandler);

        // Create the Undertow server with the path handler and bind it to port
        Undertow server = Undertow.builder()
                .addHttpListener(config.port(), "localhost")
                .setHandler(pathHandler)
                .build();

        // Start the server
        server.start();

        // Print a message indicating that the server is running
        log.info("\n~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~\n"
                + "~ _                                      _ ~\n"
                + "~| |__   __ _ ___  ___        __ _ _ __ (_)~\n"
                + "~| '_ \\ / _` / __|/ _ \\_____ / _` | '_ \\| |~\n"
                + "~| |_) | (_| \\__ \\  __/_____| (_| | |_) | |~\n"
                + "~|_.__/ \\__,_|___/\\___|      \\__,_| .__/|_|~\n"
                + "~                                 |_|      ~\n"
                + "~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~");
        log.info("Server started on port {}", config.port());
        log.debug("registered paths {}", pathHandler);
    }
}
