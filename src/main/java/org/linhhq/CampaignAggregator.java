package org.linhhq;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CLI tool to aggregate campaign data from a large CSV file and produce
 * top‑10 reports by CTR and CPA.
 * Usage:
 * java CampaignAggregator --input <path> [--output <dir>]
 * If --output is omitted, files are written to the current working directory.
 */
public class CampaignAggregator {

    // Column indices (0‑based)
    private static final int COL_CAMPAIGN_ID = 0;
    private static final int COL_IMPRESSIONS = 2;
    private static final int COL_CLICKS = 3;
    private static final int COL_SPEND = 4;
    private static final int COL_CONVERSIONS = 5;
    private static final int EXPECTED_COLUMNS = 6;

    // Output file names
    private static final String OUTPUT_CTR = "top10_ctr.csv";
    private static final String OUTPUT_CPA = "top10_cpa.csv";

    // Output CSV headers
    private static final String[] HEADERS = {
            "campaign_id", "total_impressions", "total_clicks",
            "total_spend", "total_conversions", "CTR", "CPA"
    };

    public static void main(String[] args) {
        long startTime = System.currentTimeMillis();

        String input = null;
        String output = null;
        boolean hasHeader = true;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--input":
                    if (i + 1 < args.length) {
                        input = args[++i];
                    } else {
                        System.err.println("Error: --input requires a file path");
                        System.exit(1);
                    }
                    break;
                case "--output":
                    if (i + 1 < args.length) {
                        output = args[++i];
                    } else {
                        System.err.println("Error: --output requires a directory path");
                        System.exit(1);
                    }
                    break;
                case "--no-header":
                    hasHeader = false;
                    break;
                default:
                    System.err.println("Unknown argument: " + args[i]);
                    System.exit(1);
            }
        }

        if (input == null) {
            System.err.println("Error: --input is required");
            System.exit(1);
        }

        if (!input.toLowerCase().endsWith(".csv")) {
            System.err.println("Error: Input file must be a CSV file");
            System.exit(1);
        }

        if (output == null) {
            System.err.println("Error: --output is required");
            System.exit(1);
        }

        printMemoryUsage("Start", startTime);
        try {
            Map<String, CampaignStats> campaignMap = aggregate(input, hasHeader);

            if (campaignMap.isEmpty()) {
                System.err.println("Warning: No valid campaign data found. Output files will contain only headers.");
            }

            writeTop10ByCTR(campaignMap, output);
            writeTop10ByCPA(campaignMap, output);

            System.out.println("Processing completed successfully.");
            System.out.println("Output files: " + Paths.get(output, OUTPUT_CTR) + ", " + Paths.get(output, OUTPUT_CPA));
        } catch (IOException e) {
            System.err.println("I/O error: " + e.getMessage());
            System.exit(1);
        } catch (Exception e) {
            System.err.println("Unexpected error: " + e.getMessage());
            System.exit(1);
        }
        printMemoryUsage("End", startTime);
    }

    /**
     * Reads the CSV line by line and aggregates statistics per campaign.
     * Memory usage is proportional to the number of distinct campaigns.
     *
     * @param csvPath   path to the input CSV file
     * @param hasHeader whether the first line of the CSV is a header row
     * @return map from campaign_id to aggregated statistics
     * @throws IOException if the file cannot be read
     */
    public static Map<String, CampaignStats> aggregate(String csvPath, boolean hasHeader) throws IOException {
        Map<String, CampaignStats> statsMap = new HashMap<>();
        Path file = Paths.get(csvPath);

        if (!Files.exists(file)) {
            throw new FileNotFoundException("Input file not found: " + csvPath);
        }

        System.out.println("Starting aggregation...");
        try (BufferedReader reader = new BufferedReader(Files.newBufferedReader(file), 1024 * 1024)) { // 1 MB buffer
            String line;
            int lineNumber = 0;

            // Read header line (assume first line is header)
            if (hasHeader) {
                if (reader.readLine() == null) return statsMap;
                lineNumber++;
                System.out.println("Skipped header row (line 1)");
            }

            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = line.trim();
                if (line.isEmpty()) {
                    continue;
                }

                String[] parts = line.split(",", -1); // keep empty fields
                if (parts.length < EXPECTED_COLUMNS) {
                    System.err.printf("Warning: Skipping malformed line %d (expected %d columns, got %d)%n",
                            lineNumber, EXPECTED_COLUMNS, parts.length);
                    continue;
                }

                try {
                    String campaignId = parts[COL_CAMPAIGN_ID].trim();
                    if (campaignId.isEmpty()) {
                        System.err.printf("Warning: Skipping line %d – empty campaign_id%n", lineNumber);
                        continue;
                    }

                    long impressions = parseLong(parts[COL_IMPRESSIONS], lineNumber, "impressions");
                    long clicks = parseLong(parts[COL_CLICKS], lineNumber, "clicks");
                    double spend = parseDouble(parts[COL_SPEND], lineNumber, "spend");
                    long conversions = parseLong(parts[COL_CONVERSIONS], lineNumber, "conversions");

                    statsMap.computeIfAbsent(campaignId, k -> new CampaignStats())
                            .add(impressions, clicks, spend, conversions);

                } catch (NumberFormatException e) {
                    System.err.printf("Warning: Skipping line %d – %s%n", lineNumber, e.getMessage());
                }
            }
        }
        return statsMap;
    }

    /**
     * Writes the top‑10 campaigns with the highest CTR.
     * Campaigns with zero total impressions are excluded from the ranking.
     */
    public static void writeTop10ByCTR(Map<String, CampaignStats> campaignMap, String outputDir) throws IOException {
        List<Map.Entry<String, CampaignStats>> eligible = campaignMap.entrySet().stream()
                .filter(it -> it.getValue().getImpressions() > 0)
                .sorted((a, b) -> Double.compare(b.getValue().ctr(), a.getValue().ctr()))
                .toList();

        List<Map.Entry<String, CampaignStats>> top10 = eligible.stream()
                .limit(10)
                .collect(Collectors.toList());

        writeReport(Paths.get(outputDir, OUTPUT_CTR), top10);
    }

    /**
     * Writes the top‑10 campaigns with the lowest CPA.
     * Campaigns with zero total conversions are excluded from the ranking.
     */
    public static void writeTop10ByCPA(Map<String, CampaignStats> campaignMap, String outputDir) throws IOException {
        List<Map.Entry<String, CampaignStats>> eligible = campaignMap.entrySet().stream()
                .filter(it -> it.getValue().getConversions() > 0)
                .sorted(Comparator.comparingDouble(e -> e.getValue().cpa()))
                .toList();

        List<Map.Entry<String, CampaignStats>> top10 = eligible.stream()
                .limit(10)
                .collect(Collectors.toList());

        writeReport(Paths.get(outputDir, OUTPUT_CPA), top10);
    }

    private static void writeReport(Path outputPath, List<Map.Entry<String, CampaignStats>> campaigns) throws IOException {
        // Ensure parent directory exists
        Files.createDirectories(outputPath.getParent());

        try (BufferedWriter writer = Files.newBufferedWriter(outputPath)) {
            // Write header
            writer.write(String.join(",", HEADERS));
            writer.newLine();

            // Write data rows
            for (Map.Entry<String, CampaignStats> entry : campaigns) {
                String campaignId = entry.getKey();
                CampaignStats campaignStats = entry.getValue();
                writer.write(String.format("%s,%d,%d,%.6f,%d,%.4f,%.2f",
                        escapeCsv(campaignId),
                        campaignStats.getImpressions(),
                        campaignStats.getClicks(),
                        campaignStats.getSpend(),
                        campaignStats.getConversions(),
                        campaignStats.ctr(),
                        campaignStats.cpa()));
                writer.newLine();
            }
        }
    }

    /**
     * Basic CSV escaping: wrap in double quotes if contains comma or newline.
     * Campaign IDs are expected to be simple strings, but we handle the general case.
     */
    private static String escapeCsv(String value) {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private static long parseLong(String value, int lineNumber, String fieldName) {
        value = value.trim();
        if (value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(String.format("line %d, field '%s' invalid integer: '%s'", lineNumber, fieldName, value));
        }
    }

    private static double parseDouble(String value, int lineNumber, String fieldName) {
        value = value.trim();
        if (value.isEmpty()) return 0.0;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(String.format("line %d, field '%s' invalid number: '%s'", lineNumber, fieldName, value));
        }
    }

    static void printMemoryUsage(String stage, long startTime) {
        Runtime rt = Runtime.getRuntime();
        long totalMem = rt.totalMemory();
        long freeMem = rt.freeMemory();
        long usedMem = totalMem - freeMem;
        long elapsed = System.currentTimeMillis() - startTime;
        double elapsedSec = elapsed / 1000.0;
        System.out.printf("[%s] Used: %.2f MB | Total: %.2f MB | Max: %.2f MB | Elapsed: %.3f s%n",
                stage,
                usedMem / 1024.0 / 1024.0,
                totalMem / 1024.0 / 1024.0,
                rt.maxMemory() / 1024.0 / 1024.0,
                elapsedSec);
    }
}
