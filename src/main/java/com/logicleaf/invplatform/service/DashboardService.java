package com.logicleaf.invplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.logicleaf.invplatform.dto.FounderDashboardResponse;
import com.logicleaf.invplatform.dto.MonthlyMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
public class DashboardService {

    @Autowired
    private ZohoService zohoService;

    public FounderDashboardResponse getFounderDashboardData(String founderEmail) {
        LocalDate now = LocalDate.now();
        LocalDate startDate = now.minusMonths(5).withDayOfMonth(1);
        LocalDate endDate = now.withDayOfMonth(now.lengthOfMonth());

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        String startDateStr = startDate.format(dateFormatter);
        String endDateStr = endDate.format(dateFormatter);

        LocalDate previousMonth = now.minusMonths(1);

        LocalDate previousMonthStart = previousMonth.withDayOfMonth(1);
        LocalDate previousMonthEnd = previousMonth.withDayOfMonth(previousMonth.lengthOfMonth());

        String previousMonthStartStr = previousMonthStart.format(dateFormatter);
        String previousMonthEndStr = previousMonthEnd.format(dateFormatter);

        // Fetch Zoho data in one request each
        JsonNode salesOrders = zohoService.fetchSalesOrdersForFounder(founderEmail, startDateStr, endDateStr);
        JsonNode expenses = zohoService.fetchExpensesForFounder(founderEmail, startDateStr, endDateStr);
        JsonNode bankAccounts = zohoService.fetchAllBankAccountsForFounder(founderEmail);
        JsonNode contacts = zohoService.fetchContactsFromZoho(founderEmail, new HashMap<>());
        JsonNode invoices = zohoService.fetchInvoicesFromZoho(founderEmail, new HashMap<>());
        JsonNode pnl = zohoService.fetchProfitAndLossReport(founderEmail, startDateStr, endDateStr);
        JsonNode recurringInvoices = zohoService.fetchRecurringInvoicesFromZoho(founderEmail, new HashMap<>());

        // Group by month (like "Jun", "Sep")
        Map<String, Double> monthlyRevenueMap = groupByMonthShortName(salesOrders, "salesorders", "total");
        Map<String, Double> monthlyExpenseMap = groupByMonthShortName(expenses, "expenses", "total");

        List<LocalDate> last6Months = IntStream.rangeClosed(0, 5)
                .mapToObj(i -> now.minusMonths(5 - i).withDayOfMonth(1))
                .collect(Collectors.toList());

        List<MonthlyMetric> revenueData = new ArrayList<>();
        List<MonthlyMetric> expenseData = new ArrayList<>();
        List<MonthlyMetric> burnRateData = new ArrayList<>();

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

        for (LocalDate monthStart : last6Months) {
            String monthKey = monthStart.format(monthFormatter);
            double monthlyRevenue = monthlyRevenueMap.getOrDefault(monthKey, 0.0);
            double monthlyExpense = monthlyExpenseMap.getOrDefault(monthKey, 0.0);

            double netProfit = monthlyRevenue - monthlyExpense;
            double burnRate = netProfit < 0 ? Math.abs(netProfit) : 0.0;

            revenueData.add(new MonthlyMetric(monthKey, monthlyRevenue));
            expenseData.add(new MonthlyMetric(monthKey, monthlyExpense));
            burnRateData.add(new MonthlyMetric(monthKey, burnRate));
        }

        double totalBankBalance = getBankBalance(bankAccounts);

        double latestMonthExpense = expenseData.isEmpty()
                ? 0.0
                : expenseData.get(expenseData.size() - 1).getValue();

        int cashRunwayMonths = latestMonthExpense > 0
                ? (int) Math.floor(totalBankBalance / latestMonthExpense)
                : 0;

        int teamSize = getZohoUserCount(founderEmail);

        double CAC = calculateCAC(expenses, contacts, previousMonthStartStr, previousMonthEndStr);

        // LTV
        double averageLifeSpam = calculateAverageCustomerLifespan(contacts);
        double LTV = calculateLTV(invoices, pnl, contacts, averageLifeSpam);

        // churn
        double churn = getChurnRateFromContacts(contacts, previousMonthStartStr, previousMonthEndStr);

        Map<String, Double> kpi = new LinkedHashMap<>();
        kpi.put("NPS", 30.0);
        kpi.put("Churn", churn);
        kpi.put("LTV", LTV);
        kpi.put("CAC", CAC);

        Map<String, Integer> goals = Map.of(
                "Revenue Target", 83,
                "Customer Acquisition", 67,
                "Product Development", 92);

        return FounderDashboardResponse.builder()
                .cashRunwayMonths(cashRunwayMonths)
                .revenueGrowth(revenueData)
                .burnRateAnalysis(burnRateData)
                .keyPerformanceIndicators(kpi)
                .monthlyGoalsProgress(goals)
                .teamSize(teamSize)
                .build();
    }

