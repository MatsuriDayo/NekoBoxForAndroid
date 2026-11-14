package io.nekohasekai.sagernet.group;

import org.junit.Test;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.TypeDescription;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class RawUpdaterYamlSecurityTest {
    private Yaml safeYaml() {
        Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
        yaml.addTypeDescription(new TypeDescription(String.class, "str"));
        return yaml;
    }

    @Test public void acceptsOrdinaryClashSubscriptionYaml() {
        Map<?, ?> parsed = safeYaml().loadAs(
                "proxies:\n  - name: fixture\n    type: socks5\n    server: example.com\n    port: 1080\n",
                Map.class);
        List<?> proxies = (List<?>) parsed.get("proxies");
        assertEquals("fixture", ((Map<?, ?>) proxies.get(0)).get("name"));
    }

    @Test public void rejectsArbitraryJavaTypeTags() {
        try {
            safeYaml().loadAs("proxies:\n  - !!java.io.File [/tmp/nekobox-yaml-marker]\n", Map.class);
            fail("untrusted Java type tags must be rejected");
        } catch (YAMLException expected) {
            // SafeConstructor rejects global Java class tags.
        }
    }
}
