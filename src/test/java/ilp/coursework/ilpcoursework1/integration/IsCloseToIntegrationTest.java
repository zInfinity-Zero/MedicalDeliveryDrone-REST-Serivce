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
public class IsCloseToIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testIsCloseToTrue() throws Exception {
        String json = """
        {
            "position1": {"lat": 1.0, "lng": 1.0},
            "position2": {"lat": 1.0, "lng": 1.0001}
        }
        """;

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("true"));
    }

    @Test
    void testIsCloseToFalse() throws Exception {
        String json = """
        {
            "position1": {"lat": 1.0, "lng": 1.0},
            "position2": {"lat": 1.01, "lng": 1.01}
        }
        """;

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(content().string("false"));
    }

    @Test
    void testIsCloseToBadRequest() throws Exception {
        String json = """
        {
            "position1": {"lat": 1.0, "lng": 1.0},
            "position2": null
        }
        """;

        mockMvc.perform(post("/api/v1/isCloseTo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }
}
