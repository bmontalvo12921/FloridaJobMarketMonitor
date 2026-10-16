import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Locale;


public class JobMarketCollector {

    // ==========================================
    // ADZUNA API SETTINGS
    // ==========================================

    private static final String APP_ID =
            System.getenv("ADZUNA_APP_ID");

    private static final String APP_KEY =
            System.getenv("ADZUNA_APP_KEY");

    private static final String API_URL =
            "https://api.adzuna.com/v1/api";


    // ==========================================
    // MYSQL DATABASE SETTINGS
    // ==========================================

    private static final String DATABASE_URL = databaseUrl();

    private static final String DATABASE_USER =
            envOrFallback("MYSQLUSER", "MYSQL_USER");

    private static final String DATABASE_PASSWORD =
            envOrFallback("MYSQLPASSWORD", "MYSQL_PASSWORD");

    private static String databaseUrl() {
        String host = System.getenv("MYSQLHOST");
        String port = System.getenv("MYSQLPORT");
        String database = System.getenv("MYSQLDATABASE");

        if (host == null || host.isBlank()) {
            return "jdbc:mysql://localhost:3306/job_market";
        }
        if (port == null || port.isBlank()) {
            port = "3306";
        }
        if (database == null || database.isBlank()) {
            database = "railway";
        }

        return "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
    }

    private static String envOrFallback(String primaryName, String fallbackName) {
        String value = System.getenv(primaryName);
        if (value == null || value.isBlank()) {
            value = System.getenv(fallbackName);
        }
        return value;
    }


    // ==========================================
    // MAIN PROGRAM
    // ==========================================

    public static void main(String[] args) {

        System.out.println("======================================");
        System.out.println(" Florida Data Analyst Job Market Monitor");
        System.out.println("======================================");
        System.out.println();

        try {

            checkCredentials();

            createTables();

            String json = getJobsFromAdzuna();

            saveJobs(json);

            System.out.println();
            System.out.println("Data collection completed.");
            System.out.println(
                    "Collection time: " + LocalDateTime.now()
            );

        } catch (Exception e) {

            System.out.println();
            System.out.println("An error occurred:");
            System.out.println(e.getMessage());
            System.exit(1);
        }
    }


    // ==========================================
    // CHECK CREDENTIALS
    // ==========================================

    private static void checkCredentials() {

        if (APP_ID == null || APP_ID.isBlank()) {

            throw new IllegalStateException(
                    "ADZUNA_APP_ID environment variable is missing."
            );
        }

        if (APP_KEY == null || APP_KEY.isBlank()) {

            throw new IllegalStateException(
                    "ADZUNA_APP_KEY environment variable is missing."
            );
        }

        if (DATABASE_USER == null || DATABASE_USER.isBlank()) {

            throw new IllegalStateException(
                    "MYSQLUSER (or MYSQL_USER) environment variable is missing."
            );
        }

        if (DATABASE_PASSWORD == null) {

            throw new IllegalStateException(
                    "MYSQLPASSWORD (or MYSQL_PASSWORD) environment variable is missing."
            );
        }
    }


    // ==========================================
    // CREATE MYSQL TABLES
    // ==========================================

    private static void createTables()
            throws SQLException {

        String jobsTable = """
                CREATE TABLE IF NOT EXISTS jobs (
                    job_id VARCHAR(100) PRIMARY KEY,
                    title VARCHAR(255),
                    company VARCHAR(255),
                    location VARCHAR(255),
                    category VARCHAR(255),
                    salary_min DECIMAL(10,2),
                    salary_max DECIMAL(10,2),
                    salary_predicted BOOLEAN,
                    contract_type VARCHAR(100),
                    contract_time VARCHAR(100),
                    description TEXT,
                    job_url TEXT,
                    created VARCHAR(100),
                    education_level VARCHAR(50),
                    education_requirement VARCHAR(50)
                )
                """;


        String snapshotsTable = """
                CREATE TABLE IF NOT EXISTS job_snapshots (
                    snapshot_id INT AUTO_INCREMENT PRIMARY KEY,
                    job_id VARCHAR(100) NOT NULL,
                    collected_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,

                    FOREIGN KEY (job_id)
                        REFERENCES jobs(job_id)
                        ON DELETE CASCADE
                )
                """;


        try (
                Connection connection =
                        DriverManager.getConnection(
                                DATABASE_URL,
                                DATABASE_USER,
                                DATABASE_PASSWORD
                        );

                PreparedStatement jobsStatement =
                        connection.prepareStatement(jobsTable);

                PreparedStatement snapshotsStatement =
                        connection.prepareStatement(snapshotsTable)
        ) {

            jobsStatement.executeUpdate();

            // Make sure education columns exist
            // This also works if the jobs table already existed.
            ensureEducationColumns(connection);

            snapshotsStatement.executeUpdate();

            System.out.println("MySQL database tables ready.");
        }
    }


