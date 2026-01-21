
# Setting Up the Wave Backend on Google Cloud

This guide provides a high-level overview of the steps required to set up the backend for your Wave messaging application on Google Cloud Platform (GCP). The Wave backend is based on the Signal Server, which is a complex system with multiple components.

**Disclaimer:** This is a challenging task that requires expertise in backend development, cloud infrastructure, and security. This guide is intended to point you in the right direction, but you will need to refer to the official documentation for both Signal Server and Google Cloud for detailed instructions.

## 1. Prerequisites

Before you begin, you will need:

*   **A Google Cloud Platform Account:** With billing enabled.
*   **A Domain Name:** You should have already purchased `wave.org` or a similar domain.
*   **The Signal Server Source Code:** You can find it on the official Signal GitHub repository. You will need to download or clone this code to your local machine.

## 2. Backend Architecture Overview

The Signal/Wave backend is not a single application but a collection of microservices that work together. The key components you will need to set up include:

*   **Signal Server:** The main application server that handles user registration, message routing, and client connections.
*   **PostgreSQL Database:** Used to store user account data, contacts, and other relational information.
*   **Redis Cache:** Used for caching and temporary data storage.
*   **Attachment/Storage Service:** For handling file uploads and downloads (pictures, videos, etc.).
*   **Push Notification Service Integration:** To send push notifications to Android devices (via FCM) and iOS devices (via APNS).

## 3. Step-by-Step Setup Guide on Google Cloud

### Step 3.1: Set Up Your Google Cloud Project

1.  **Create a New Project:** Go to the [Google Cloud Console](https://console.cloud.google.com/) and create a new project for your Wave backend.
2.  **Enable APIs:** You will need to enable several APIs for your project. The most important ones are:
    *   Compute Engine API
    *   Cloud SQL Admin API
    *   Virtual Private Cloud (VPC) Network API

### Step 3.2: Configure Networking

1.  **Create a VPC Network:** It's a good practice to create a dedicated Virtual Private Cloud (VPC) network for your backend to isolate it from other projects.
2.  **Firewall Rules:** Create firewall rules to control traffic to your servers. You will need to open ports for HTTPS (443), and other ports required by the Signal Server.

### Step 3.3: Provision Databases

1.  **Create a Cloud SQL for PostgreSQL Instance:**
    *   Navigate to the Cloud SQL section in the GCP console.
    *   Create a new PostgreSQL instance. Choose an appropriate machine type and storage size.
    *   Set a strong password for the `postgres` user. **Store this securely.**
    *   Create a database within this instance for the Signal Server.
2.  **Create a Memorystore for Redis Instance:**
    *   Navigate to the Memorystore section.
    *   Create a new Redis instance.

### Step 3.4: Provision Virtual Machines (Compute Engine)

You will need at least one VM to run the Signal Server. For a production environment, you would likely have multiple VMs for different services and for redundancy.

1.  **Create a VM Instance:**
    *   Go to the Compute Engine section and create a new VM instance.
    *   Choose a suitable machine type (e.g., `e2-medium` to start).
    *   Select an operating system, such as Ubuntu or Debian.
2.  **Install Dependencies:** Connect to your VM via SSH and install necessary software, including:
    *   Java (the Signal Server is a Java application)
    *   Build tools (like Gradle or Maven, depending on the Signal Server build process)
    *   The `psql` client to connect to your database.

### Step 3.5: Configure and Deploy the Server Code

This is the most involved step. You will need to adapt the Signal Server source code to your "Wave" brand and configuration.

1.  **Modify Configuration Files:** The Signal Server code will have configuration files (often in YAML or properties files) where you need to input your specific details:
    *   **Database Connection:** The IP address of your Cloud SQL instance, the database name, username, and password.
    *   **Redis Connection:** The IP address of your Redis instance.
    *   **Domain Names:** Change all references from `signal.org` to `wave.org` and its subdomains (`chat.wave.org`, `storage.wave.org`, etc.).
    *   **Security Keys and Secrets:** Generate new random keys and secrets for signing tokens, encrypting data, etc.
2.  **Build the Server:** Use the provided build scripts in the Signal Server repository to compile the code into executable JAR files.
3.  **Deploy to VM:** Copy the built JAR files to your VM and run them. You will likely want to create a service (using `systemd`) to ensure the server runs automatically and restarts on failure.

### Step 3.6: Configure DNS

1.  **Get Your VM's External IP:** Find the external IP address of your VM or your Google Cloud Load Balancer.
2.  **Create DNS Records:** Go to your domain registrar's DNS management panel and create `A` records to point your subdomains (`chat.wave.org`, `storage.wave.org`, etc.) to the IP address from the previous step.

### Step 3.7: Set Up TLS/SSL

1.  **Install a Web Server/Proxy:** It's best practice to run a web server like Nginx in front of your Java application to handle incoming HTTPS traffic.
2.  **Obtain TLS Certificates:** Use a tool like `certbot` to get free TLS certificates from Let's Encrypt for all your subdomains.
3.  **Configure Nginx:** Configure Nginx as a reverse proxy to pass traffic to your running Signal Server application.

## 4. Final Steps and Maintenance

*   **Testing:** Thoroughly test your setup. Try to register a new user from your Wave Android app, send messages, and make sure everything works as expected.
*   **Monitoring and Logging:** Set up monitoring and logging for your servers and application using Google Cloud's operations suite (formerly Stackdriver) to track performance and diagnose issues.
*   **Updates:** Keep your server software, operating system, and all dependencies updated to protect against security vulnerabilities.

This guide is a starting point. You will encounter many specific challenges and details along the way. Be prepared to do a lot of reading in the Signal Server and Google Cloud documentation.
