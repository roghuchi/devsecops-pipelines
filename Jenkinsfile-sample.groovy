// Sample Jenkinsfile for a DevSecOps CI/CD Pipeline
// Tools used: SonarQube, Trivy, OWASP Dependency-Check, DefectDojo
// Each stage is annotated with purpose and description

pipeline {
    agent { label 'your-node-label' }

    environment {
        // SCM & Project Configuration
        GIT_REPO_URL = 'https://your.git.repo/sample-project.git'

        // SonarQube Configuration
        SONARQUBE_URL = 'http://your-sonarqube-url'
        SONARQUBE_AUTH_TOKEN = credentials('sonar-token-id')
        SONARQUBE_PROJECT_ID = 'sample-sonar-project'
        SONAR_SCANNER_PATH = '/opt/sonar/sonar-scanner/bin/sonar-scanner'
        SONAR_REPORT_FILE = 'sonar_report.json'
        SONARQUBE_USERNAME = 'your-sonarqube-username'
        SONARQUBE_PASSWORD = credentials('sonar-password-id')

        // DefectDojo Configuration
        DEFECTDOJO_API_KEY = credentials('defectdojo-api-key-id')
        DEFECTDOJO_URL = 'http://your-defectdojo-url/api/v2'
        DEFECTDOJO_TEST_ID = '1'
        DEFECTDOJO_SCAN_TYPE = 'SonarQube Scan'
        DEFECTDOJO_TAGS = 'Security'
        DEFECTDOJO_PRODUCT_NAME = 'sample-product'
        DEFECTDOJO_ENGAGEMENT_NAME = 'ci/cd'

        // Dependency-Check Configuration
        DC_REPORT_FILE = 'dependency-check-report.xml'
        DC_DEFECTDOJO_SCAN_TYPE = 'Dependency Check Scan'

        // Trivy Configuration
        TRIVY_REPORT_FILE = 'trivy_report.json'
        TRIVY_SCAN_TYPE = 'Trivy Scan'

        // GitLab-hosted Python script for SonarQube report enhancement
        GITLAB_FILE_URL = 'https://your.gitlab/api/v4/projects/1/repository/files/sample%2Fadd_mitigation_impact.py/raw?ref=main'
    }

    stages {
        stage('Clean Workspace') {
            steps {
                echo "Cleaning up Jenkins workspace to ensure a fresh build..."
                cleanWs()
            }
        }

        stage('Clone Source Repository') {
            steps {
                echo "Cloning source code from Git repository..."
                checkout([$class: 'GitSCM',
                    branches: [[name: '*/main']],
                    userRemoteConfigs: [[url: "${GIT_REPO_URL}", credentialsId: 'your-git-cred-id']]
                ])
            }
        }

        stage('SonarQube Analyze') {
            steps {
                echo "Running static code analysis using SonarQube..."
                script {
                    withSonarQubeEnv('SonarQube') {
                        sh """
                        ${SONAR_SCANNER_PATH} \\
                            -Dsonar.projectKey=${SONARQUBE_PROJECT_ID} \\
                            -Dsonar.host.url=${SONARQUBE_URL} \\
                            -Dsonar.sources=. \\
                            -Dsonar.login=${SONARQUBE_AUTH_TOKEN}
                        """
                    }
                }
            }
        }

        stage('Download & Prepare SonarQube Report Enhancer Script') {
            steps {
                echo "Downloading Python script to enhance SonarQube report with mitigation and impact fields..."
                script {
                    withCredentials([string(credentialsId: 'your-gitlab-token-id', variable: 'GITLAB_TOKEN')]) {
                        def response = httpRequest(
                            url: GITLAB_FILE_URL,
                            customHeaders: [[name: 'PRIVATE-TOKEN', value: "${GITLAB_TOKEN}"]],
                            validResponseCodes: '200'
                        )
                        writeFile file: 'add_mitigation_impact.py', text: response.content
                        echo "Script saved as: add_mitigation_impact.py"
                    }
                }
            }
        }

        stage('Download and Enhance SonarQube Report') {
            steps {
                echo "Running the downloaded Python script to fetch and enhance the SonarQube report..."
                script {
                    sh """
                    export SONARQUBE_URL=${SONARQUBE_URL}
                    export SONARQUBE_USERNAME=${SONARQUBE_USERNAME}
                    export SONARQUBE_PASSWORD=${SONARQUBE_PASSWORD}
                    export SONARQUBE_PROJECT_ID=${SONARQUBE_PROJECT_ID}
                    export SONAR_REPORT_FILE=${SONAR_REPORT_FILE}
                    python3 add_mitigation_impact.py
                    """
                    if (!fileExists(SONAR_REPORT_FILE)) {
                        error("SonarQube report not found after enhancement script ran.")
                    }
                    echo "Enhanced SonarQube JSON report generated: ${SONAR_REPORT_FILE}"
                }
            }
        }

        stage('Run OWASP Dependency-Check') {
            steps {
                echo "Performing dependency vulnerability scan with OWASP Dependency-Check..."
                sh """
                /opt/dependency-check/bin/dependency-check.sh -n \\
                    --project '${SONARQUBE_PROJECT_ID}' \\
                    --scan . \\
                    --out ./${DC_REPORT_FILE} \\
                    --format XML
                """
            }
        }

        stage('Run Trivy Scan') {
            steps {
                echo "Running Trivy to scan source files for vulnerabilities, misconfigurations, and secrets..."
                sh """
                trivy fs --scanners vuln,secret,misconfig --skip-db-update -f json -o ${TRIVY_REPORT_FILE} .
                """
                if (!fileExists(TRIVY_REPORT_FILE)) {
                    error("Trivy report not found.")
                }
                echo "Trivy scan completed successfully: ${TRIVY_REPORT_FILE}"
            }
        }

        stage('Upload Trivy Report to DefectDojo') {
            steps {
                echo "Uploading Trivy report to DefectDojo..."
                script {
                    try {
                        sh """
                        curl -X POST \\
                          '${DEFECTDOJO_URL}/import-scan/' \\
                          -H 'Authorization: Token ${DEFECTDOJO_API_KEY}' \\
                          -F 'test=${DEFECTDOJO_TEST_ID}' \\
                          -F 'file=@${TRIVY_REPORT_FILE};type=application/json' \\
                          -F 'scan_type=${TRIVY_SCAN_TYPE}' \\
                          -F 'tags=${DEFECTDOJO_TAGS}' \\
                          -F 'product_name=${DEFECTDOJO_PRODUCT_NAME}' \\
                          -F 'engagement_name=${DEFECTDOJO_ENGAGEMENT_NAME}'
                        """
                        echo "Trivy report uploaded successfully."
                    } catch (e) {
                        echo "Failed to upload Trivy report: ${e.getMessage()}"
                        error("Aborting pipeline due to upload failure.")
                    }
                }
            }
        }

        stage('Upload SonarQube Report to DefectDojo') {
            steps {
                echo "Uploading enhanced SonarQube report to DefectDojo..."
                script {
                    try {
                        sh """
                        curl -X POST \\
                          '${DEFECTDOJO_URL}/import-scan/' \\
                          -H 'Authorization: Token ${DEFECTDOJO_API_KEY}' \\
                          -F 'test=${DEFECTDOJO_TEST_ID}' \\
                          -F 'file=@${SONAR_REPORT_FILE};type=application/json' \\
                          -F 'scan_type=${DEFECTDOJO_SCAN_TYPE}' \\
                          -F 'tags=${DEFECTDOJO_TAGS}' \\
                          -F 'product_name=${DEFECTDOJO_PRODUCT_NAME}' \\
                          -F 'engagement_name=${DEFECTDOJO_ENGAGEMENT_NAME}'
                        """
                        echo "SonarQube report uploaded successfully."
                    } catch (e) {
                        echo "Failed to upload SonarQube report: ${e.getMessage()}"
                        error("Aborting pipeline due to upload failure.")
                    }
                }
            }
        }

        stage('Upload Dependency-Check Report to DefectDojo') {
            steps {
                echo "Uploading Dependency-Check report to DefectDojo..."
                script {
                    try {
                        sh """
                        curl -X POST \\
                          '${DEFECTDOJO_URL}/import-scan/' \\
                          -H 'Authorization: Token ${DEFECTDOJO_API_KEY}' \\
                          -F 'test=${DEFECTDOJO_TEST_ID}' \\
                          -F 'file=@${DC_REPORT_FILE};type=application/xml' \\
                          -F 'scan_type=${DC_DEFECTDOJO_SCAN_TYPE}' \\
                          -F 'tags=${DEFECTDOJO_TAGS}' \\
                          -F 'product_name=${DEFECTDOJO_PRODUCT_NAME}' \\
                          -F 'engagement_name=${DEFECTDOJO_ENGAGEMENT_NAME}'
                        """
                        echo "Dependency-Check report uploaded successfully."
                    } catch (e) {
                        echo "Failed to upload Dependency-Check report: ${e.getMessage()}"
                        error("Aborting pipeline due to upload failure.")
                    }
                }
            }
        }
    }

    post {
        always {
            echo "Pipeline execution finished."
        }
        success {
            echo "Build and scans completed successfully!"
        }
        failure {
            echo "Pipeline failed. Please review logs for details."
        }
    }
}
