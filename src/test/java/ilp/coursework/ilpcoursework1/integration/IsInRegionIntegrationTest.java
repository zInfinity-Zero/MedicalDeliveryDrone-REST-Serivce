package ilp.coursework.ilpcoursework1.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class IsInRegionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testValidRegionPointInside() throws Exception {
        String json = """
        {
            "position": { "lat": 0.5, "lng": 0.5 },
            "region": {
                "vertices": [
                    {"lat": 0.0, "lng": 0.0},
                    {"lat": 0.0, "lng": 1.0},
                    {"lat": 1.0, "lng": 1.0},
                    {"lat": 1.0, "lng": 0.0}
                ]
            }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testPointOutsideRegion() throws Exception {
        String json = """
        {
            "position": { "lat": 2.0, "lng": 2.0 },
            "region": {
                "vertices": [
                    {"lat": 0.0, "lng": 0.0},
                    {"lat": 0.0, "lng": 1.0},
                    {"lat": 1.0, "lng": 1.0},
                    {"lat": 1.0, "lng": 0.0}
                ]
            }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void testInvalidRegionWithTwoVertices() throws Exception {
        String json = """
        {
            "position": { "lat": 0.5, "lng": 0.5 },
            "region": {
                "vertices": [
                    {"lat": 0.0, "lng": 0.0},
                    {"lat": 1.0, "lng": 1.0}
                ]
            }
        }
        """;

        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMissingFieldsReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/isInRegion")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
