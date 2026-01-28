# 👋 Welcome to Familier

**Familier** is a platform designed to help strengthen intimacy in modern digital families by using **Generative AI** as a supportive tool, not a replacement for real human connection.

The goal of Familier is to **encourage meaningful interaction**, not empty or automated communication.

---

## 🏗️ Project Overview

This repository contains the **backend service** of the Familier system.

**Tech stack:**
- Java
- Spring Boot
- Modular Monolith Architecture
- RESTful API
- OpenAPI (Swagger)
- (Planned) Gemini API integration

---

## 🚀 Getting Started

### Prerequisites
- Java 17+
- Maven

### Installation & Run

Clone the repository and start the application:

```bash
git clone https://github.com/pnv-familier/backend
cd backend
mvn spring-boot:run
```
### 🔍 Health Check
After the application starts, verify it is running correctly:

Health endpoint:
http://localhost:8080/actuator/health

### 📄 API Documentation
Swagger UI (API-first approach):

http://localhost:8080/swagger-ui.html

### 📂 Project Structure
The backend follows a modular monolith architecture:
```
module/
shared/   → cross-cutting concerns (security, exception, config)
```


### 📌 Development Notes
- Business logic lives in the service layer

- Controllers are thin and handle HTTP concerns only

- All exceptions are handled globally

- External services (e.g. AI APIs) are isolated behind adapters

### For detailed rules, see:

[BACKEND_GUIDELINE.md](docs/GUIDELINE.md)

[CONTRIBUTING.md](https://github.com/pnv-familier/conventions/blob/main/github/CONTRIBUTING.md)