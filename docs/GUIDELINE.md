# 🧩 Backend Coding Guideline (Spring Boot – Modular Monolith)

This document defines the **mandatory architecture rules and coding flow**
for the backend source code.

All backend code must follow this guideline.

---

## 1. Architecture Overview

The backend uses a **modular monolith** structure.

Each module represents a **business feature** and owns its logic and data.
```
auth/
family/
shared/
```

Modules must be **independent** and communicate only through well-defined APIs.

---

## 2. Module Structure (MANDATORY)

Each feature module MUST follow this structure:
```
<module>/
├── controller
├── domain
├── dto
├── repository
└── service
```

Example:
```
family/
├── controller
├── domain
├── dto
├── repository
└── service
```

---

## 3. Layer Responsibilities (Strict Rules)

### 3.1 `controller`
**Purpose:** HTTP layer

Allowed:
- request mapping
- request validation
- calling service methods
- mapping response DTOs

Forbidden:
- business logic
- repository access
- try/catch for business exceptions

Controllers must be **thin**.

---

### 3.2 `service`
**Purpose:** Business logic / use cases

Allowed:
- business rules
- transactions
- calling repositories
- calling other services (same module)
- throwing business exceptions

Forbidden:
- HTTP concerns
- request/response objects
- framework-specific logic (except transactions)

---

### 3.3 `domain`
**Purpose:** Core business model

Contains:
- entities
- value objects
- enums

Rules:
- No framework annotations except JPA
- No business workflows
- No DTOs

---

### 3.4 `repository`
**Purpose:** Data access

Rules:
- One repository per aggregate
- No business logic
- No DTO mapping
- Used only by services

---

### 3.5 `dto`
**Purpose:** API data contracts

Contains:
- request DTOs
- response DTOs

Rules:
- DTOs are module-specific
- Do NOT reuse DTOs across modules
- Do NOT put DTOs in `shared` unless explicitly approved

---

## 4. Shared Module Rules
```
shared/
├── config
├── exception
├── security
└── util
```

### 4.1 `shared/exception`
- Global exception handling
- Base exception classes
- Error response model

Rules:
- Business exceptions are thrown from services
- Controllers do NOT handle exceptions

---

### 4.2 `shared/security`
- Security configuration
- Filters (JWT, authentication)
- Authorization helpers

Rules:
- No business logic
- No feature-specific code

---

### 4.3 `shared/config`
- Application-wide configuration
- Bean definitions
- External service configuration

---

### 4.4 `shared/util`
- Stateless helper utilities only
- No business rules
- No Spring dependencies

---

## 5. Data Flow (MANDATORY)
```
HTTP Request
↓
Controller
↓
Service
↓
Repository
↓
Database
```

Response flows in reverse.

❌ Skipping layers is not allowed.

---

## 6. Exception Handling Rules

- Services throw business exceptions
- Global handler maps exceptions → HTTP responses
- No `try/catch` in controllers (except technical cases)

---

## 7. Cross-Module Rules

- Modules must not access each other’s repositories
- Direct domain access across modules is forbidden
- Shared logic must go to `shared` or be duplicated intentionally

---

## 8. API-First Rules

- All endpoints must appear in Swagger
- Request / response DTOs must be explicit
- Do not expose domain entities directly

---

## 9. Forbidden Patterns

❌ Business logic in controllers  
❌ Repository calls in controllers  
❌ DTOs inside `domain`  
❌ Cross-module repository access  
❌ Catching business exceptions in controllers

---

## 10. Definition of Done (Backend)

Before marking a task complete:
- Correct module used
- Layer responsibilities respected
- DTOs used for API boundaries
- Exceptions handled globally
- Swagger updated automatically

---

## Final Rule

> If another backend developer cannot understand where your code belongs in **5 minutes**, it is not done.

**Clarity > Cleverness.**