    // ==========================================
    // ENSURE EDUCATION COLUMNS EXIST
    // ==========================================

    private static void ensureEducationColumns(Connection connection)
            throws SQLException {

        String checkSql = """
                SELECT COUNT(*)
                FROM INFORMATION_SCHEMA.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'jobs'
                  AND COLUMN_NAME = ?
                """;


        String addEducationLevelSql = """
                ALTER TABLE jobs
                ADD COLUMN education_level VARCHAR(50)
                """;


        String addEducationRequirementSql = """
                ALTER TABLE jobs
                ADD COLUMN education_requirement VARCHAR(50)
                """;


        try (
                PreparedStatement checkStatement =
                        connection.prepareStatement(checkSql)
        ) {

            // ------------------------------------------
            // CHECK EDUCATION LEVEL COLUMN
            // ------------------------------------------

            checkStatement.setString(
                    1,
                    "education_level"
            );

            try (var resultSet =
                         checkStatement.executeQuery()) {

                resultSet.next();

                if (resultSet.getInt(1) == 0) {

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            addEducationLevelSql
                                    )
                    ) {

                        statement.executeUpdate();
                    }
                }
            }


            // ------------------------------------------
            // CHECK EDUCATION REQUIREMENT COLUMN
            // ------------------------------------------

            checkStatement.setString(
                    1,
                    "education_requirement"
            );

