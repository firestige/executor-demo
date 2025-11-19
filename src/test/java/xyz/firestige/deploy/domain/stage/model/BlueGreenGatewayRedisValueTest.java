package xyz.firestige.deploy.domain.stage.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import xyz.firestige.deploy.domain.shared.vo.TenantId;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BlueGreenGatewayRedisValue 序列化测试
 */
class BlueGreenGatewayRedisValueTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldSerializeToJson() throws Exception {
        // Given
        RouteInfo route1 = new RouteInfo("route_001", "uri1", "uri2");
        RouteInfo route2 = new RouteInfo("route_002", "uri3", "uri4");

        BlueGreenGatewayRedisValue value = new BlueGreenGatewayRedisValue(
                TenantId.of("tenant_12345"),
                "unit_a",
                "unit_b",
                List.of(route1, route2)
        );

        // When
        String json = objectMapper.writeValueAsString(value);

        // Then
        assertNotNull(json);
        assertTrue(json.contains("\"tenantId\":\"tenant_12345\""));
        assertTrue(json.contains("\"sourceUnitName\":\"unit_a\""));
        assertTrue(json.contains("\"targetUnitName\":\"unit_b\""));
        assertTrue(json.contains("\"routes\""));
        assertTrue(json.contains("\"id\":\"route_001\""));
        assertTrue(json.contains("\"sourceUri\":\"uri1\""));
        assertTrue(json.contains("\"targetUri\":\"uri2\""));

        System.out.println("JSON output:");
        System.out.println(json);
    }

    @Test
    void shouldDeserializeFromJson() throws Exception {
        // Given
        String json = """
                {
                  "tenantId": "tenant_12345",
                  "sourceUnitName": "unit_a",
                  "targetUnitName": "unit_b",
                  "routes": [
                    {
                      "id": "route_001",
                      "sourceUri": "uri1",
                      "targetUri": "uri2"
                    },
                    {
                      "id": "route_002",
                      "sourceUri": "uri3",
                      "targetUri": "uri4"
                    }
                  ]
                }
                """;

        // When
        BlueGreenGatewayRedisValue value = objectMapper.readValue(json, BlueGreenGatewayRedisValue.class);

        // Then
        assertNotNull(value);
        assertEquals("tenant_12345", value.getTenantId());
        assertEquals("unit_a", value.getSourceUnitName());
        assertEquals("unit_b", value.getTargetUnitName());

        List<RouteInfo> routes = value.getRoutes();
        assertNotNull(routes);
        assertEquals(2, routes.size());

        RouteInfo route1 = routes.get(0);
        assertEquals("route_001", route1.getId());
        assertEquals("uri1", route1.getSourceUri());
        assertEquals("uri2", route1.getTargetUri());

        RouteInfo route2 = routes.get(1);
        assertEquals("route_002", route2.getId());
        assertEquals("uri3", route2.getSourceUri());
        assertEquals("uri4", route2.getTargetUri());
    }

    @Test
    void shouldRoundTrip() throws Exception {
        // Given
        RouteInfo route1 = new RouteInfo("route_001", "http://localhost:8080", "http://localhost:9090");

        BlueGreenGatewayRedisValue original = new BlueGreenGatewayRedisValue(
                TenantId.of("tenant_test"),
                "unit_old",
                "unit_new",
                List.of(route1)
        );

        // When - serialize and deserialize
        String json = objectMapper.writeValueAsString(original);
        BlueGreenGatewayRedisValue deserialized = objectMapper.readValue(json, BlueGreenGatewayRedisValue.class);

        // Then
        assertEquals(original.getTenantId(), deserialized.getTenantId());
        assertEquals(original.getSourceUnitName(), deserialized.getSourceUnitName());
        assertEquals(original.getTargetUnitName(), deserialized.getTargetUnitName());
        assertEquals(original.getRoutes().size(), deserialized.getRoutes().size());
        assertEquals(original.getRoutes().get(0).getId(), deserialized.getRoutes().get(0).getId());
    }
}

