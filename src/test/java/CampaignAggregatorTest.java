import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.linhhq.CampaignAggregator;
import org.linhhq.CampaignStats;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class CampaignAggregatorTest {

    @TempDir
    Path tempDir;

    @Test
    void testAggregationAndTop10() throws IOException {
        // Create a sample CSV file
        Path input = tempDir.resolve("test.csv");
        List<String> lines = Arrays.asList(
                "campaign_id,date,impressions,clicks,spend,conversions",
                "c1,2024-01-01,1000,50,100.5,10",
                "c1,2024-01-02,500,30,50.0,5",
                "c2,2024-01-01,2000,0,200.0,0",
                "c3,2024-01-01,0,0,0,0",
                "c4,2024-01-01,100,20,25.0,2",
                "c4,2024-01-02,100,10,10.0,1",
                "c5,2024-01-01,300,60,30.0,3"   // high CTR: 0.2, CPA: 10.0
        );
        Files.write(input, lines);

        // Aggregate
        Map<String, CampaignStats> stats = CampaignAggregator.aggregate(input.toString(), true);

        // Verify totals
        assertEquals(5, stats.size());

        CampaignStats c1 = stats.get("c1");
        assertEquals(1500, c1.getImpressions());
        assertEquals(80, c1.getClicks());
        assertEquals(150.5, c1.getSpend(), 0.001);
        assertEquals(15, c1.getConversions());
        assertEquals(Math.floor(80.0 / 1500 * 10000) / 10000, c1.ctr(), 1e-9);
        assertEquals(Math.floor(150.5 / 15 * 100) / 100, c1.cpa(), 1e-9);

        CampaignStats c2 = stats.get("c2");
        assertEquals(2000, c2.getImpressions());
        assertEquals(0, c2.getClicks());
        assertEquals(200.0, c2.getSpend());
        assertEquals(0, c2.getConversions());
        assertEquals(0.0, c2.ctr());
        assertEquals(0.0, c2.cpa());

        // Write reports
        CampaignAggregator.writeTop10ByCTR(stats, tempDir.toString());
        CampaignAggregator.writeTop10ByCPA(stats, tempDir.toString());

        // Check top10_ctr.csv (highest CTR)
        Path ctrFile = tempDir.resolve("top10_ctr.csv");
        assertTrue(Files.exists(ctrFile));
        List<String> ctrLines = Files.readAllLines(ctrFile);
        assertEquals(5, ctrLines.size()); // header + 4 campaigns with impressions>0
        // Expected order by CTR: c5 (0.2), c1 (0.05333), c4 (0.15? wait c4: total clicks 30, impressions 200 -> CTR=0.15), c2 (0.0)
        // Actually c4: 20+10=30 clicks, 100+100=200 impressions -> 0.15; c5: 60/300=0.2; c1: 80/1500=0.05333; c2: 0.0
        // So order: c5, c4, c1, c2. c3 excluded because impressions=0.
        assertTrue(ctrLines.get(1).startsWith("c5"));
        assertTrue(ctrLines.get(2).startsWith("c4"));
        assertTrue(ctrLines.get(3).startsWith("c1"));
        assertTrue(ctrLines.get(4).startsWith("c2"));

        // Check top10_cpa.csv (lowest CPA, only campaigns with conversions>0)
        Path cpaFile = tempDir.resolve("top10_cpa.csv");
        assertTrue(Files.exists(cpaFile));
        List<String> cpaLines = Files.readAllLines(cpaFile);
        assertEquals(4, cpaLines.size()); // header + c5, c4, c1 (c2 and c3 have conversions=0)
        // Expected CPA: c5: 30/3=10.0; c4: (25+10)/(2+1)=35/3≈11.6667; c1: 150.5/15≈10.0333
        // Order ascending: c5 (10.0), c1 (10.0333), c4 (11.6667)
        assertTrue(cpaLines.get(1).startsWith("c5"));
        assertTrue(cpaLines.get(2).startsWith("c1"));
        assertTrue(cpaLines.get(3).startsWith("c4"));
    }

    @Test
    void testMalformedRowsAreSkipped() throws IOException {
        Path input = tempDir.resolve("bad.csv");
        List<String> lines = Arrays.asList(
                "campaign_id,date,impressions,clicks,spend,conversions",
                "good,2024-01-01,100,10,10.0,1",
                "bad,2024-01-01,not_a_number,10,10.0,1",  // invalid impressions
                "short,2024-01-01,100,10",                // too few columns
                ",2024-01-01,100,10,10.0,1",              // empty campaign_id
                "good2,2024-01-01,200,20,20.0,2"
        );
        Files.write(input, lines);

        Map<String, CampaignStats> stats = CampaignAggregator.aggregate(input.toString(), true);
        assertEquals(2, stats.size()); // only "good" and "good2"
        assertTrue(stats.containsKey("good"));
        assertTrue(stats.containsKey("good2"));
        assertEquals(100, stats.get("good").getImpressions());
        assertEquals(200, stats.get("good2").getImpressions());
    }

    @Test
    void testEmptyFile() throws IOException {
        Path input = tempDir.resolve("empty.csv");
        Files.write(input, Collections.emptyList());

        Map<String, CampaignStats> stats = CampaignAggregator.aggregate(input.toString(), true);
        assertTrue(stats.isEmpty());

        CampaignAggregator.writeTop10ByCTR(stats, tempDir.toString());
        CampaignAggregator.writeTop10ByCPA(stats, tempDir.toString());

        Path ctrFile = tempDir.resolve("top10_ctr.csv");
        List<String> ctrLines = Files.readAllLines(ctrFile);
        assertEquals(1, ctrLines.size()); // only header
        assertEquals("campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA", ctrLines.get(0));
    }

    @Test
    void testLessThanTenCampaigns() throws IOException {
        Path input = tempDir.resolve("small.csv");
        List<String> lines = Arrays.asList(
                "campaign_id,date,impressions,clicks,spend,conversions",
                "a,2024-01-01,100,10,10.0,1",
                "b,2024-01-01,200,20,20.0,2"
        );
        Files.write(input, lines);

        Map<String, CampaignStats> stats = CampaignAggregator.aggregate(input.toString(), true);
        CampaignAggregator.writeTop10ByCTR(stats, tempDir.toString());
        CampaignAggregator.writeTop10ByCPA(stats, tempDir.toString());

        List<String> ctrLines = Files.readAllLines(tempDir.resolve("top10_ctr.csv"));
        assertEquals(3, ctrLines.size()); // header + 2 rows
        List<String> cpaLines = Files.readAllLines(tempDir.resolve("top10_cpa.csv"));
        assertEquals(3, cpaLines.size()); // header + 2 rows
    }

    @Test
    void testNoHeader() throws IOException {
        // Create a sample CSV file without a header row
        Path input = tempDir.resolve("noheader.csv");
        List<String> lines = Arrays.asList(
                "c1,2024-01-01,1000,50,100.5,10",
                "c2,2024-01-01,2000,0,200.0,0",
                "c3,2024-01-01,0,0,0,0",
                "c4,2024-01-01,100,20,25.0,2",
                "c4,2024-01-02,100,10,10.0,1",
                "c5,2024-01-01,300,60,30.0,3"
        );
        Files.write(input, lines);

        // Aggregate with hasHeader = false → first line is treated as data
        Map<String, CampaignStats> stats =
                CampaignAggregator.aggregate(input.toString(), false);

        // Verify that all 5 campaigns are present (no row was skipped)
        assertEquals(5, stats.size()); // c1, c2, c3, c4, c5

        // Validate aggregated data for campaign c1
        CampaignStats c1 = stats.get("c1");
        assertEquals(1000, c1.getImpressions());
        assertEquals(50, c1.getClicks());
        assertEquals(100.5, c1.getSpend(), 0.001);
        assertEquals(10, c1.getConversions());

        // Validate aggregated data for campaign c4 (two rows merged)
        CampaignStats c4 = stats.get("c4");
        assertEquals(200, c4.getImpressions());
        assertEquals(30, c4.getClicks());
        assertEquals(35.0, c4.getSpend(), 0.001);
        assertEquals(3, c4.getConversions());

        // Write the two output reports
        CampaignAggregator.writeTop10ByCTR(stats, tempDir.toString());
        CampaignAggregator.writeTop10ByCPA(stats, tempDir.toString());

        // Check top10_ctr.csv – only campaigns with impressions > 0
        Path ctrFile = tempDir.resolve("top10_ctr.csv");
        List<String> ctrLines = Files.readAllLines(ctrFile);
        // Header + 4 campaigns (c1, c2, c4, c5) – c3 excluded because impressions = 0
        assertEquals(5, ctrLines.size());

        // Check top10_cpa.csv – only campaigns with conversions > 0
        Path cpaFile = tempDir.resolve("top10_cpa.csv");
        List<String> cpaLines = Files.readAllLines(cpaFile);
        // Header + 3 campaigns (c1, c4, c5) – c2 and c3 excluded
        assertEquals(4, cpaLines.size());
    }

    @Test
    void testTop10CtrMatchesExpectedOutput() throws IOException {
        // Create a sample CSV file with known data
        Path input = tempDir.resolve("data.csv");
        List<String> lines = Arrays.asList(
                "campaign_id,date,impressions,clicks,spend,conversions",
                "CMP042,2024-01-01,125000,6250,12500.50,625",
                "CMP015,2024-01-01,340000,15300,30600.25,1530",
                "CMP008,2024-01-01,890000,35600,71200.75,3560",
                "CMP023,2024-01-01,445000,15575,31150.00,1557",
                "CMP031,2024-01-01,670000,20100,40200.50,2010"
        );
        Files.write(input, lines);

        // Aggregate and write top10 by CTR
        Map<String, CampaignStats> stats =
                CampaignAggregator.aggregate(input.toString(), true);
        CampaignAggregator.writeTop10ByCTR(stats, tempDir.toString());

        // Read the generated top10_ctr.csv and verify its contents
        Path ctrFile = tempDir.resolve("top10_ctr.csv");
        List<String> outputLines = Files.readAllLines(ctrFile);

        // Expected output lines based on the input data (CTR = clicks/impressions, CPA = spend/conversions)
        List<String> expectedLines = Arrays.asList(
                "campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA",
                "CMP042,125000,6250,12500.500000,625,0.0500,20.00",
                "CMP015,340000,15300,30600.250000,1530,0.0450,20.00",
                "CMP008,890000,35600,71200.750000,3560,0.0400,20.00",
                "CMP023,445000,15575,31150.000000,1557,0.0350,20.00",
                "CMP031,670000,20100,40200.500000,2010,0.0300,20.00"
        );

        assertEquals(expectedLines, outputLines);
    }

    @Test
    void testTop10CpaMatchesExpectedOutput() throws IOException {
        // Create a sample CSV file with known data
        Path input = tempDir.resolve("data.csv");
        List<String> lines = Arrays.asList(
                "campaign_id,date,impressions,clicks,spend,conversions",
                "CMP007,2024-01-01,450000,13500,13500.00,1350",
                "CMP019,2024-01-01,780000,23400,23400.00,2340",
                "CMP033,2024-01-01,290000,8700,10440.00,870",
                "CMP012,2024-01-01,560000,16800,21840.00,1680",
                "CMP025,2024-01-01,320000,9600,13440.00,960"
        );
        Files.write(input, lines);

        // Aggregate and write top10 by CPA
        Map<String, CampaignStats> stats =
                CampaignAggregator.aggregate(input.toString(), true);
        CampaignAggregator.writeTop10ByCPA(stats, tempDir.toString());

        // Read the generated top10_cpa.csv and verify its contents
        Path ctrFile = tempDir.resolve("top10_cpa.csv");
        List<String> outputLines = Files.readAllLines(ctrFile);

        // Expected output lines based on the input data (CTR = clicks/impressions, CPA = spend/conversions)
        List<String> expectedLines = Arrays.asList(
                "campaign_id,total_impressions,total_clicks,total_spend,total_conversions,CTR,CPA",
                "CMP019,780000,23400,23400.000000,2340,0.0300,10.00",
                "CMP007,450000,13500,13500.000000,1350,0.0300,10.00",
                "CMP033,290000,8700,10440.000000,870,0.0300,12.00",
                "CMP012,560000,16800,21840.000000,1680,0.0300,13.00",
                "CMP025,320000,9600,13440.000000,960,0.0300,14.00"
        );

        assertEquals(expectedLines, outputLines);
    }
}