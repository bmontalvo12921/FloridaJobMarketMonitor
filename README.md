Florida Job Market Monitor

About the Project

Florida Job Market Monitor is a Java application that collects job postings from the Adzuna API and saves them in a MySQL database. It collects information such as job titles, companies, locations, salary ranges, and education requirements.

Technologies

Java 17

Maven

MySQL

Adzuna API

IntelliJ IDEA

Requirements

Before running the project, install:

Java 17 or later

Maven

MySQL Server

An Adzuna API account

Installation

Clone the repository:

git clone https://github.com/bmontalvo12921/FloridaJobMarketMonitor.git
cd FloridaJobMarketMonitor

Database Setup

Create a local database and user in MySQL:

CREATE DATABASE job_market;

CREATE USER 'jobmarket_app'@'localhost'
IDENTIFIED BY 'your_local_password';

GRANT SELECT, INSERT, UPDATE, CREATE, ALTER
ON job_market.*
TO 'jobmarket_app'@'localhost';

FLUSH PRIVILEGES;

Replace your_local_password with a password you choose.

The application creates the jobs and job_snapshots tables when it runs.

Environment Variables

Set the following environment variables before running the collector:

Variable

Purpose

ADZUNA_APP_ID

Your Adzuna application ID

ADZUNA_APP_KEY

Your Adzuna application key

MYSQLUSER

Your local MySQL username

MYSQLPASSWORD

Your local MySQL password

The collector also accepts MYSQL_USER and MYSQL_PASSWORD as alternative names for the database username and password.

The default local database is job_market on localhost:3306. If you use another database server, configure MYSQLHOST, MYSQLPORT, and MYSQLDATABASE.

Windows PowerShell example

$env:ADZUNA_APP_ID = "your_adzuna_app_id"
$env:ADZUNA_APP_KEY = "your_adzuna_app_key"
$env:MYSQLUSER = "jobmarket_app"
$env:MYSQLPASSWORD = "your_local_password"

Use your own credentials. Do not put real API keys or database passwords in source code or commit them to GitHub.

Build and Run

Build the project:

mvn clean package

Run the collector:

java -jar target/florida-job-market-monitor-1.0-SNAPSHOT.jar

Make sure MySQL is running before starting the application.

Database Tables

jobs stores job posting details, including salary and education information.

job_snapshots stores the dates when job postings were collected.

You can view the records through MySQL Workbench or the MySQL command-line client.

Example query:

SELECT title, company, location, salary_min, salary_max,
       education_level, education_requirement
FROM jobs
LIMIT 50;

Collected Data

The repository includes CSV data files that can be opened in Excel or another spreadsheet application. These files can be used to review the collected job information without setting up MySQL first.

Related Project

The companion dashboard is intended to display the collected job data:

https://github.com/bmontalvo12921/Florida-Job-Market-Dashboard

Security

Do not share your API keys, database passwords, or production credentials. Developers should use their own local database credentials when running this project.
