package pt.psoft.g1.psoftg1.configuration;

import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class InfoController {
    private final Optional<BuildProperties> build;
    private final String environment;

    public InfoController(@Autowired(required = false) BuildProperties build,
                          @Value("${demo.environment:${DEMO_ENVIRONMENT:local}}") String environment) {
        this.build = Optional.ofNullable(build);
        this.environment = environment;
    }

    @GetMapping("/info")
    public Map<String, String> info() {
        return Map.of(
            "application", build.map(BuildProperties::getArtifact).orElse("psoft-g1"),
            "version", build.map(BuildProperties::getVersion).orElse("0.0.1-SNAPSHOT"),
            "commit", build.map(b -> b.get("commit")).orElse("local"),
            "environment", environment
        );
    }
}

