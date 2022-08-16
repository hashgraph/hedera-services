package com.hedera.services.context.properties;

public class PropertyNames {
    private PropertyNames() {
        /* No-Op */
    }

    public static final String BOOTSTRAP_FEE_SCHEDULE_JSON_RESOURCE =
            "bootstrap.feeSchedulesJson.resource";
    public static final String BOOTSTRAP_GENESIS_PUBLIC_KEY = "bootstrap.genesisPublicKey";
    public static final String BOOTSTRAP_HAPI_PERMISSIONS_PATH = "bootstrap.hapiPermissions.path";
    public static final String BOOTSTRAP_NETWORK_PROPERTIES_PATH =
            "bootstrap.networkProperties.path";
    public static final String BOOTSTRAP_RATES_CURRENT_HBAR_EQUIV =
            "bootstrap.rates.currentHbarEquiv";
    public static final String BOOTSTRAP_RATES_CURRENT_CENT_EQUIV =
            "bootstrap.rates.currentCentEquiv";
    public static final String BOOTSTRAP_RATES_CURRENT_EXPIRY = "bootstrap.rates.currentExpiry";
    public static final String BOOTSTRAP_RATES_NEXT_HBAR_EQUIV = "bootstrap.rates.nextHbarEquiv";
    public static final String BOOTSTRAP_RATES_NEXT_CENT_EQUIV = "bootstrap.rates.nextCentEquiv";
    public static final String BOOTSTRAP_RATES_NEXT_EXPIRY = "bootstrap.rates.nextExpiry";
    public static final String BOOTSTRAP_SYSTEM_ENTITY_EXPIRY = "bootstrap.system.entityExpiry";
    public static final String BOOTSTRAP_THROTTLE_DEF_JSON_RESOURCE =
            "bootstrap.throttleDefsJson.resource";
}
