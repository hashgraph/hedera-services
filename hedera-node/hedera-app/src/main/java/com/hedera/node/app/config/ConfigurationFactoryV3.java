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
import com.hedera.node.app.config.validation.BootstrapConfigDefaultsValidation;
import com.hedera.node.app.config.validation.BootstrapConfigForbiddenPropertiesValidation;
import com.hedera.node.app.config.validation.ParseableValueValidator;
import com.hedera.node.app.service.mono.context.properties.BootstrapProperties;
import com.swirlds.common.config.sources.PropertyFileConfigSource;
import com.swirlds.config.api.Configuration;
import com.swirlds.config.api.ConfigurationBuilder;
import edu.umd.cs.findbugs.annotations.NonNull;
import java.io.IOException;
import java.nio.file.Path;

public class ConfigurationFactoryV3 {

  @NonNull
  public Configuration createConfiguration() throws IOException {
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
        .withValidator(new BootstrapConfigDefaultsValidation())
        .withValidator(new BootstrapConfigForbiddenPropertiesValidation())
        .withValidator(new ParseableValueValidator())
        .withSource(new PropertyFileConfigSource(Path.of(
            BootstrapProperties.BOOTSTRAP_PROPS_RESOURCE)))
        .withSource(new PropertyFileConfigSource(Path.of(
            BootstrapProperties.BOOTSTRAP_OVERRIDE_PROPS_LOC)) {
          @Override
          public int getOrdinal() {
            //TODO: Add constructor in platform
            return super.getOrdinal() + 1;
          }
        }).withSource(new PropertyFileConfigSource(Path.of(
            "data/config/node.properties")) { //See ScreenedNodeFileProps
          @Override
          public int getOrdinal() {
            //TODO: Add constructor in platform
            return super.getOrdinal() + 2;
          }
        }).withSource(new PropertyFileConfigSource(Path.of(
            "data/config/application.properties")) { //See ScreenedNodeFileProps
          @Override
          public int getOrdinal() {
            //TODO: Add constructor in platform
            return super.getOrdinal() + 3;
          }
        })
        .build();
  }
}
