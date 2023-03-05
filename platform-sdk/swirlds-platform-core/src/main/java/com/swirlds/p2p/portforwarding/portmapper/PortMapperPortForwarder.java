/*
 * Copyright (C) 2016-2023 Hedera Hashgraph, LLC
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

package com.swirlds.p2p.portforwarding.portmapper;

import static com.swirlds.logging.LogMarker.PORT_FORWARDING;

import com.offbynull.portmapper.PortMapperFactory;
import com.offbynull.portmapper.gateway.Bus;
import com.offbynull.portmapper.gateway.Gateway;
import com.offbynull.portmapper.gateways.network.NetworkGateway;
import com.offbynull.portmapper.gateways.network.internalmessages.KillNetworkRequest;
import com.offbynull.portmapper.gateways.process.ProcessGateway;
import com.offbynull.portmapper.gateways.process.internalmessages.KillProcessRequest;
import com.offbynull.portmapper.mapper.MappedPort;
import com.offbynull.portmapper.mapper.PortMapper;
import com.offbynull.portmapper.mapper.PortType;
import com.swirlds.common.threading.framework.config.ThreadConfiguration;
import com.swirlds.common.threading.manager.ThreadManager;
import com.swirlds.p2p.portforwarding.PortForwarder;
import com.swirlds.p2p.portforwarding.PortMapping;
import com.swirlds.p2p.portforwarding.PortMappingListener;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class PortMapperPortForwarder implements PortForwarder, Runnable {
    private static final int mappingDuration = 60;
    private static final Logger logger = LogManager.getLogger(PortMapperPortForwarder.class);

    private Bus networkBus;
    private Bus processBus;
    private PortMapper mapper;
    private final Queue<PortPair> ports = new ConcurrentLinkedQueue<>();
    private volatile String externalIp = null;
    private boolean successful = false;
    private Thread refresher;
    private final List<PortMappingListener> listeners = new LinkedList<>();

    /**
     * Responsible for creating and managing threads used by this object.
     */
    private final ThreadManager threadManager;

    /**
     * @param threadManager
     * 		responsible for creating and managing this object's threads
     */
    public PortMapperPortForwarder(final ThreadManager threadManager) {
        this.threadManager = threadManager;
    }

    public void addListener(PortMappingListener listener) {
        listeners.add(listener);
    }

    public void addPortMapping(String ip, int internalPort, int externalPort, Protocol protocol, String name) {
        addPortMapping(new PortMapping(ip, internalPort, externalPort, protocol));
    }

    public void addPortMapping(PortMapping portMapping) {
        ports.add(new PortPair(portMapping));
    }

    public void setPortMappings(List<PortMapping> portsToBeMapped) {
        ports.clear();
        for (PortMapping portMapping : portsToBeMapped) {
            addPortMapping(portMapping);
        }
    }

    public void execute() {
        try {
            // Start gateways
            Gateway network = NetworkGateway.create();
            Gateway process = ProcessGateway.create();
            networkBus = network.getBus();
            processBus = process.getBus();

            // Discover port forwarding devices and take the first one found
            List<PortMapper> mappers = PortMapperFactory.discover(networkBus, processBus);
            if (mappers.size() == 0) {
                for (PortMappingListener listener : listeners) {
                    listener.noForwardingDeviceFound();
                }
                return;
            }

            mapper = mappers.get(0);

            // Map some internal port to some preferred external port
            //
            // IMPORTANT NOTE: Many devices prevent you from mapping ports that are <= 1024
            // (both internal and external ports). Be mindful of this when choosing which
            // ports you want to map.
            for (PortPair pair : ports) {
                MappedPort mappedPort;
                PortMapping mapping = pair.getSpecified();
                try {
                    mappedPort = mapper.mapPort(
                            PortType.valueOf(mapping.getProtocol().toString()),
                            mapping.getInternalPort(),
                            mapping.getExternalPort(),
                            mappingDuration);
                    pair.setActual(mappedPort);
                    this.setExternalIp(mappedPort.getExternalAddress().getHostAddress());
                    successful = true;
                    for (PortMappingListener listener : listeners) {
                        listener.mappingAdded(mapping);
                    }
                } catch (NullPointerException | IllegalStateException | IllegalArgumentException e) {
                    for (PortMappingListener listener : listeners) {
                        listener.mappingFailed(mapping, e);
                    }
                }
            }

            if (!successful) {
                // no ports were mapped
                return;
            }

            // start the refresher thread that will refresh the port mappings until stopped
            long minSleep = -1;
            for (PortPair pair : ports) {
                MappedPort mappedPort = pair.getActual();
                if (mappedPort == null) {
                    continue;
                }
                // the port mapping is valid for getLifetime() seconds, so we will refresh
                // every getLifetime()/2 seconds, to make sure we refresh often enough.
                // Multiply by 1000 to get milliseconds, which is used by Thread.sleep().
                long sleep = mappedPort.getLifetime() * 1000 / 2;
                if (minSleep == -1 || sleep < minSleep) {
                    minSleep = sleep;
                }
            }
            if (minSleep > 0) {
                refresher = new ThreadConfiguration(threadManager)
                        .setComponent("network")
                        .setThreadName("MappingRefresher")
                        .setRunnable(new MappingRefresher(this, minSleep))
                        .build(true /*start*/);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            closeService();
        } catch (Exception e) {
            logger.error(PORT_FORWARDING.getMarker(), "An exception occurred while trying to do port forwarding:", e);
        }
    }

    public void refreshMappings() {
        Iterator<PortPair> i = ports.iterator();
        while (i.hasNext()) {
            PortPair pair = i.next();
            MappedPort mappedPort = pair.getActual();
            if (mappedPort == null) {
                i.remove();
                continue;
            }
            try {
                mappedPort = mapper.refreshPort(mappedPort, mappedPort.getLifetime());
                this.setExternalIp(mappedPort.getExternalAddress().getHostAddress());

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                closeService();
            } catch (NullPointerException | IllegalArgumentException | IllegalStateException e) {
                logger.error(PORT_FORWARDING.getMarker(), "An exception occurred while refreshing a mapped port:", e);
                i.remove();
                for (PortMappingListener listener : listeners) {
                    listener.mappingFailed(pair.getSpecified(), e);
                }
            }
        }
    }

    public String getExternalIPAddress() {
        return externalIp;
    }

    private void setExternalIp(String externalIp) {
        if (this.externalIp == null || !this.externalIp.equals(externalIp)) {
            this.externalIp = externalIp;
            for (PortMappingListener listener : listeners) {
                listener.foundExternalIp(externalIp);
            }
        }
    }

    public boolean isSuccessful() {
        return successful;
    }

    public void closeService() {
        if (refresher != null) {
            refresher.interrupt();
        }

        // Unmap port
        if (mapper != null) {
            for (PortPair pair : ports) {
                MappedPort mappedPort = pair.getActual();
                if (mappedPort == null) {
                    continue;
                }
                try {
                    mapper.unmapPort(mappedPort);
                } catch (Exception e) {
                    logger.error(PORT_FORWARDING.getMarker(), "An exception occurred while unmapping a port:", e);
                }
            }
        }

        // Stop gateways
        if (networkBus != null) {
            networkBus.send(new KillNetworkRequest());
        }
        if (processBus != null) {
            processBus.send(new KillProcessRequest());
        }
    }

    @Override
    public void run() {
        execute();
        if (!isSuccessful()) {
            // port forwarding doesn't work, shutdown the service
            closeService();
        }
    }
}