    /**
     * ✅ Groups revenue or expense JSON by month name (e.g., "Jun", "Sep").
     */
    private Map<String, Double> groupByMonthShortName(JsonNode root, String arrayKey, String amountKey) {
        Map<String, Double> monthMap = new HashMap<>();
        if (root == null || !root.has(arrayKey))
            return monthMap;

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

        for (JsonNode entry : root.get(arrayKey)) {
            if (entry.has("date") && entry.has(amountKey)) {
                String dateStr = entry.get("date").asText();
                double amount = entry.get(amountKey).asDouble(0.0);
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    String monthKey = date.format(monthFormatter);
                    monthMap.merge(monthKey, amount, Double::sum);
                } catch (Exception ignored) {
                }
            }
        }
        return monthMap;
    }

    /**
     * ✅ Fetches all employees from Zoho and counts them.
     */
    public int getZohoUserCount(String founderEmail) {
        JsonNode response = zohoService.fetchEmployeesFromZoho(founderEmail, 1, 200);
        JsonNode users = response.path("employees");
        if (users.isArray()) {
            return users.size();
        }
        return 0;
    }

    /**
     * ✅ Sums up all account balances from Zoho Bank Accounts API response.
     */
    public double getBankBalance(JsonNode bankAccountsResponse) {
        double totalBalance = 0.0;

        if (bankAccountsResponse != null && bankAccountsResponse.has("bankaccounts")) {
            for (JsonNode account : bankAccountsResponse.get("bankaccounts")) {
                double balance = account.path("balance").asDouble(0.0);
                totalBalance += balance;
            }
        }

        return totalBalance;
    }

    public double getChurnRateFromContacts(JsonNode contactsJson, String startDateStr, String endDateStr) {

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        Set<String> activeAtStart = new HashSet<>();
        Set<String> inactiveAtEnd = new HashSet<>();

        if (contactsJson.has("contacts")) {
            for (JsonNode c : contactsJson.get("contacts")) {

                // Only customers
                if (!c.has("contact_type") ||
                        !c.get("contact_type").asText().equalsIgnoreCase("customer")) {
                    continue;
                }

                // Parse created_time → "YYYY-MM-DDTHH:MM:SS"
                String createdRaw = c.get("created_time").asText();
                LocalDate createdDate = LocalDate.parse(createdRaw.substring(0, 10));

                String id = c.get("contact_id").asText();
                String status = c.has("status") ? c.get("status").asText() : "active";

                // Customer existed by the start date → count as potential start customers
                if (!createdDate.isAfter(startDate)) {

                    // Only count active customers at start
                    if (status.equalsIgnoreCase("active")) {
                        activeAtStart.add(id);
                    }
                }

                // Customer existed by the end date & is inactive now → churned
                if (!createdDate.isAfter(endDate)) {
                    if (status.equalsIgnoreCase("inactive")) {
                        inactiveAtEnd.add(id);
                    }
                }
            }
        }

        // Churned customers = active at start BUT inactive at end
        Set<String> lost = new HashSet<>(activeAtStart);
        lost.retainAll(inactiveAtEnd);

        int startingActiveCustomers = activeAtStart.size();
        int lostCount = lost.size();

        if (startingActiveCustomers == 0)
            return 0.0;

        return (lostCount * 100.0) / startingActiveCustomers;
    }

    public double calculateAverageCustomerLifespan(JsonNode contactsJson) {

        LocalDate today = LocalDate.now();
        double totalMonths = 0.0;
        int customerCount = 0;

        if (!contactsJson.has("contacts")) {
            return 0.0;
        }

        for (JsonNode contact : contactsJson.get("contacts")) {

            // Only customer type
            if (!contact.has("contact_type") ||
                    !contact.get("contact_type").asText().equalsIgnoreCase("customer")) {
                continue;
            }

            // Parse created_time: "2025-09-24T22:18:31+0530"
            if (!contact.has("created_time"))
                continue;

            String createdRaw = contact.get("created_time").asText();
            LocalDate createdDate = LocalDate.parse(createdRaw.substring(0, 10));

            // Calculate lifespan in MONTHS
            long months = ChronoUnit.MONTHS.between(createdDate, today);

            if (months < 0)
                months = 0; // safety check

            totalMonths += months;
            customerCount++;
        }

        if (customerCount == 0)
            return 0.0;

        return totalMonths / customerCount; // Average Months
    }

    public double calculateLTV(
            JsonNode invoicesJson,
            JsonNode pnlJson,
            JsonNode contactsJson,
            double averageLifespan) {

        // ---------------------------
        // 1️⃣ Calculate Total Revenue (from invoices)
        // ---------------------------
        double totalRevenue = 0.0;

        if (invoicesJson.has("invoices")) {
            for (JsonNode invoice : invoicesJson.get("invoices")) {

                if (invoice.has("status") &&
                        invoice.get("status").asText().equalsIgnoreCase("paid")) {

                    if (invoice.has("total")) {
                        totalRevenue += invoice.get("total").asDouble();
                    }
                }
            }
        }

        // Avoid divide-by-zero later
        if (totalRevenue <= 0)
            return 0.0;

        // ---------------------------
        // 2️⃣ Extract Gross Profit from P&L JSON
        // ---------------------------
        double grossProfit = 0.0;

        if (pnlJson.has("profit_and_loss")) {
            for (JsonNode section : pnlJson.get("profit_and_loss")) {
                if (section.has("name") &&
                        section.get("name").asText().equalsIgnoreCase("Gross Profit")) {

                    grossProfit = section.get("total").asDouble();
                    break;
                }
            }
        }

        // If grossProfit missing → cannot compute margin
        if (grossProfit <= 0)
            return 0.0;

        double grossMarginDecimal = grossProfit / totalRevenue; // 0.75 means 75% margin

        // ---------------------------
        // 3️⃣ Count customers from contacts JSON
        // ---------------------------
        int customerCount = 0;

        if (contactsJson.has("contacts")) {
            for (JsonNode c : contactsJson.get("contacts")) {
                if (c.has("contact_type") &&
                        c.get("contact_type").asText().equalsIgnoreCase("customer")) {

                    customerCount++;
                }
            }
        }

        if (customerCount == 0)
            return 0.0;

        // ---------------------------
        // 4️⃣ Final LTV Calculation
        // ---------------------------
        double ltv = ((totalRevenue / customerCount) * grossMarginDecimal) * averageLifespan;

        return ltv;
    }

    public double calculateCAC(
            JsonNode expensesJson,
            JsonNode contactsJson,
            String startDateStr,
            String endDateStr) {

        LocalDate startDate = LocalDate.parse(startDateStr);
        LocalDate endDate = LocalDate.parse(endDateStr);

        // -----------------------------
        // 1️⃣ Sum Marketing Expenses
        // -----------------------------

        // Customize your marketing categories here
        Set<String> marketingCategories = Set.of(
                "Marketing");

        double marketingExpenseTotal = 0.0;

        if (expensesJson.has("expenses")) {
            for (JsonNode exp : expensesJson.get("expenses")) {

                if (!exp.has("account_name"))
                    continue;

                String accountName = exp.get("account_name").asText();

                // Check if this expense falls under marketing
                if (marketingCategories.contains(accountName)) {

                    if (exp.has("total")) {
                        marketingExpenseTotal += exp.get("total").asDouble();
                    }
                }
            }
        }

        // -----------------------------
        // 2️⃣ Count New Customers
        // -----------------------------
        int newCustomerCount = 0;

        if (contactsJson.has("contacts")) {
            for (JsonNode contact : contactsJson.get("contacts")) {

                if (!contact.has("contact_type") ||
                        !contact.get("contact_type").asText().equalsIgnoreCase("customer")) {
                    continue;
                }

                if (!contact.has("created_time"))
                    continue;

                String createdTime = contact.get("created_time").asText();
                LocalDate createdDate = LocalDate.parse(createdTime.substring(0, 10));

                // New customer falls inside the date range
                if (!createdDate.isBefore(startDate) && !createdDate.isAfter(endDate)) {
                    newCustomerCount++;
                }
            }
        }

        // -----------------------------
        // 3️⃣ Final CAC Calculation
        // -----------------------------

        if (newCustomerCount == 0)
            return 0.0; // avoid division by zero

        return marketingExpenseTotal / newCustomerCount;
    }

}
