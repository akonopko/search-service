# Nevis Search Service

A high-performance search service leveraging PostgreSQL's `pg_trgm` for fuzzy client matching and Gemini AI for document summarization and semantic indexing.

## 1. How to Build
The project uses Maven for dependency management and Docker Compose for the database lifecycle.

### 1.1. Prerequisites
Java: JDK 21+

Maven: 3.6+

Docker: (Docker Engine and Docker Compose)

### 1.2. Compilation and Packaging
To compile the project, run tests, and create the executable JAR file, run:

```mvn clean package```

The executable JAR file will be located in the target/ directory.

## 2. How to Start the Application
The service requires a running PostgreSQL database. We use Docker Compose to set up the isolated environment.

### 2.1. Build docker image and start
From the project root directory, run:

Application requires Gemini API key to run. This key should be provided as environment variable

```APP_GEMINI_API_KEY={your_key} docker-compose up --build```

It will create environment and start dockerized application

### 2.2. Accessing Documentation (Swagger UI)
Once running, the application is accessible at http://localhost:8080.

The interactive API documentation (Swagger UI) is available at: http://localhost:8080/swagger-ui/index.html

### 3. API usage

#### Client Creation

The following commands initialize the database with various client profiles.

```
curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients -H "Content-Type: application/json" -d '{
   "first_name": "John",
   "last_name": "Doe",
   "email": "john.doe@techcorp.com",
   "description": "Senior Software Architect specializing in Java and Spring Boot. Loves hiking.",
   "social_links": ["https://linkedin.com/in/johndoe", "https://github.com/johndoe"]
}'

curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients -H "Content-Type: application/json" -d '{
    "first_name": "Alice",
    "last_name": "Smith",
    "email": "asmith@legal-advice.com",
    "description": "Legal consultant expert in intellectual property and copyright law.",
    "links": []
}'

curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients -H "Content-Type: application/json" -d '{
    "first_name": "Alice",
    "last_name": "Konopko",
    "email": "hello@kitty.com",
    "description": "Pop artist in South Korea.",
    "links": []
}'

```

#### Error Handling & Validation

Duplicate Email Prevention. Attempts to create a client with an existing email will fail.
```
curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients -H "Content-Type: application/json" -d '{
   "first_name": "John",
   "last_name": "Doe",
   "email": "john.doe@techcorp.com",
   "description": "Duplicate email test.",
   "links": []
}'
```

Returns 400 on malformed query (too short)

```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q="
```


Returns 401 on wrong credentials

```
curl -u nevis_admin:nocredsthistime "http://localhost:8080/search?q=somequery"
```

#### Search API

Finds 1 exact match:

input:
```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=techcorp.com"
```

output:
```{
  "clientMatches": [
    {
      "client_id": "3d759c9d-fc5d-4bec-b3d5-c06128bc23e2",
      "first_name": "John",
      "last_name": "Doe",
      "email": "john.doe@techcorp.com",
      "description": "Senior Software Architect specializing in Java and Spring Boot. Loves hiking.",
      "score": 1.0,
      "social_links": [
        "https://linkedin.com/in/johndoe",
        "https://github.com/johndoe"
      ],
      "created_at": "2026-01-06T14:45:04.482503Z"
    }
  ],
  "clientSuggestions": [],
  "documents": []
}
```


Finds 2 exact matches (Alice Konopko and Alice Smith):

input:
```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=Alice"  

```

output:

```
{
  "clientMatches": [
    {
      "client_id": "198dee60-2248-45a0-8bab-5726f9852638",
      "first_name": "Alice",
      "last_name": "Smith",
      "email": "asmith@legal-advice.com",
      "description": "Legal consultant expert in intellectual property and copyright law.",
      "score": 1.0,
      "social_links": [],
      "created_at": "2026-01-06T14:50:27.449738Z"
    },
    {
      "client_id": "495513a6-748c-4925-bf04-3f7a76875317",
      "first_name": "Alice",
      "last_name": "Konopko",
      "email": "hello@kitty.com",
      "description": "Pop artist in South Korea.",
      "score": 1.0,
      "social_links": [],
      "created_at": "2026-01-06T14:50:31.137127Z"
    }
  ],
  "clientSuggestions": [],
  "documents": []
}
```

