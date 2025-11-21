package ilp.coursework.ilpcoursework1.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@SpringBootTest
@AutoConfigureMockMvc
public class DistanceToIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testDistanceToEndpoint() throws Exception {
        String json = """
        {
            "position1": {"lat": 1.0, "lng": 1.0},
            "position2": {"lat": 1.0, "lng": 1.0001}
        }
        """;

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isNumber());
    }

    @Test
    void testDistanceToBadRequest() throws Exception {
        String json = """
        {
            "position1": {"lat": 1.0, "lng": 1.0},
            "position2": null
        }
        """;

        mockMvc.perform(post("/api/v1/distanceTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
