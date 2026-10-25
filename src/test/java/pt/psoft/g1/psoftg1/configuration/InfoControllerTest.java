package pt.psoft.g1.psoftg1.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.info.BuildProperties;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class InfoControllerTest {

    @Test
    @DisplayName("Should return default values when BuildProperties is null")
    void testInfoWithNullBuildProperties() {
        InfoController controller = new InfoController(null, "dev");
        Map<String, String> info = controller.info();

        assertNotNull(info);
        assertEquals("psoft-g1", info.get("application"));
        assertEquals("0.0.1-SNAPSHOT", info.get("version"));
        assertEquals("local", info.get("commit"));
        assertEquals("dev", info.get("environment"));
    }

    @Test
    @DisplayName("Should return configured build info when BuildProperties is present")
    void testInfoWithCustomBuildProperties() {
        Properties props = new Properties();
        props.put("artifact", "psoft-g1");
        props.put("version", "1.2.3");
        props.put("commit", "abc1234567890abcdef1234567890abcdef123456");
        BuildProperties buildProperties = new BuildProperties(props);

        InfoController controller = new InfoController(buildProperties, "prod");
        Map<String, String> info = controller.info();

        assertNotNull(info);
        assertEquals("psoft-g1", info.get("application"));
        assertEquals("1.2.3", info.get("version"));
        assertEquals("abc1234567890abcdef1234567890abcdef123456", info.get("commit"));
        assertEquals("prod", info.get("environment"));
    }
}

