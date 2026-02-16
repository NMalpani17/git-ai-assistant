# Git AI Assistant

An AI-powered Git command assistant that converts natural language queries into Git commands using a distributed actor architecture. Built with Spring Boot, Akka Actors, Spring AI (OpenAI), and PostgreSQL with pgvector for RAG-based retrieval.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-green)
![Akka](https://img.shields.io/badge/Akka-2.8-blue)
![React](https://img.shields.io/badge/React-19-61DAFB)

---

## Features

- **Natural Language to Git** — Ask questions like *"How do I undo my last commit?"* and get the exact Git command with explanation
- **Safety Detection** — Dangerous commands (force push, reset --hard) are flagged with warnings and safer alternatives
- **RAG-Enhanced Responses** — 45 Git commands with vector embeddings for context-aware answers via pgvector
- **2-Node Akka Cluster** — Distributed actor system with automatic failover between nodes
- **Concurrent User Support** — Built-in simulation for 5-20 parallel users with performance metrics
- **React Frontend** — Professional chat interface with cluster status monitoring and safety badges

---

## Architecture

```
User Query → React Frontend → REST API
                                  ↓
                          SessionActor (Orchestrator)
                         /        |          \          \
                   RAGActor   LLMActor   SafetyActor   LoggingActor
                     ↓           ↓            ↓             ↓
                 pgvector     OpenAI     Risk Analysis   Audit Log
                (vectors)    (GPT-4o)    (patterns)     (fire-forget)
```

### Actor Communication Patterns

| Actor | Pattern | Purpose |
|-------|---------|---------|
| LoggingActor | **TELL** (fire-and-forget) | Audit logging without blocking |
| RAGActor | **ASK** (request-response) | Vector similarity search |
| LLMActor | **ASK** (request-response) | OpenAI LLM integration |
| SafetyActor | **FORWARD** (preserving sender) | Risk analysis with original sender context |
| SessionActor | **Orchestrator** | Coordinates the full pipeline |

### Supervisor Strategies

| Actor | Strategy | Reason |
|-------|----------|--------|
| LoggingActor | RESUME | Logging errors shouldn't crash the actor |
| LLMActor | RESTART with BACKOFF | External API calls may fail temporarily |
| RAGActor | RESTART with BACKOFF | Database calls may fail temporarily |
| SafetyActor | RESTART | Stateless, safe to restart immediately |
| SessionActor | RESTART | Orchestrator, stateful but can recover |

---

## Tech Stack

**Backend:** Spring Boot 3.2, Akka Typed 2.8, Spring AI 1.0 (OpenAI), PostgreSQL + pgvector, Java 17

**Frontend:** React 19, Tailwind CSS, Lucide Icons, Vite

**Infrastructure:** Docker (PostgreSQL), 2-node Akka Cluster

---

## Prerequisites

- Java 17+
- Maven
- Node.js 18+
- Docker
- OpenAI API Key

---

## Getting Started

### 1. Clone the repository

```bash
git clone https://github.com/NMalpani17/git-ai-assistant.git
cd git-ai-assistant
```

### 2. Set up environment variables

Create `server/.env` file:

```
OPENAI_API_KEY=your_openai_api_key_here
```

### 3. Start PostgreSQL (Docker)

```bash
docker-compose up -d
```

Wait ~10-15 seconds for the database to be ready.

### 4. Start Backend — Node 1

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=node1
```

Node 1 runs on **HTTP: 8080** | **Akka: 2551**

### 5. Start Backend — Node 2 (new terminal)

```bash
cd server
mvn spring-boot:run -Dspring-boot.run.profiles=node2
```

Node 2 runs on **HTTP: 8081** | **Akka: 2552**

### 6. Start Frontend (new terminal)

```bash
cd client
npm install
npm run dev
```

Frontend runs on **http://localhost:5173**

---

## Usage

Open `http://localhost:5173` and try queries like:

- *"How do I undo my last commit?"*
- *"Create a new branch called feature-login"*
- *"I need to force push my changes"* (triggers safety warning)
- *"How to resolve merge conflicts?"*
- *"What is git rebase?"*

The response includes the Git command, explanation, safety level (SAFE / CAUTION / DANGEROUS), and alternatives for risky commands.

---

## API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/git/ask` | Full pipeline: RAG → LLM → Safety |
| GET | `/api/git/ask?q=...` | Full pipeline (GET) |
| GET | `/api/git/search?q=...` | RAG vector search only |
| GET | `/api/git/safety?command=...` | Safety check only |
| GET | `/api/cluster/status` | Cluster status and members |
| GET | `/api/cluster/health` | Cluster health check |
| POST | `/api/simulation/run?users=5&requests=3` | Run concurrent user simulation |
| GET | `/api/metrics` | Application metrics |

---

## Concurrent User Simulation

The system includes a built-in load testing simulator accessible via the `/api/simulation/run` endpoint. It fires parallel requests through the full actor pipeline and returns performance stats including throughput, latency percentiles (p50/p90/p99), success rates, and per-user breakdowns.

---

## Project Structure

```
git-ai-assistant/
├── client/                          # React frontend
│   └── src/
│       ├── App.jsx                  # Main app with failover logic
│       └── components/
│           ├── Header.jsx           # Cluster status display
│           ├── ChatArea.jsx         # Message display
│           ├── InputArea.jsx        # Query input
│           ├── AssistantResponse.jsx # Response with safety badges
│           ├── MessageBubble.jsx    # User message bubble
│           └── QuickButtons.jsx     # Quick query suggestions
├── server/                          # Spring Boot backend
│   └── src/main/java/com/gitassistant/
│       ├── actors/
│       │   ├── SessionActor.java    # Orchestrator
│       │   ├── RAGActor.java        # Vector search (ASK)
│       │   ├── LLMActor.java        # OpenAI integration (ASK)
│       │   ├── SafetyActor.java     # Risk detection (FORWARD)
│       │   └── LoggingActor.java    # Audit logging (TELL)
│       ├── config/
│       │   ├── AkkaConfig.java      # Actor system + supervisors
│       │   ├── CorsConfig.java      # CORS for frontend
│       │   └── JpaConfig.java       # JPA transaction manager
│       ├── controllers/
│       │   ├── GitController.java   # Main REST API
│       │   ├── ClusterController.java
│       │   ├── HealthController.java
│       │   ├── SimulationController.java
│       │   └── MetricsController.java
│       ├── messages/                # Akka message protocols
│       ├── models/
│       │   └── GitCommand.java      # JPA entity with pgvector
│       ├── repositories/
│       │   └── GitCommandRepository.java
│       └── services/
│           ├── RAGService.java      # Vector similarity search
│           ├── EmbeddingService.java # OpenAI embeddings
│           ├── DataLoaderService.java # 45 Git commands dataset
│           ├── ConcurrentUserSimulator.java
│           └── MetricsService.java
└── docker-compose.yml               # PostgreSQL + pgvector
```

---

## Cluster Failover

The frontend automatically falls back from Node 1 to Node 2 if Node 1 is unavailable. The header shows real-time cluster status with node health indicators and leader election.

---

## Author

**Niraj Malpani**