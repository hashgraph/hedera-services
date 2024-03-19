package com.hedera.node.blocknode.core.services;

import java.util.*;

public class BlockNodeServicesRegistryImpl {
    private Map<String, Object> serviceMap = new HashMap<>();

    public void registerService(String serviceName, Object serviceInstance) {
        serviceMap.put(serviceName, serviceInstance);
    }

    public Object getService(String serviceName) {
        return serviceMap.get(serviceName);
    }
}
