package dev.guardflow.server.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.guardflow.server.runtime.ExperimentRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "guardflow.ticker.enabled=false")
@AutoConfigureMockMvc
class ApiSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ExperimentRegistry registry;

    @Test
    void algorithmsEndpointReturnsFourAlgorithms() throws Exception {
        mockMvc.perform(get("/api/algorithms"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value("token_bucket"))
                .andExpect(jsonPath("$[1].id").value("fixed_window"))
                .andExpect(jsonPath("$[2].id").value("sliding_window"))
                .andExpect(jsonPath("$[3].id").value("leaky_bucket"));
    }

    @Test
    void invalidParamsReturn400() throws Exception {
        mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "algorithm": "token_bucket",
                                  "params": { "capacity": 0, "ratePerSecond": 2 },
                                  "input": { "mode": "constant", "qps": 20 }
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_PARAM"));
    }

    @Test
    void createStartTickAndReadSnapshot() throws Exception {
        String createBody = """
                {
                  "algorithm": "token_bucket",
                  "params": { "capacity": 10, "ratePerSecond": 2 },
                  "input": { "mode": "constant", "qps": 20 },
                  "speed": 10
                }
                """;
        MvcResult created = mockMvc.perform(post("/api/experiments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("idle"))
                .andReturn();
        String id = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(post("/api/experiments/{id}/control", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"start\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"));

        for (int i = 0; i < 20; i++) {
            assertThat(registry.get(id).tick()).isPresent();
        }

        MvcResult snapshot = mockMvc.perform(get("/api/experiments/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("running"))
                .andExpect(jsonPath("$.totals.offered").value(40))
                .andExpect(jsonPath("$.samples.length()").value(20))
                .andReturn();

        JsonNode root = objectMapper.readTree(snapshot.getResponse().getContentAsString());
        long offered = root.path("totals").path("offered").asLong();
        long passed = root.path("totals").path("passed").asLong();
        long rejected = root.path("totals").path("rejected").asLong();
        assertThat(offered).isEqualTo(40);
        assertThat(passed + rejected).isEqualTo(offered);
        assertThat(root.path("level").asDouble()).isBetween(0.0, 1.0);
    }
}
