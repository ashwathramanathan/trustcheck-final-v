package trustcheck;

import java.nio.file.Files;
import java.nio.file.Path;

/** Dependency-free smoke checks for the core verification and persistence rules. */
public final class TrustCheckSmokeTest {
    public static void main(String[] args) throws Exception {
        Path temp = Files.createTempDirectory("trustcheck-smoke-");
        Path entitiesFile = temp.resolve("entities.csv");
        Path reportsFile = temp.resolve("reports.db");
        Files.writeString(entitiesFile, "Example Org,company,Smoke test reference\n");
        EntityRepository entities = new EntityRepository(entitiesFile);
        ReportRepository reports = new ReportRepository(reportsFile);
        VerificationService service = new VerificationService(entities, reports, new KeywordDetector());

        require(service.verify("  eXample Org ", "company", "").status().equals("VERIFIED"), "case-insensitive exact match with surrounding whitespace");
        Result suspicious = service.verify("Elsewhere", "internship", "Pay registration fee for guaranteed placement");
        require(suspicious.status().equals("SUSPICIOUS") && suspicious.keywords().size() >= 2, "warning phrase detection");
        require(service.verify("Unknown Name", "certificate", "").status().equals("UNVERIFIED"), "unknown name is not marked verified");
        require(service.verify("Example Org Institute", "company", "").status().equals("UNVERIFIED"), "partial match remains unverified");

        reports.add("Unknown Name", "certificate", "The issuer asked for an advance payment.", "", "");
        require(service.verify("Unknown Name", "certificate", "").status().equals("UNVERIFIED"), "community allegation does not become a verified finding");
        require(reports.search("advance payment", "certificate").contains("Unknown Name"), "report can be searched by description and category");
        ReportRepository reloaded = new ReportRepository(reportsFile);
        require(reloaded.size() == 1 && reloaded.search("Unknown", "all").contains("Community allegation"), "report persists after repository reload");
        System.out.println("TrustCheck smoke checks passed: exact, keyword, unknown, partial, allegation, search, persistence.");
    }

    private static void require(boolean condition, String description) {
        if (!condition) throw new AssertionError("Failed: " + description);
    }
}
