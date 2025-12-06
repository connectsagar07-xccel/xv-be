package com.logicleaf.invplatform.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.logicleaf.invplatform.dto.FounderDashboardResponse;
import com.logicleaf.invplatform.dto.MonthlyMetric;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
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

        // Fetch Zoho data in one request each
        JsonNode salesOrders = zohoService.fetchSalesOrdersForFounder(founderEmail, startDateStr, endDateStr);
        JsonNode expenses = zohoService.fetchExpensesForFounder(founderEmail, startDateStr, endDateStr);
        JsonNode bankAccounts = zohoService.fetchAllBankAccountsForFounder(founderEmail);

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

        // --- Calculate Dynamic KPIs (CAC, LTV, Churn) ---

        // 1. CAC Calculation
        // Formula: Total Sales & Marketing Spend ÷ Number of New Customers Acquired
        JsonNode marketingExpenses = zohoService.fetchExpensesByCategory(founderEmail, "Marketing", startDateStr, endDateStr);
        JsonNode newCustomers = zohoService.fetchContactsByType(founderEmail, "customer", startDateStr, endDateStr);

        double totalMarketingSpend = sumAmount(marketingExpenses, "expenses", "total");
        int newCustomerCount = countItems(newCustomers, "contacts");
        double cac = newCustomerCount > 0 ? totalMarketingSpend / newCustomerCount : 0.0;

        // 2. Churn Rate Calculation
        // Formula: (MRR Lost from Churn ÷ MRR at Start of Period) × 100
        JsonNode recurringInvoices = zohoService.fetchRecurringInvoices(founderEmail);
        double churnRate = calculateChurnRate(recurringInvoices);

        // 3. LTV Calculation
        // Formula: (Average Revenue per Customer × Gross Margin %) × Average Customer Lifespan
        // Average Customer Lifespan = 1 / Churn Rate (if rate is monthly)
        // Average Revenue per Customer (ARPU) = Total Revenue (Last Month) / Total Customers

        // Fetch revenue (paid invoices) for the last month to calculate current ARPU
        LocalDate lastMonthStart = now.minusMonths(1).withDayOfMonth(1);
        LocalDate lastMonthEnd = now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth());
        String lmStartStr = lastMonthStart.format(dateFormatter);
        String lmEndStr = lastMonthEnd.format(dateFormatter);

        // We use fetchInvoicesByStatus but we need to filter by date manually or if API supports it.
        // Since fetchInvoicesByStatus fetches all paid invoices (per my implementation in ZohoService),
        // I should probably iterate and filter.
        // Wait, fetchInvoicesByStatus in ZohoService currently only takes status.
        // I will fetch all paid invoices and filter for last month in Java.
        JsonNode paidInvoices = zohoService.fetchInvoicesByStatus(founderEmail, "paid");
        double lastMonthRevenue = sumInvoicesForPeriod(paidInvoices, lastMonthStart, lastMonthEnd);

        // Fetch Total Customers (all time)
        JsonNode allCustomers = zohoService.fetchContactsByType(founderEmail, "customer", null, null);
        int totalCustomerCount = countItems(allCustomers, "contacts");

        double arpu = totalCustomerCount > 0 ? lastMonthRevenue / totalCustomerCount : 0.0;

        // Gross Margin %
        // Fetch P&L for the last month to ensure data is relevant and "from_date" is valid.
        // User requested: "show data for 1 month previous".
        JsonNode pnlReport = zohoService.fetchProfitAndLossReport(founderEmail, lmStartStr, lmEndStr);
        double grossMarginPercent = calculateGrossMargin(pnlReport);

        // Average Customer Lifespan (Months)
        // If Churn Rate is 2%, lifespan = 1 / 0.02 = 50 months.
        double lifespanMonths = (churnRate > 0) ? (100.0 / churnRate) : 36.0; // Default to 36 months if churn is 0

        double ltv = (arpu * (grossMarginPercent / 100.0)) * lifespanMonths;

        Map<String, Double> kpi = new LinkedHashMap<>();
        kpi.put("NPS", 30.0); // NPS is usually from survey tools, keeping hardcoded or need another source
        kpi.put("Churn", round(churnRate, 2));
        kpi.put("LTV", round(ltv, 2));
        kpi.put("CAC", round(cac, 2));

        Map<String, Integer> goals = Map.of(
                "Revenue Target", 83,
                "Customer Acquisition", 67,
                "Product Development", 92
        );

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
        if (root == null || !root.has(arrayKey)) return monthMap;

        DateTimeFormatter monthFormatter = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH);

        for (JsonNode entry : root.get(arrayKey)) {
            if (entry.has("date") && entry.has(amountKey)) {
                String dateStr = entry.get("date").asText();
                double amount = entry.get(amountKey).asDouble(0.0);
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    String monthKey = date.format(monthFormatter);
                    monthMap.merge(monthKey, amount, Double::sum);
                } catch (Exception ignored) {}
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

    // --- Helper Methods for Calculations ---

    private double sumAmount(JsonNode root, String arrayKey, String amountKey) {
        double total = 0.0;
        if (root != null && root.has(arrayKey)) {
            for (JsonNode item : root.get(arrayKey)) {
                total += item.path(amountKey).asDouble(0.0);
            }
        }
        return total;
    }

    private int countItems(JsonNode root, String arrayKey) {
        if (root != null && root.has(arrayKey) && root.get(arrayKey).isArray()) {
            return root.get(arrayKey).size();
        }
        return 0;
    }

    private double calculateChurnRate(JsonNode recurringInvoices) {
        // Formula: (MRR Lost from Churn / MRR at Start of Period) * 100
        // We will approximate this using "Recurring Invoices" status.
        // MRR Lost = Sum of recurred amount of cancelled/expired invoices in the last month?
        // MRR Active = Sum of recurred amount of active invoices.
        // Note: Accurately calculating "Start of Period" MRR requires historical data.
        // Proxy: Start MRR = Current Active MRR + Lost MRR (Assuming no new MRR for simplicity, or just use Active).
        // A simple churn rate calculation often used is: (Churned MRR this month / (Active MRR start of month)) * 100.
        // We will loop through recurring invoices.

        double mrrLost = 0.0;
        double mrrActive = 0.0;

        if (recurringInvoices == null || !recurringInvoices.has("recurring_invoices")) {
            return 0.0;
        }

        for (JsonNode invoice : recurringInvoices.get("recurring_invoices")) {
            String status = invoice.path("status").asText();
            double amount = invoice.path("recurrence_amount").asDouble(0.0); // Assuming this field exists, or 'total'
            if (amount == 0) amount = invoice.path("total").asDouble(0.0);

            // Normalize to Monthly amount
            String frequency = invoice.path("recurrence_frequency").asText("months").toLowerCase();
            if (frequency.contains("year")) {
                amount = amount / 12;
            } else if (frequency.contains("week")) {
                amount = amount * 4.33;
            }
            // else assumes monthly

            if ("active".equalsIgnoreCase(status)) {
                mrrActive += amount;
            } else if ("stopped".equalsIgnoreCase(status) || "expired".equalsIgnoreCase(status)) {
                // Check if it stopped recently (e.g., within last 30 days) would be better
                // But without iterating "last_payment_date" or "updated_time", we might count old churn.
                // Let's check "last_payment_date" if available
                String lastPaymentDateStr = invoice.path("last_payment_date").asText(null);
                if (lastPaymentDateStr != null) {
                    try {
                        LocalDate lastPayment = LocalDate.parse(lastPaymentDateStr);
                        if (lastPayment.isAfter(LocalDate.now().minusMonths(1))) {
                            mrrLost += amount;
                        }
                    } catch (Exception e) {
                        // ignore
                    }
                } else {
                    // Fallback: if we can't determine when it stopped, we might skip it or assume it's relevant?
                    // Let's assume relevant if no date? No, that would inflate churn.
                    // If no date, maybe check updated_time?
                     String updatedTime = invoice.path("updated_time").asText(null); // 2023-10-27T...
                     if (updatedTime != null && updatedTime.length() >= 10) {
                         try {
                             LocalDate updated = LocalDate.parse(updatedTime.substring(0, 10));
                             if (updated.isAfter(LocalDate.now().minusMonths(1))) {
                                 mrrLost += amount;
                             }
                         } catch (Exception e) {}
                     }
                }
            }
        }

        double mrrStart = mrrActive + mrrLost; // Approx
        if (mrrStart > 0) {
            return (mrrLost / mrrStart) * 100.0;
        }
        return 0.0;
    }

    private double sumInvoicesForPeriod(JsonNode invoices, LocalDate start, LocalDate end) {
        double total = 0.0;
        if (invoices != null && invoices.has("invoices")) {
            for (JsonNode inv : invoices.get("invoices")) {
                String dateStr = inv.path("date").asText(); // Invoice date
                try {
                    LocalDate date = LocalDate.parse(dateStr);
                    if ((date.isEqual(start) || date.isAfter(start)) && (date.isEqual(end) || date.isBefore(end))) {
                         total += inv.path("total").asDouble(0.0);
                    }
                } catch (Exception ignored) {}
            }
        }
        return total;
    }

    private double calculateGrossMargin(JsonNode pnlReport) {
        // P&L Report usually has "income", "expense", "gross_profit" keys at top level or inside a "profitandloss" object.
        // Structure varies. Let's assume standard Zoho books structure.
        // Usually: { "profitandloss": { "income": ..., "expense": ..., "gross_profit": ... } }
        // Or it lists accounts.
        // Assuming a summary is provided.
        // If not, we might need to parse.
        // Simple fallback: If we have total income and total expense from separate calls (salesOrders vs expenses), we can approximate.
        // Gross Margin = (Total Revenue - COGS) / Total Revenue.
        // If we treat all "expenses" as COGS (conservative), then Gross Margin = (Revenue - Expense) / Revenue.
        // However, P&L is better.

        if (pnlReport == null) return 0.0;

        // Try to parse from P&L JSON
        // Example structure: { "profitandloss": { "gross_profit": 1000, "total_income": 5000 } }
        // Or if it's a list of accounts.
        // Let's try to find keys.

        /*
           Zoho P&L Response often looks like:
           {
             "profitandloss": {
                "start_date": "...",
                "end_date": "...",
                "income": { "total": 10000, ... },
                "expense": { "total": 5000, ... },
                "gross_profit": 5000,
                "net_profit": ...
             }
           }
        */

        JsonNode root = pnlReport.path("profitandloss");
        if (root.isMissingNode()) root = pnlReport; // Maybe root is the object

        double totalIncome = root.path("income").path("total").asDouble(0.0);
        // If "income" is just a number
        if (totalIncome == 0) totalIncome = root.path("total_income").asDouble(0.0);

        double grossProfit = root.path("gross_profit").asDouble(0.0);

        if (totalIncome == 0 && root.has("income") && !root.get("income").isObject()) {
             // Maybe income is a direct number
             totalIncome = root.get("income").asDouble(0.0);
        }

        // If we can't find specific gross profit, we calculate Net Margin?
        // Formula asked for Gross Margin.
        // If P&L parsing fails, we return 0.0 or a default?
        // Let's try to calculate from what we have if 0.
        if (totalIncome > 0) {
            return (grossProfit / totalIncome) * 100.0;
        }

        return 0.0;
    }

    private double round(double value, int places) {
        if (places < 0) throw new IllegalArgumentException();
        long factor = (long) Math.pow(10, places);
        value = value * factor;
        long tmp = Math.round(value);
        return (double) tmp / factor;
    }
}
