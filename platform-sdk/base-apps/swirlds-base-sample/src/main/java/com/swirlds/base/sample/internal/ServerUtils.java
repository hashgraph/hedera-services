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

package com.swirlds.base.sample.internal;

import com.swirlds.base.sample.config.BaseApiConfig;
import com.swirlds.base.sample.service.InventoryService;
import com.swirlds.base.sample.service.ItemService;
import com.swirlds.base.sample.service.OperationService;
import com.swirlds.base.sample.service.PurchaseService;
import com.swirlds.base.sample.service.SaleService;
import com.swirlds.common.context.PlatformContext;
import edu.umd.cs.findbugs.annotations.NonNull;
import io.undertow.Undertow;
import io.undertow.server.handlers.BlockingHandler;
import io.undertow.server.handlers.PathHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Http Server manager utils
 */
public class ServerUtils {
    private static final Logger log = LogManager.getLogger(ServerUtils.class);

    /**
     * Creates and starts a Http server with a set of defined handlers
     */
    public static void createServer(
            final @NonNull BaseApiConfig config, final @NonNull PlatformContext swirldsContext) {
        // Create a path handler to associate handlers with different paths
        PathHandler pathHandler = new PathHandler();

        new AdapterHandler<>(swirldsContext, new InventoryService(), config.apiBasePath() + "/inventories")
                .into(pathHandler);
        new AdapterHandler<>(swirldsContext, new OperationService(swirldsContext), config.apiBasePath() + "/operations")
                .into(pathHandler);
        new AdapterHandler<>(swirldsContext, new ItemService(), config.apiBasePath() + "/items").into(pathHandler);
        new AdapterHandler<>(swirldsContext, new SaleService(swirldsContext), config.apiBasePath() + "/sales")
                .into(pathHandler);
        new AdapterHandler<>(swirldsContext, new PurchaseService(swirldsContext), config.apiBasePath() + "/purchases")
                .into(pathHandler);
        // Create the Undertow server with the path handler and bind it to port
        Undertow server = Undertow.builder()
                .addHttpListener(config.port(), config.host())
                // Blocking adapter
                .setHandler(new BlockingHandler(pathHandler))
                .build();

        // Start the server
        server.start();

        if (config.banner()) {
            log.info("\n              _      _     _           _                    \n"
                    + " _____      _(_)_ __| | __| |___      | |__   __ _ ___  ___ \n"
                    + "/ __\\ \\ /\\ / / | '__| |/ _` / __|_____| '_ \\ / _` / __|/ _ \\\n"
                    + "\\__ \\\\ V  V /| | |  | | (_| \\__ \\_____| |_) | (_| \\__ \\  __/\n"
                    + "|___/ \\_/\\_/ |_|_|  |_|\\__,_|___/ _   |_.__/ \\__,_|___/\\___|\n"
                    + " ___  __ _ _ __ ___  _ __ | | ___| |                        \n"
                    + "/ __|/ _` | '_ ` _ \\| '_ \\| |/ _ \\ |                        \n"
                    + "\\__ \\ (_| | | | | | | |_) | |  __/_|                        \n"
                    + "|___/\\__,_|_| |_| |_| .__/|_|\\___(_) ");
        }
        log.info("Server started on {}:{}", config.host(), config.port());
        log.debug("All registered paths {}", pathHandler);
    }
}
