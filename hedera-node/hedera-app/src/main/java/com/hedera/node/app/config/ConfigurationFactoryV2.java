package com.hedera.node.app.config;

import com.hedera.node.app.config.converter.AccountIDConverter;
import com.hedera.node.app.config.converter.CongestionMultipliersConverter;
import com.hedera.node.app.config.converter.EntityScaleFactorsConverter;
import com.hedera.node.app.config.converter.EntityTypeConverter;
import com.hedera.node.app.config.converter.HederaFunctionalityConverter;
import com.hedera.node.app.config.converter.KnownBlockValuesConverter;
import com.hedera.node.app.config.converter.LegacyContractIdActivationsConverter;
import com.hedera.node.app.config.converter.MapAccessTypeConverter;
import com.hedera.node.app.config.converter.ProfileConverter;
import com.hedera.node.app.config.converter.ScaleFactorConverter;
import com.hedera.node.app.config.converter.SidecarTypeConverter;
import com.hedera.node.app.config.converter.StakeStartupHelperRecomputeTypeConverter;
import com.hedera.node.app.config.source.PropertySourceBasedConfigSource;
import com.hedera.node.app.service.mono.context.properties.PropertySource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;

public class ConfigurationFactoryV2 {

  public Configuration create(@NonNull final PropertySource propertySource) {
    return ConfigurationBuilder.create()
        .withConverter(new AccountIDConverter())
        .withConverter(new CongestionMultipliersConverter())
        .withConverter(new EntityScaleFactorsConverter())
        .withConverter(new EntityTypeConverter())
        .withConverter(new HederaFunctionalityConverter())
        .withConverter(new KnownBlockValuesConverter())
        .withConverter(new LegacyContractIdActivationsConverter())
        .withConverter(new MapAccessTypeConverter())
        .withConverter(new ProfileConverter())
        .withConverter(new ScaleFactorConverter())
        .withConverter(new SidecarTypeConverter())
        .withConverter(new StakeStartupHelperRecomputeTypeConverter())
        .withSource(new PropertySourceBasedConfigSource(propertySource))
        .build();
  }
}
