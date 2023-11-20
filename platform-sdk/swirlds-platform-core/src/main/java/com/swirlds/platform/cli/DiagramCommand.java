package com.swirlds.platform.cli;

import com.swirlds.base.time.Time;
import com.swirlds.cli.PlatformCli;
import com.swirlds.cli.utility.AbstractCommand;
import com.swirlds.cli.utility.SubcommandOf;
import com.swirlds.common.context.DefaultPlatformContext;
import com.swirlds.common.context.PlatformContext;
import com.swirlds.common.crypto.CryptographyHolder;
import com.swirlds.common.metrics.noop.NoOpMetrics;
import com.swirlds.config.api.Configuration;
import com.swirlds.platform.config.DefaultConfiguration;
import com.swirlds.platform.wiring.PlatformWiring;
import java.io.IOException;
import java.util.Base64;
import java.util.Set;
import picocli.CommandLine;

@CommandLine.Command(
        name = "diagram",
        mixinStandardHelpOptions = true,
        description = "Generate a mermaid style diagram of platform wiring.")
@SubcommandOf(PlatformCli.class)
public final class DiagramCommand extends AbstractCommand {

    private DiagramCommand() {}

    /**
     * Entry point.
     */
    @Override
    public Integer call() throws IOException {
        final Configuration configuration = DefaultConfiguration.buildBasicConfiguration();
        final PlatformContext platformContext =
                new DefaultPlatformContext(configuration, new NoOpMetrics(), CryptographyHolder.get());

        final PlatformWiring platformWiring = new PlatformWiring(platformContext, Time.getCurrent());

        final String diagramString = platformWiring.getModel().generateWiringDiagram(Set.of());
        final String encodedDiagramString = Base64.getEncoder().encodeToString(diagramString.getBytes());

        final String editorUrl = "https://mermaid.ink/svg/" + encodedDiagramString + "?bgColor=e8e8e8";

        System.out.println(diagramString);
        System.out.println();
        System.out.println(editorUrl);
        return 0;
    }

}
