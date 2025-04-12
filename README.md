# devsecops-pipelines
Jenkinsfiles for a DevSecOps CI/CD Pipeline


This project contains a sample Jenkinsfile for a DevSecOps CI/CD pipeline integrating SonarQube, Trivy, OWASP Dependency-Check, and DefectDojo for automated code quality and security scanning.

📌 Pipeline Overview

The pipeline automates the following steps:

    Clean Workspace
    Clears the Jenkins build directory to ensure no residual files remain from previous builds.

    Clone Source Code
    Fetches source code from a Git repository.

    Static Code Analysis via SonarQube
    Runs SonarQube analysis to identify code quality issues and security vulnerabilities.

    Download Python Script for Report Enhancement
    Retrieves a custom Python script from a GitLab repository to add mitigation and impact fields to the SonarQube report.

    Generate and Enhance SonarQube JSON Report
    Uses the downloaded Python script to query SonarQube API and save an enhanced report with additional context.

    Run OWASP Dependency-Check
    Performs a dependency vulnerability scan against project dependencies.

    Run Trivy Security Scan
    Scans the codebase for vulnerabilities, misconfigurations, and secrets using Trivy.

    Upload All Reports to DefectDojo
    Sends all generated reports to DefectDojo for centralized vulnerability management.
