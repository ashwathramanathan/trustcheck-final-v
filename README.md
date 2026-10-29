# TrustCheck

TrustCheck is a small, self-contained Java web application that helps students inspect internship offers, companies, and certificate issuers. It uses Java's built-in `HttpServer`, a static HTML/CSS/vanilla JavaScript interface, and a local text file for persistent community submissions. There are no Java dependencies or external databases.

## Prerequisites

- JDK 17 or newer (both `java` and `javac` on PATH)
- A modern browser

## Run on Windows

1. Extract the ZIP.
2. Double-click `run.bat` (or open a terminal in the extracted `TrustCheck` folder).
3. In a terminal, run `run.bat`.
4. Visit <http://localhost:8080>.
5. Press Ctrl+C in the server window to stop it.

## Run on macOS or Linux

From the extracted `TrustCheck` directory:

```sh
chmod +x run.sh
./run.sh
```

Then open <http://localhost:8080>. Set the `PORT` environment variable to use a different local port.

## Project layout

```text
TrustCheck/
├── README.md
├── run.bat
├── run.sh
├── src/trustcheck/TrustCheckServer.java
├── src/trustcheck/TrustCheckSmokeTest.java
├── web/
│   ├── index.html
│   ├── style.css
│   └── app.js
└── data/
    ├── verified-entities.csv
    └── reports.db                 (created on first run)
```

## Try these checks

| Scenario | Category | Name / text | Expected behavior |
|---|---|---|---|
| Exact starter record | Company | `OpenAI` | `VERIFIED` exact starter-dataset match, unless reports or warning phrases create a conflict |
| Warning phrases | Internship offer | `Northstar` with “pay registration fee for guaranteed placement” | `SUSPICIOUS`, with matched phrases shown |
| Unknown name | Certificate issuer | `Example Learning House` | `UNVERIFIED`; absence from the small list is not evidence of fraud |
| Partial name | Company | `OpenAI Research Division` | `UNVERIFIED` partial match, not a verified match |
| Community flow | Any | Submit a report with a description of 15+ characters | Saved to `data/reports.db`, shown on the community board, searchable and filterable, labelled “Community allegation” |

## How the result is determined

1. **Starter dataset:** `data/verified-entities.csv` contains a tiny set of example reference entries. Names are matched ignoring case and surrounding whitespace. Exact and partial matches are reported separately. The file is provided to demonstrate the mechanism; it does not represent all legitimate organizations.
2. **Keyword scan:** selected phrases related to upfront fees and guaranteed placement trigger `SUSPICIOUS`. These phrases are warnings, not proof of fraud. A clean scan cannot make an entity verified.
3. **Community:** submitted reports are stored locally in `data/reports.db` and displayed as user allegations. Reports are not independently checked and do not become verified facts due to volume.

Only an exact dataset match with no matching report or detected warning phrase can return `VERIFIED`. Always verify an offer using contact information found independently on an organization's official website. TrustCheck does not query live registries or external reputation services, authenticate evidence, or verify certificates. It provides no accuracy guarantee, official partnership, or professional investigative service. The local server is intended for a demonstration on a trusted device or network; reports are not encrypted or moderated, and the server binds to localhost by default.

## Beginner notes

- `TrustCheckServer` owns HTTP routing and request validation.
- `EntityRepository` reads the starter CSV.
- `KeywordDetector` finds warning phrases.
- `VerificationService` combines the three signals without treating missing data as proof.
- `ReportRepository` persists user submissions using standard Java file APIs.
- The browser sends form-encoded requests to `/api/verify` and `/api/reports`.

No external libraries, framework, database, build tool, or runtime service is required. The interface uses system font fallbacks and does not require an internet connection.

## Run core smoke checks

```sh
mkdir -p build
javac --add-modules jdk.httpserver -encoding UTF-8 -d build src/trustcheck/*.java
java -cp build trustcheck.TrustCheckSmokeTest
```
