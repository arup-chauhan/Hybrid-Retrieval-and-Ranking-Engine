package com.hybrid.orchestration;

import com.hybrid.orchestration.service.OrchestrationService;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

public class OrchestrationServiceTest {
    @Test
    void testExecuteWorkflow() {
        RestTemplate restTemplate = mock(RestTemplate.class);
        when(restTemplate.postForObject(any(String.class), any(Object.class), eq(String.class))).thenReturn("ok");
        OrchestrationService service = new OrchestrationService(restTemplate);
        assertDoesNotThrow(service::executeWorkflow);
        verify(restTemplate, times(4)).postForObject(any(String.class), any(Object.class), eq(String.class));
    }
}
