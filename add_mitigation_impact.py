import os
import requests
import json

# === CONFIGURATION ===
# Set your SonarQube connection details via environment variables or hardcoded for testing
BASE_URL = os.getenv("SONARQUBE_URL", "http://your-sonarqube-url")
USERNAME = os.getenv("SONARQUBE_USERNAME", "admin")
PASSWORD = os.getenv("SONARQUBE_PASSWORD", "your-sonarqube-password")
PROJECT_KEY = os.getenv("SONARQUBE_PROJECT_ID", "sample-project-key")
OUTPUT_FILE = os.getenv("SONAR_REPORT_FILE", "sonar_report.json")

# === VALIDATION ===
# Ensure required parameters are set
if not BASE_URL or not USERNAME or not PASSWORD or not PROJECT_KEY:
    raise ValueError("Missing required configuration for SonarQube connection.")

# === FETCH ISSUES FROM SONARQUBE ===
def fetch_issues():
    """Fetches all issues for the given SonarQube project, with pagination."""
    url = f"{BASE_URL}/api/issues/search"
    params = {"componentKeys": PROJECT_KEY, "ps": 500, "p": 1}
    
    all_issues = []
    first_page_data = None

    while True:
        response = requests.get(url, auth=(USERNAME, PASSWORD), params=params)
        response.raise_for_status()
        issues_data = response.json()

        if first_page_data is None:
            first_page_data = issues_data

        all_issues.extend(issues_data.get("issues", []))

        paging = issues_data.get("paging", {})
        if paging.get("pageIndex", 1) * paging.get("pageSize", 500) >= paging.get("total", 0):
            break  # No more pages

        params["p"] += 1  # Next page

    if first_page_data:
        first_page_data["issues"] = all_issues

    return first_page_data

# === FETCH RULE DETAILS ===
def fetch_rule_details(rule_key):
    """Fetches rule details for a given rule key to extract mitigation and impact info."""
    url = f"{BASE_URL}/api/rules/show"
    params = {"key": rule_key}

    response = requests.get(url, auth=(USERNAME, PASSWORD), params=params)
    response.raise_for_status()
    rule_details = response.json().get("rule", {})

    mitigation = f"""
Rule Key: {rule_details.get('key')}
Name: {rule_details.get('name')}
Severity: {rule_details.get('severity')}
Description: {rule_details.get('htmlDesc', '').strip()}
"""

    impacts = rule_details.get("impacts", [])
    impact_details = ""
    if impacts:
        impact_details = "\n".join(
            [f"Software Quality: {impact.get('softwareQuality', 'N/A')}, Severity: {impact.get('severity', 'N/A')}" for impact in impacts]
        )

    return mitigation.strip(), impact_details.strip()

# === UPDATE ISSUES WITH MITIGATION & IMPACT ===
def update_messages(issues):
    """Updates issue messages with mitigation and impact information from rule details."""
    for issue in issues:
        if "message" in issue:
            mitigation, impact = fetch_rule_details(issue["rule"])
            issue["message"] = f"Mitigation: {mitigation}\nImpact: {impact}\nOriginal: {issue['message']}"
    return issues

# === MAIN FUNCTION ===
def main():
    try:
        print("Fetching issues from SonarQube...")
        data = fetch_issues()
        print(f"Fetched {len(data['issues'])} issues.")

        print("Updating issue messages with mitigation and impact details...")
        data["issues"] = update_messages(data["issues"])

        print(f"Saving updated issues to {OUTPUT_FILE}...")
        with open(OUTPUT_FILE, "w") as f:
            json.dump(data, f, indent=4)

        print(f"Report generated: {OUTPUT_FILE}")

    except Exception as e:
        print(f"An error occurred: {e}")

# === ENTRY POINT ===
if __name__ == "__main__":
    main()
