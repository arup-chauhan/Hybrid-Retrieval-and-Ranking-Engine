package com.hybrid.vector;

import com.hybrid.vector.controller.VectorController;
import com.hybrid.vector.model.VectorResult;
import com.hybrid.vector.service.VectorSearchService;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(VectorController.class)
class VectorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private VectorSearchService vectorSearchService;

    private static List<VectorResult> mockResults;

    @BeforeAll
    static void initData() {
        mockResults = List.of(new VectorResult("doc-1", 0.9));
    }

    @BeforeEach
    void setup() {
        when(vectorSearchService.search("query")).thenReturn(mockResults);
    }

    @Test
    void testSearchEndpoint() throws Exception {
        mockMvc.perform(get("/api/vector/search").param("query", "query"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value("doc-1"))
                .andExpect(jsonPath("$[0].similarityScore").value(0.9));
    }
}
