package com.logicleaf.invplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logicleaf.invplatform.model.Integration;
import com.logicleaf.invplatform.model.IntegrationStatus;
import com.logicleaf.invplatform.model.IntegrationType;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.IntegrationRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class ZohoServiceTest {

    @Mock
    private StartupRepository startupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private IntegrationRepository integrationRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private ZohoService zohoService;

    private User mockUser;
    private Startup mockStartup;
    private Integration mockIntegration;
    private final String founderEmail = "founder@example.com";
    private final String orgId = "123456789";

    @BeforeEach
    void setUp() {
        mockUser = new User();
        mockUser.setId("user123");
        mockUser.setEmail(founderEmail);

        mockStartup = new Startup();
        mockStartup.setId("startup123");
        mockStartup.setFounderUserId("user123");

        mockIntegration = Integration.builder()
                .startupId("startup123")
                .integrationType(IntegrationType.ZOHO)
                .status(IntegrationStatus.CONNECTED)
                .accessToken("access_token_123")
                .refreshToken("refresh_token_123")
                .expiresAt(LocalDateTime.now().plusHours(1))
                .connectionConfig("{\"organization_id\": \"" + orgId + "\"}")
                .build();

    }

    @Test
    void fetchProfitAndLossReport_ShouldUseDefaultDates_WhenDatesAreNull() throws Exception {
        // Arrange
        when(userRepository.findByEmail(founderEmail)).thenReturn(Optional.of(mockUser));
        when(startupRepository.findByFounderUserId(mockUser.getId())).thenReturn(Optional.of(mockStartup));
        when(integrationRepository.findByStartupIdAndIntegrationType(mockStartup.getId(), IntegrationType.ZOHO))
                .thenReturn(mockIntegration);

        // Mock extractOrganizationId internal logic which uses objectMapper
        JsonNode mockConfigNode = mock(JsonNode.class);
        JsonNode mockOrgIdNode = mock(JsonNode.class);
        when(objectMapper.readTree(mockIntegration.getConnectionConfig())).thenReturn(mockConfigNode);
        when(mockConfigNode.path("organization_id")).thenReturn(mockOrgIdNode);
        when(mockOrgIdNode.asText(null)).thenReturn(orgId);

        // Mock API response
        String responseBody = "{\"profitandloss\": {}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        JsonNode mockResponseJson = mock(JsonNode.class);
        when(objectMapper.readTree(responseBody)).thenReturn(mockResponseJson);

        // Act
        zohoService.fetchProfitAndLossReport(founderEmail, null, null);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        String capturedUrl = urlCaptor.getValue();

        // Assert it DOES contain from_date and to_date
        assertTrue(capturedUrl.contains("https://www.zohoapis.in/books/v3/reports/profitandloss"));
        assertTrue(capturedUrl.contains("organization_id=" + orgId));
        assertTrue(capturedUrl.contains("from_date=" + java.time.LocalDate.now().minusMonths(1).toString()));
        assertTrue(capturedUrl.contains("to_date=" + java.time.LocalDate.now().toString()));
    }

    @Test
    void fetchProfitAndLossReport_ShouldUseProvidedDates() throws Exception {
        // Arrange
        when(userRepository.findByEmail(founderEmail)).thenReturn(Optional.of(mockUser));
        when(startupRepository.findByFounderUserId(mockUser.getId())).thenReturn(Optional.of(mockStartup));
        when(integrationRepository.findByStartupIdAndIntegrationType(mockStartup.getId(), IntegrationType.ZOHO))
                .thenReturn(mockIntegration);

        JsonNode mockConfigNode = mock(JsonNode.class);
        JsonNode mockOrgIdNode = mock(JsonNode.class);
        when(objectMapper.readTree(mockIntegration.getConnectionConfig())).thenReturn(mockConfigNode);
        when(mockConfigNode.path("organization_id")).thenReturn(mockOrgIdNode);
        when(mockOrgIdNode.asText(null)).thenReturn(orgId);

        String responseBody = "{\"profitandloss\": {}}";
        ResponseEntity<String> responseEntity = new ResponseEntity<>(responseBody, HttpStatus.OK);
        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(responseEntity);

        JsonNode mockResponseJson = mock(JsonNode.class);
        when(objectMapper.readTree(responseBody)).thenReturn(mockResponseJson);

        String fromDate = "2023-01-01";
        String toDate = "2023-01-31";

        // Act
        zohoService.fetchProfitAndLossReport(founderEmail, fromDate, toDate);

        // Assert
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).exchange(urlCaptor.capture(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class));

        String capturedUrl = urlCaptor.getValue();
        assertTrue(capturedUrl.contains("from_date=" + fromDate));
        assertTrue(capturedUrl.contains("to_date=" + toDate));
    }
}
