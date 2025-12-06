package com.logicleaf.invplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logicleaf.invplatform.model.Integration;
import com.logicleaf.invplatform.model.IntegrationType;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.IntegrationRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class ZohoServiceTest {

    @Mock
    private StartupRepository startupRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private IntegrationRepository integrationRepository;

    @InjectMocks
    private ZohoService zohoService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        // Inject values for @Value fields
        ReflectionTestUtils.setField(zohoService, "zohoClientId", "testClientId");
        ReflectionTestUtils.setField(zohoService, "zohoClientSecret", "testClientSecret");
        ReflectionTestUtils.setField(zohoService, "zohoRedirectUri", "testRedirectUri");
    }

    @Test
    void fetchProfitAndLossReport_shouldIncludeDateParameters() throws Exception {
        // Arrange
        String founderEmail = "test@example.com";
        User user = new User();
        user.setId("1");
        user.setEmail(founderEmail);

        Startup startup = new Startup();
        startup.setId("100");
        startup.setFounderUserId("1");

        Integration integration = new Integration();
        integration.setStartupId("100");
        integration.setIntegrationType(IntegrationType.ZOHO);
        integration.setAccessToken("testAccessToken");
        integration.setExpiresAt(LocalDateTime.now().plusHours(1));
        integration.setConnectionConfig("{\"organization_id\": \"org123\"}");

        when(userRepository.findByEmail(founderEmail)).thenReturn(Optional.of(user));
        when(startupRepository.findByFounderUserId("1")).thenReturn(Optional.of(startup));
        when(integrationRepository.findByStartupIdAndIntegrationType("100", IntegrationType.ZOHO))
                .thenReturn(integration);

        String expectedJson = "{\"profitandloss\": {}}";
        JsonNode expectedNode = new ObjectMapper().readTree(expectedJson);

        when(restTemplate.exchange(any(String.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(new ResponseEntity<>(expectedJson, HttpStatus.OK));
        when(objectMapper.readTree(expectedJson)).thenReturn(expectedNode);

        // Mock ObjectMapper to return the orgId when parsing the config
        JsonNode configNode = new ObjectMapper().readTree(integration.getConnectionConfig());
        when(objectMapper.readTree(integration.getConnectionConfig())).thenReturn(configNode);

        // Act
        zohoService.fetchProfitAndLossReport(founderEmail);

        // Assert
        LocalDate endDate = LocalDate.now();
        LocalDate startDate = endDate.minusMonths(1);
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        String expectedUrlStart = "https://www.zohoapis.in/books/v3/reports/profitandloss?organization_id=org123";

        // Verify that the URL is a String and matches expectation
        verify(restTemplate).exchange(
            org.mockito.ArgumentMatchers.<String>argThat(url ->
                url.startsWith(expectedUrlStart) &&
                url.contains("date_start=" + startDate.format(formatter)) &&
                url.contains("date_end=" + endDate.format(formatter))
            ),
            eq(HttpMethod.GET),
            any(HttpEntity.class),
            eq(String.class)
        );
    }
}
