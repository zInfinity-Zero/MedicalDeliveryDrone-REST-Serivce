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
public class NextPositionIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testValidNextPosition() throws Exception {
        String json = """
        {
            "start": { "lat": 55.946233, "lng": -3.192473 },
            "angle": 90
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lat").isNumber())
                .andExpect(jsonPath("$.lng").isNumber());
    }

    @Test
    void testInvalidAngleReturnsBadRequest() throws Exception {
        String json = """
        {
            "start": { "lat": 55.946233, "lng": -3.192473 },
            "angle": 10
        }
        """;

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testMissingBodyReturnsBadRequest() throws Exception {
        String json = "{}";

        mockMvc.perform(post("/api/v1/nextPosition")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
