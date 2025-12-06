package com.logicleaf.invplatform.service;

import com.logicleaf.invplatform.exception.BadRequestException;
import com.logicleaf.invplatform.exception.ResourceNotFoundException;
import com.logicleaf.invplatform.model.Integration;
import com.logicleaf.invplatform.model.IntegrationStatus;
import com.logicleaf.invplatform.model.IntegrationType;
import com.logicleaf.invplatform.model.Startup;
import com.logicleaf.invplatform.model.User;
import com.logicleaf.invplatform.repository.IntegrationRepository;
import com.logicleaf.invplatform.repository.StartupRepository;
import com.logicleaf.invplatform.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class ZohoService {

    @Value("${ZOHO_CLIENT_ID}")
    private String zohoClientId;

    @Value("${ZOHO_CLIENT_SECRET}")
    private String zohoClientSecret;

    @Value("${ZOHO_REDIRECT_URI}")
    private String zohoRedirectUri;

    @Autowired
    private StartupRepository startupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationRepository integrationRepository;

    private static final Logger logger = LoggerFactory.getLogger(ZohoService.class);

    private static final String ZOHO_AUTH_URL = "https://accounts.zoho.in/oauth/v2/auth";
    private static final String ZOHO_TOKEN_URL = "https://accounts.zoho.in/oauth/v2/token";

    public String getZohoAuthUrl(String userEmail) {
        // We pass the user's email as state to identify them on callback
        return ZOHO_AUTH_URL + "?response_type=code&client_id=" + zohoClientId +
                "&scope=ZohoExpense.fullaccess.all,ZohoBooks.fullaccess.all&redirect_uri=" + zohoRedirectUri +
                "&access_type=offline&prompt=consent&state=" + userEmail;
    }

    public void handleZohoCallback(String code, String state) {
        String userEmail = state; // The user's email we passed in the auth URL

        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new RuntimeException("User not found for state: " + userEmail));

        Startup startup = startupRepository.findByFounderUserId(user.getId())
                .orElseThrow(() -> new RuntimeException("Startup profile not found for user: " + userEmail));

        RestTemplate restTemplate = new RestTemplate();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
        map.add("code", code);
        map.add("client_id", zohoClientId);
        map.add("client_secret", zohoClientSecret);
        map.add("redirect_uri", zohoRedirectUri);
        map.add("grant_type", "authorization_code");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(map, headers);

        JsonNode response = restTemplate.postForObject(ZOHO_TOKEN_URL, request, JsonNode.class);

        if (response == null || !response.has("access_token")) {
            throw new RuntimeException("Failed to obtain Zoho access token.");
        }

        String accessToken = response.get("access_token").asText();
        String refreshToken = response.has("refresh_token") ? response.get("refresh_token").asText() : null;
        long expiresIn = response.get("expires_in").asLong();

        // Fetch organization ID from Zoho Books
        String orgUrl = "https://books.zoho.in/api/v3/organizations";
        HttpHeaders orgHeaders = new HttpHeaders();
        orgHeaders.set("Authorization", "Zoho-oauthtoken " + accessToken);
        HttpEntity<Void> orgEntity = new HttpEntity<>(orgHeaders);

        ResponseEntity<JsonNode> orgResp = restTemplate.exchange(orgUrl, HttpMethod.GET, orgEntity, JsonNode.class);
        String organizationId = null;
        if (orgResp.getStatusCode().is2xxSuccessful() && orgResp.getBody() != null) {
            JsonNode organizations = orgResp.getBody().get("organizations");
            if (organizations != null && organizations.isArray() && organizations.size() > 0) {
                organizationId = organizations.get(0).path("organization_id").asText();
            }
        }

        Integration integration = integrationRepository.findByStartupIdAndIntegrationType(
                startup.getId(), IntegrationType.ZOHO);

        if (integration == null) {
            integration = Integration.builder()
                    .startupId(startup.getId())
                    .integrationType(IntegrationType.ZOHO)
                    .status(IntegrationStatus.CONNECTED)
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .expiresAt(LocalDateTime.now().plusSeconds(expiresIn))
                    .connectionConfig(organizationId != null
                            ? "{\"organization_id\": \"" + organizationId + "\"}"
                            : null)
                    .lastSyncTime(LocalDateTime.now())
                    .build();
        } else {
            integration.setStatus(IntegrationStatus.CONNECTED);
            integration.setAccessToken(accessToken);
            integration.setRefreshToken(refreshToken);
            integration.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            integration.setLastSyncTime(LocalDateTime.now());
            if (organizationId != null) {
                integration.setConnectionConfig("{\"organization_id\": \"" + organizationId + "\"}");
            }
        }
        integrationRepository.save(integration);

    }

    private String extractOrganizationId(Integration integration) {
        try {
            if (integration.getConnectionConfig() != null) {
                JsonNode config = objectMapper.readTree(integration.getConnectionConfig());
                return config.path("organization_id").asText(null);
            }
        } catch (Exception e) {
            logger.error("❌ Failed to parse organization_id from integration config: {}", e.getMessage());
        }
        return null;
    }

    private Integration getIntegrationOrThrow(String founderEmail) {
        User user = userRepository.findByEmail(founderEmail)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + founderEmail));

        Startup startup = startupRepository.findByFounderUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Startup not found for user: " + founderEmail));

        Integration integration = integrationRepository.findByStartupIdAndIntegrationType(
                startup.getId(), IntegrationType.ZOHO);

        if (integration == null || integration.getAccessToken() == null) {
            throw new BadRequestException("Zoho integration not connected for this startup.");
        }

        ensureValidToken(integration);
        return integration;
    }

    public JsonNode fetchSalesOrdersForFounder(String founderEmail, String startDate, String endDate) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/salesorders");
        urlBuilder.append("?organization_id=").append(organizationId);
        if (startDate != null && !startDate.isBlank())
            urlBuilder.append("&date_start=").append(startDate);
        if (endDate != null && !endDate.isBlank())
            urlBuilder.append("&date_end=").append(endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Sales orders fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching sales orders for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch sales orders: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchExpensesForFounder(String founderEmail, String startDate, String endDate) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/expenses");
        urlBuilder.append("?organization_id=").append(organizationId);
        if (startDate != null && !startDate.isBlank())
            urlBuilder.append("&date_start=").append(startDate);
        if (endDate != null && !endDate.isBlank())
            urlBuilder.append("&date_end=").append(endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Expenses fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching expenses for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch expenses: " + e.getMessage(), e);
        }
    }

    private void refreshAccessToken(Integration integration) {
        String refreshToken = integration.getRefreshToken();
        if (refreshToken == null || refreshToken.isEmpty()) {
            throw new RuntimeException("Refresh token missing. Please reauthorize your Zoho integration.");
        }

        String url = "https://accounts.zoho.in/oauth/v2/token";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("refresh_token", refreshToken);
        params.add("client_id", zohoClientId);
        params.add("client_secret", zohoClientSecret);
        params.add("grant_type", "refresh_token");

        HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(params, headers);

        try {
            ResponseEntity<JsonNode> resp = restTemplate.exchange(url, HttpMethod.POST, request, JsonNode.class);
            JsonNode body = resp.getBody();

            if (body == null || !body.has("access_token")) {
                throw new RuntimeException(
                        "Failed to refresh Zoho token: " + (body != null ? body.toString() : "null response"));
            }

            String newAccessToken = body.get("access_token").asText();
            long expiresIn = body.has("expires_in") ? body.get("expires_in").asLong() : 3600L;

            integration.setAccessToken(newAccessToken);
            integration.setExpiresAt(LocalDateTime.now().plusSeconds(expiresIn));
            integration.setLastSyncTime(LocalDateTime.now());

            integrationRepository.save(integration);

            logger.info(" Zoho access token refreshed successfully for startupId: {}", integration.getStartupId());
        } catch (Exception e) {
            logger.error(" Error refreshing Zoho token for startupId {}: {}", integration.getStartupId(),
                    e.getMessage());
            throw new RuntimeException("Error refreshing Zoho token: " + e.getMessage(), e);
        }
    }

    private void ensureValidToken(Integration integration) {
        if (integration.getAccessToken() == null) {
            throw new IllegalStateException("Missing Zoho access token. Please reconnect Zoho integration.");
        }

        if (integration.getExpiresAt() == null || integration.getExpiresAt().isBefore(LocalDateTime.now())) {
            logger.info(" Zoho token expired — refreshing for startupId {}", integration.getStartupId());
            refreshAccessToken(integration);
        }
    }

    public JsonNode fetchAllUsersFromZoho(String founderEmail) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new RuntimeException("Zoho organization ID not found in integration config.");
        }

        String url = String.format("https://www.zohoapis.in/books/v3/users?organization_id=%s", organizationId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            logger.info(" Users fetched successfully from Zoho for startupId: {}", integration.getStartupId());
            return objectMapper.readTree(resp.getBody());

        } catch (Exception e) {
            logger.error(" Error fetching Zoho users for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch users from Zoho: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchEmployeesFromZoho(String founderEmail, Integer page, Integer perPage) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new RuntimeException("Zoho organization ID not found in integration config.");
        }

        StringBuilder urlBuilder = new StringBuilder(
                String.format("https://www.zohoapis.in/books/v3/employees?organization_id=%s", organizationId));

        urlBuilder.append("&page=").append(page != null && page > 0 ? page : 1);
        urlBuilder.append("&per_page=").append(perPage != null && perPage > 0 ? perPage : 200);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);
            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            logger.info("✅ Employees fetched successfully from Zoho for startupId: {}", integration.getStartupId());
            return objectMapper.readTree(resp.getBody());
        } catch (Exception e) {
            logger.error("❌ Error fetching Zoho employees for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch employees from Zoho: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchAllBankAccountsForFounder(String founderEmail) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        String url = String.format(
                "https://www.zohoapis.in/books/v3/bankaccounts?organization_id=%s",
                organizationId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Zoho API returned " + response.getStatusCodeValue() + ": " + response.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            logger.info("✅ All bank accounts fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error("❌ Error fetching all bank accounts for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch all bank accounts: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchBankAccountDetailsForFounder(String founderEmail, String bankAccountId) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        // ✅ Zoho Bank Account API Endpoint
        String url = String.format(
                "https://www.zohoapis.in/books/v3/bankaccounts/%s?organization_id=%s",
                bankAccountId, organizationId);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");

        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> response = restTemplate.exchange(
                    url, HttpMethod.GET, entity, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException(
                        "Zoho API returned " + response.getStatusCodeValue() + ": " + response.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(response.getBody());
            logger.info("✅ Bank account details fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error("❌ Error fetching bank account details for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch bank account details: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchExpensesByCategory(String founderEmail, String category, String startDate, String endDate) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/expenses");
        urlBuilder.append("?organization_id=").append(organizationId);

        // Use 'category_name' as a best guess for the prompt's intent, instead of description.
        // Although the prompt said "category=Marketing", user feedback says to use category_name or just pass it as is.
        // I'll stick to passing 'category_name' because API v3 docs often refer to it.
        // If not, I can just append what the prompt said.
        // But the feedback explicitly said "revert to using category_name... instead of description_contains".
        if (category != null && !category.isBlank())
            urlBuilder.append("&category_name=").append(category);

        if (startDate != null && !startDate.isBlank())
            urlBuilder.append("&date_start=").append(startDate);
        if (endDate != null && !endDate.isBlank())
            urlBuilder.append("&date_end=").append(endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Expenses (category: {}) fetched successfully for startupId: {}", category, integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching expenses for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch expenses: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchContactsByType(String founderEmail, String type, String startDate, String endDate) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/contacts");
        urlBuilder.append("?organization_id=").append(organizationId);
        if (type != null && !type.isBlank())
            urlBuilder.append("&contact_type=").append(type);
        if (startDate != null && !startDate.isBlank())
            urlBuilder.append("&date_start=").append(startDate);
        if (endDate != null && !endDate.isBlank())
            urlBuilder.append("&date_end=").append(endDate);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Contacts fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching contacts for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch contacts: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchInvoicesByStatus(String founderEmail, String status) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/invoices");
        urlBuilder.append("?organization_id=").append(organizationId);
        if (status != null && !status.isBlank())
            urlBuilder.append("&status=").append(status);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity,
                    String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Invoices fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching invoices for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch invoices: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchProfitAndLossReport(String founderEmail, String fromDate, String toDate) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        StringBuilder urlBuilder = new StringBuilder("https://www.zohoapis.in/books/v3/reports/profitandloss");
        urlBuilder.append("?organization_id=").append(organizationId);

        if (fromDate == null || fromDate.isBlank()) {
            fromDate = java.time.LocalDate.now().minusMonths(1).toString();
        }
        if (toDate == null || toDate.isBlank()) {
            toDate = java.time.LocalDate.now().toString();
        }

        urlBuilder.append("&from_date=").append(fromDate);
        urlBuilder.append("&to_date=").append(toDate);

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(urlBuilder.toString(), HttpMethod.GET, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" P&L Report fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching P&L report for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch P&L report: " + e.getMessage(), e);
        }
    }

    public JsonNode fetchRecurringInvoices(String founderEmail) {
        Integration integration = getIntegrationOrThrow(founderEmail);
        String organizationId = extractOrganizationId(integration);
        if (organizationId == null) {
            throw new BadRequestException("Zoho organization ID not found in integration configuration.");
        }

        String url = "https://www.zohoapis.in/books/v3/recurringinvoices?organization_id=" + organizationId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Zoho-oauthtoken " + integration.getAccessToken());
        headers.set("Accept", "application/json");
        HttpEntity<Void> entity = new HttpEntity<>(headers);

        try {
            ResponseEntity<String> resp = restTemplate.exchange(url, HttpMethod.GET, entity, String.class);

            if (!resp.getStatusCode().is2xxSuccessful()) {
                throw new RuntimeException("Zoho API returned " + resp.getStatusCodeValue() + ": " + resp.getBody());
            }

            JsonNode responseJson = objectMapper.readTree(resp.getBody());
            logger.info(" Recurring Invoices fetched successfully for startupId: {}", integration.getStartupId());
            return responseJson;
        } catch (Exception e) {
            logger.error(" Error fetching recurring invoices for {}: {}", founderEmail, e.getMessage());
            throw new RuntimeException("Failed to fetch recurring invoices: " + e.getMessage(), e);
        }
    }

}