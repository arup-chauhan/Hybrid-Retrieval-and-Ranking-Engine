package com.hybrid.vector;

import com.hybrid.vector.controller.VectorController;
import com.hybrid.vector.service.VectorSearchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class VectorControllerTest {

    @Test
    void testSearchEndpoint() throws Exception {
        VectorController controller = new VectorController(new VectorSearchService());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(controller).build();

        mockMvc.perform(get("/api/vector/search").param("query", "query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").exists())
                .andExpect(jsonPath("$[0].similarityScore").exists());
    }
}