            try (var resultSet =
                         checkStatement.executeQuery()) {

                resultSet.next();

                if (resultSet.getInt(1) == 0) {

                    try (
                            PreparedStatement statement =
                                    connection.prepareStatement(
                                            addEducationRequirementSql
                                    )
                    ) {

                        statement.executeUpdate();
                    }
                }
            }
        }
    }


    // ==========================================
    // GET JOBS FROM ADZUNA
    // ==========================================

    private static String getJobsFromAdzuna()
            throws IOException, InterruptedException {

        String what = URLEncoder.encode(
                "data analyst",
                StandardCharsets.UTF_8
        );

        String where = URLEncoder.encode(
                "Florida",
                StandardCharsets.UTF_8
        );


        String url =
                API_URL
                        + "/jobs/us/search/1"
                        + "?app_id=" + APP_ID
                        + "&app_key=" + APP_KEY
                        + "&results_per_page=50"
                        + "&what=" + what
                        + "&where=" + where
                        + "&sort_by=date"
                        + "&sort_dir=down";


        System.out.println(
                "Requesting Data Analyst jobs from Adzuna..."
        );


        HttpClient client =
                HttpClient.newHttpClient();


        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .GET()
                        .build();


        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );


        System.out.println(
                "HTTP Status: " + response.statusCode()
        );


        if (response.statusCode() != 200) {

            throw new IOException(
                    "Adzuna API returned HTTP "
                            + response.statusCode()
                            + "\n"
                            + response.body()
            );
        }


        return response.body();
    }


    // ==========================================
    // SAVE JOBS TO MYSQL
    // ==========================================

    private static void saveJobs(String json)
            throws SQLException {

        JSONObject response;

        try {

            response = new JSONObject(json);

        } catch (Exception e) {

            throw new SQLException(
                    "The Adzuna response could not be read as JSON.",
                    e
            );
        }


        // ------------------------------------------
        // GET RESULTS ARRAY SAFELY
        // ------------------------------------------

        JSONArray jobs =
                response.optJSONArray("results");

        if (jobs == null) {

            throw new SQLException(
                    "Adzuna response does not contain a results array."
            );
        }


        System.out.println(
                "Jobs received: " + jobs.length()
        );


        // ------------------------------------------
        // INSERT OR UPDATE JOB
        // ------------------------------------------

        String jobSql = """
                INSERT INTO jobs (
                    job_id,
                    title,
                    company,
                    location,
                    category,
                    salary_min,
                    salary_max,
                    salary_predicted,
                    contract_type,
                    contract_time,
                    description,
                    job_url,
                    created,
                    education_level,
                    education_requirement
                )
                VALUES (
                    ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?
                )
                ON DUPLICATE KEY UPDATE
                    title = VALUES(title),
                    company = VALUES(company),
                    location = VALUES(location),
                    category = VALUES(category),
                    salary_min = VALUES(salary_min),
                    salary_max = VALUES(salary_max),
                    salary_predicted = VALUES(salary_predicted),
                    contract_type = VALUES(contract_type),
                    contract_time = VALUES(contract_time),
                    description = VALUES(description),
                    job_url = VALUES(job_url),
                    created = VALUES(created),
                    education_level = VALUES(education_level),
                    education_requirement = VALUES(education_requirement)
                """;


        // ------------------------------------------
        // CREATE SNAPSHOT
        // ------------------------------------------

        String snapshotSql = """
                INSERT INTO job_snapshots (
                    job_id
                )
                VALUES (?)
                """;


        try (
                Connection connection =
                        DriverManager.getConnection(
                                DATABASE_URL,
                                DATABASE_USER,
                                DATABASE_PASSWORD
                        );

                PreparedStatement jobStatement =
                        connection.prepareStatement(jobSql);

                PreparedStatement snapshotStatement =
                        connection.prepareStatement(snapshotSql)
        ) {

            int jobsProcessed = 0;
            int snapshotsCreated = 0;
            int jobsSkipped = 0;


            // ------------------------------------------
            // PROCESS EACH JOB
            // ------------------------------------------

            for (int i = 0;
                 i < jobs.length();
                 i++) {

                try {

                    JSONObject job =
                            jobs.getJSONObject(i);


                    // ==================================
                    // BASIC JOB INFORMATION
                    // ==================================

                    String id =
                            job.optString(
                                    "id",
                                    ""
                            );


                    if (id.isBlank()) {

                        System.out.println(
                                "Skipping job because it has no ID."
                        );

                        jobsSkipped++;

                        continue;
                    }


                    String title =
                            job.optString(
                                    "title",
                                    ""
                            );


                    String description =
                            job.optString(
                                    "description",
                                    ""
                            );


                    // ==================================
                    // EDUCATION INFORMATION
                    // ==================================

                    String educationLevel =
                            determineEducationLevel(
                                    description
                            );


                    String educationRequirement =
                            determineEducationRequirement(
                                    description
                            );


                    String created =
                            job.optString(
                                    "created",
                                    ""
                            );


                    String jobUrl =
                            job.optString(
                                    "redirect_url",
                                    ""
                            );


                    // ==================================
                    // SALARY INFORMATION
                    // ==================================

                    double salaryMin =
                            job.optDouble(
                                    "salary_min",
                                    0
                            );


                    double salaryMax =
                            job.optDouble(
                                    "salary_max",
                                    0
                            );


                    boolean salaryPredicted =
                            job.optInt(
                                    "salary_is_predicted",
                                    0
                            ) == 1;


                    // ==================================
                    // CONTRACT INFORMATION
                    // ==================================

                    String contractType =
                            job.optString(
                                    "contract_type",
                                    ""
                            );


                    String contractTime =
                            job.optString(
                                    "contract_time",
                                    ""
                            );


                    // ==================================
                    // COMPANY INFORMATION
                    // ==================================

                    String company = "";

                    JSONObject companyObject =
                            job.optJSONObject("company");

                    if (companyObject != null) {

                        company =
                                companyObject.optString(
                                        "display_name",
                                        ""
                                );
                    }


                    // ==================================
                    // LOCATION INFORMATION
                    // ==================================

                    String location = "";

                    JSONObject locationObject =
                            job.optJSONObject("location");

                    if (locationObject != null) {

                        location =
                                locationObject.optString(
                                        "display_name",
                                        ""
                                );
                    }


                    // ==================================
                    // CATEGORY INFORMATION
                    // ==================================

                    String category = "";

                    JSONObject categoryObject =
                            job.optJSONObject("category");

                    if (categoryObject != null) {

                        category =
                                categoryObject.optString(
                                        "label",
                                        ""
                                );
                    }


                    // ==================================
                    // SAVE JOB
                    // ==================================

                    jobStatement.setString(
                            1,
                            id
                    );

                    jobStatement.setString(
                            2,
                            title
                    );

                    jobStatement.setString(
                            3,
                            company
                    );

                    jobStatement.setString(
                            4,
                            location
                    );

                    jobStatement.setString(
                            5,
                            category
                    );

                    jobStatement.setDouble(
                            6,
                            salaryMin
                    );

                    jobStatement.setDouble(
                            7,
                            salaryMax
                    );

                    jobStatement.setBoolean(
                            8,
                            salaryPredicted
                    );

                    jobStatement.setString(
                            9,
                            contractType
                    );

                    jobStatement.setString(
                            10,
                            contractTime
                    );

                    jobStatement.setString(
                            11,
                            description
                    );

                    jobStatement.setString(
                            12,
                            jobUrl
                    );

                    jobStatement.setString(
                            13,
                            created
                    );

                    jobStatement.setString(
                            14,
                            educationLevel
                    );

                    jobStatement.setString(
                            15,
                            educationRequirement
                    );


                    jobStatement.executeUpdate();

                    jobsProcessed++;


                    // ==================================
                    // SAVE COLLECTION SNAPSHOT
                    // ==================================

                    snapshotStatement.setString(
                            1,
                            id
                    );


                    snapshotStatement.executeUpdate();

                    snapshotsCreated++;


                } catch (Exception e) {

                    jobsSkipped++;

                    System.out.println(
                            "Skipping job "
                                    + (i + 1)
                                    + " because of an error: "
                                    + e.getMessage()
                    );
                }
            }


            // ------------------------------------------
            // COLLECTION SUMMARY
            // ------------------------------------------

            System.out.println();

            System.out.println(
                    "Jobs processed: " + jobsProcessed
            );

            System.out.println(
                    "Snapshots created: " + snapshotsCreated
            );

            System.out.println(
                    "Jobs skipped: " + jobsSkipped
            );
        }
    }


    // ==========================================
    // DETERMINE EDUCATION LEVEL
    // ==========================================

    private static String determineEducationLevel(
            String description) {

        if (description == null || description.isBlank()) {

            return "Not Specified";
        }


        String text =
                description.toLowerCase(Locale.ROOT);


        // Check highest education levels first

        if (text.contains("ph.d")
                || text.contains("phd")
                || text.contains("doctorate")
                || text.contains("doctoral degree")) {

            return "Doctorate";
        }


        if (text.contains("master's degree")
                || text.contains("masters degree")
                || text.contains("master’s degree")
                || text.contains("master's")
                || text.contains("master’s")) {

            return "Master's";
        }


        if (text.contains("bachelor's degree")
                || text.contains("bachelors degree")
                || text.contains("bachelor’s degree")
                || text.contains("bachelor's")
                || text.contains("bachelor’s")) {

            return "Bachelor's";
        }


        if (text.contains("associate's degree")
                || text.contains("associates degree")
                || text.contains("associate’s degree")
                || text.contains("associate's")
                || text.contains("associate’s")) {

            return "Associate";
        }


        if (text.contains("high school diploma")
                || text.contains("high school")
                || text.contains("ged")) {

            return "High School";
        }


        return "Not Specified";
    }


    // ==========================================
    // DETERMINE EDUCATION REQUIREMENT
    // ==========================================

    private static String determineEducationRequirement(
            String description) {

        if (description == null || description.isBlank()) {

            return "Not Specified";
        }


        String text =
                description.toLowerCase(Locale.ROOT);


        boolean required =
                text.contains("degree required")
                        || text.contains("degree is required")
                        || text.contains("required degree")
                        || text.contains("must have")
                        || text.contains("must possess")
                        || text.contains("required qualifications")
                        || text.contains("minimum qualifications");


        boolean preferred =
                text.contains("degree preferred")
                        || text.contains("degree is preferred")
                        || text.contains("preferred degree")
                        || text.contains("preferred qualifications")
                        || text.contains("preferred qualification");


        if (required) {

            return "Required";
        }


        if (preferred) {

            return "Preferred";
        }


        return "Not Specified";
    }
}
