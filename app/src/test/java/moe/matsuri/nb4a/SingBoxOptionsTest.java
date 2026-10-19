package moe.matsuri.nb4a;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class SingBoxOptionsTest {

    @Test
    public void serializesOptionAndAppliesConfigOverrides() {
        SingBoxOptions.Outbound outbound = new SingBoxOptions.Outbound();
        outbound.type = "direct";
        outbound.tag = "original";
        outbound._hack_config_map.put("tag", "overridden");
        outbound._hack_config_map.put("domain_strategy", "prefer_ipv4");

        Map<String, Object> result = outbound.asMap();

        assertEquals("direct", result.get("type"));
        assertEquals("overridden", result.get("tag"));
        assertEquals("prefer_ipv4", result.get("domain_strategy"));
        assertFalse(result.containsKey("_hack_config_map"));
    }

    @Test
    public void appliesOverridesToNestedOptions() {
        SingBoxOptions.Outbound outbound = new SingBoxOptions.Outbound();
        outbound.type = "direct";
        outbound.tag = "original";
        outbound._hack_config_map.put("tag", "nested-override");

        SingBoxOptions.MyOptions options = new SingBoxOptions.MyOptions();
        options.outbounds = Collections.singletonList(outbound);

        Map<String, Object> result = options.asMap();
        List<?> outbounds = (List<?>) result.get("outbounds");
        Map<?, ?> serializedOutbound = (Map<?, ?>) outbounds.get(0);

        assertEquals("nested-override", serializedOutbound.get("tag"));
    }

    @Test
    public void serializesCustomOptionAndAppliesOverrides() {
        SingBoxOptions.CustomSingBoxOption option =
                new SingBoxOptions.CustomSingBoxOption("{\"type\":\"direct\",\"tag\":\"custom\"}");
        option._hack_custom_config = "{\"tag\":\"merged\",\"domain_strategy\":\"ipv4_only\"}";

        Map<String, Object> result = option.asMap();

        assertEquals("direct", result.get("type"));
        assertEquals("merged", result.get("tag"));
        assertEquals("ipv4_only", result.get("domain_strategy"));
    }

    @Test
    public void serializesModernTunAddressWithoutLegacyFields() {
        SingBoxOptions.Inbound_TunOptions inbound = new SingBoxOptions.Inbound_TunOptions();
        inbound.type = "tun";
        inbound.tag = "tun-in";
        inbound.address = Arrays.asList("172.19.0.1/28", "fdfe:dcba:9876::1/126");

        Map<String, Object> result = inbound.asMap();

        assertEquals(inbound.address, result.get("address"));
        assertFalse(result.containsKey("inet4_address"));
        assertFalse(result.containsKey("inet6_address"));
        assertFalse(result.containsKey("endpoint_independent_nat"));
        assertFalse(result.containsKey("sniff"));
        assertFalse(result.containsKey("sniff_override_destination"));
        assertFalse(result.containsKey("domain_strategy"));
    }

    @Test
    public void serializesModernInboundRuleActions() {
        SingBoxOptions.Rule_DefaultOptions resolve = new SingBoxOptions.Rule_DefaultOptions();
        resolve.inbound = Arrays.asList("tun-in", "mixed-in");
        resolve.action = "resolve";
        resolve.strategy = "prefer_ipv4";

        Map<String, Object> result = resolve.asMap();

        assertEquals(resolve.inbound, result.get("inbound"));
        assertEquals("resolve", result.get("action"));
        assertEquals("prefer_ipv4", result.get("strategy"));
    }
    @Test
    public void convertsDnsAddressesToNativeTransports() {
        SingBoxOptions.DNSServerOptions udp = SingBoxOptions.DNSServerOptions.fromAddress(
                "1.1.1.1:5353", "direct", null, "local");
        assertEquals("udp", udp.type);
        assertEquals("1.1.1.1", udp.server);
        assertEquals(Integer.valueOf(5353), udp.server_port);
        assertEquals("local", udp.domain_resolver);
        assertFalse(udp.asMap().containsKey("address"));
        assertFalse(udp.asMap().containsKey("address_resolver"));
        assertFalse(udp.asMap().containsKey("detour"));
        SingBoxOptions.DNSServerOptions https = SingBoxOptions.DNSServerOptions.fromAddress(
                "https://dns.example:8443/custom-query?x=1", "remote", "proxy", "direct");
        assertEquals("https", https.type);
        assertEquals("dns.example", https.server);
        assertEquals(Integer.valueOf(8443), https.server_port);
        assertEquals("/custom-query?x=1", https.path);
        assertEquals("proxy", https.detour);
    }

    @Test
    public void convertsIpv6AndLocalDnsAddresses() {
        for (String address : Arrays.asList("2001:db8::1", "[2001:db8::1]", "udp://[2001:db8::1]")) {
            SingBoxOptions.DNSServerOptions options = SingBoxOptions.DNSServerOptions.fromAddress(
                    address, "test", "direct", null);
            assertEquals("udp", options.type);
            assertEquals("2001:db8::1", options.server);
        }
        SingBoxOptions.DNSServerOptions local = SingBoxOptions.DNSServerOptions.fromAddress(
                "local", "test", "direct", null);
        assertEquals("local", local.type);
        assertFalse(local.asMap().containsKey("server"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnsupportedDnsTransport() {
        SingBoxOptions.DNSServerOptions.fromAddress("invalid://dns.example", "test", null, null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidDnsPort() {
        SingBoxOptions.DNSServerOptions.fromAddress("tcp://dns.example:65536", "test", null, null);
    }

    @Test
    public void serializesNativeFakeDnsAndBlockAction() {
        SingBoxOptions.DNSServerOptions fake = new SingBoxOptions.DNSServerOptions();
        fake.type = "fakeip";
        fake.tag = "dns-fake";
        fake.inet4_range = "198.18.0.0/15";
        assertEquals("fakeip", fake.asMap().get("type"));
        assertFalse(fake.asMap().containsKey("address"));
        SingBoxOptions.DNSRule_DefaultOptions rule = new SingBoxOptions.DNSRule_DefaultOptions();
        rule.action = "predefined";
        rule.rcode = "NOERROR";
        assertEquals("NOERROR", rule.asMap().get("rcode"));
        assertFalse(rule.asMap().containsKey("server"));
    }

}