Finds 1 fuzzy result (Alice Konopko) with score=0.58

input:

```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=Alice%20Artist"
```

output:
```
{
  "clientMatches": [],
  "clientSuggestions": [
    {
      "client_id": "495513a6-748c-4925-bf04-3f7a76875317",
      "first_name": "Alice",
      "last_name": "Konopko",
      "email": "hello@kitty.com",
      "description": "Pop artist in South Korea.",
      "score": 0.5833333,
      "social_links": [],
      "created_at": "2026-01-06T14:50:31.137127Z"
    }
  ],
  "documents": []
}
```


Finds 1 fuzzy result (Alice Smith) with score=0.6

input:
```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=Alice%20legal"
```

output:

```
{
  "clientMatches": [],
  "clientSuggestions": [
    {
      "client_id": "198dee60-2248-45a0-8bab-5726f9852638",
      "first_name": "Alice",
      "last_name": "Smith",
      "email": "asmith@legal-advice.com",
      "description": "Legal consultant expert in intellectual property and copyright law.",
      "score": 0.6000000238418579,
      "social_links": [],
      "created_at": "2026-01-06T14:50:27.449738Z"
    }
  ],
  "documents": []
}
```



Finds 1 fuzzy result for Alice Smith with typo. Score=0.76

input:

```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=Alice%20Smit"
```

output:

```
{
  "clientMatches": [
    {
      "client_id": "198dee60-2248-45a0-8bab-5726f9852638",
      "first_name": "Alice",
      "last_name": "Smith",
      "email": "asmith@legal-advice.com",
      "description": "Legal consultant expert in intellectual property and copyright law.",
      "score": 0.7692307829856873,
      "social_links": [],
      "created_at": "2026-01-06T14:50:27.449738Z"
    }
  ],
  "clientSuggestions": [],
  "documents": []
}
```



Uploads a document with client_id provided (file is attached)

input:

```
curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients/{client_id of John Doe}/documents -H "Content-Type: application/json" --data-binary "@bill.json"
```


Finds uploaded document:

input:

```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=proof%20of%20address"
```

output:

```
{
  "clientMatches": [],
  "clientSuggestions": [],
  "documents": [
    {
      "document_id": "f8cad262-5ec7-4131-8256-ce0123576204",
      "client_id": "3d759c9d-fc5d-4bec-b3d5-c06128bc23e2",
      "title": "Utility Bill - January 2026 - Alex P. Henderson",
      "score": 0.9803373317833921,
      "summary": "This document is a residential multi-utility statement covering electricity, water, gas, and waste management services for a single billing cycle. It includes detailed consumption metrics, itemized service charges, and a historical usage analysis for the property.",
      "status": "READY",
      "created_at": "2026-01-06T14:54:24.173987Z"
    }
  ]
}
```


Uploads a document with another client_id

input:


```
curl -i -u nevis_admin:secret_pass_2026 -X POST http://localhost:8080/clients/{client_id of Alice Smith}/documents -H "Content-Type: application/json" --data-binary "@strategy.json"
```


Finds uploaded document:

input:
```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=strategy"
```


output:

```
{
  "clientMatches": [],
  "clientSuggestions": [],
  "documents": [
    {
      "document_id": "d11d4afe-8a93-4038-8a8f-e1669a52fe14",
      "client_id": "198dee60-2248-45a0-8bab-5726f9852638",
      "title": "ANNUAL STRATEGY & REBALANCE MANDATE - APEX QUANTITATIVE STRATEGIES GROUP",
      "score": 0.7292200402149958,
      "summary": "This annual mandate outlines a strategic rebalance for a volatility stacking portfolio that overlays core asset exposure with capital-efficient alpha streams to maintain a specific annualized volatility target. The update prioritizes increasing trend-following and tail-risk protection while reducing carry exposure to address shifting macroeconomic conditions and preserve the portfolio's risk-adjusted performance.",
      "status": "READY",
      "created_at": "2026-01-06T14:56:07.617747Z"
    }
  ]
}
```


Filters found documents by client_id:

```
curl -u nevis_admin:secret_pass_2026 "http://localhost:8080/search?q=strategy&client_id={client_id of John Doe}
```

output:

```
{
  "clientMatches": [],
  "clientSuggestions": [],
  "documents": []
}
```