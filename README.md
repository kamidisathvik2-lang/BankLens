<<<<<<< HEAD
<<<<<<< HEAD
# BankLens — Production Bank Statement Analyzer

AI-powered bank statement analysis built with React + Spring Boot + PostgreSQL + Redis + AWS.

## Architecture

```
React (Vercel/S3)  →  Spring Boot API (ECS Fargate)
                            ↓              ↓
                       PostgreSQL      Redis
                         (RDS)    (ElastiCache)
                            ↓
                      Anthropic API
```

## Tech Stack

**Backend:** Java 17, Spring Boot 3.2, Spring Security, JPA, Redis, Apache PDFBox, JWT  
**Frontend:** React 19, Context API  
**Database:** PostgreSQL 15  
**Cache:** Redis 7  
**Infra:** Docker, AWS ECS Fargate, RDS, ElastiCache, ECR, S3, CloudFront  
**CI/CD:** GitHub Actions

## Quick Start (Local)

### Prerequisites
- Docker + Docker Compose
- Java 17
- Node 20
- An Anthropic API key

### Run everything with Docker Compose

```bash
# Clone the repo
git clone https://github.com/YOUR_USERNAME/banklens.git
cd banklens

# Set your Anthropic key
export ANTHROPIC_API_KEY=sk-ant-...

# Start all services
docker-compose up --build
```

Frontend: http://localhost:3000  
Backend: http://localhost:8080  
API docs: http://localhost:8080/actuator/health

### Run backend locally (without Docker)

```bash
cd backend

# Start PostgreSQL and Redis only
docker-compose up postgres redis -d

# Run Spring Boot
ANTHROPIC_API_KEY=sk-ant-... mvn spring-boot:run
```

### Run frontend locally

```bash
cd frontend
npm install
REACT_APP_API_URL=http://localhost:8080 npm start
```

## API Reference

```
POST /api/auth/register    { email, password }  → { token, email, userId }
POST /api/auth/login       { email, password }  → { token, email, userId }

POST /api/analyze          multipart/form-data: file  → analysis JSON
GET  /api/history          ?page=0&size=10       → { items, totalPages }
GET  /api/history/:id                            → analysis JSON
POST /api/history/:id/chat { question }          → { answer }
POST /api/history/:id/explain { transaction }    → { explanation }
GET  /api/rate-limit                             → { remaining }
```

All endpoints except `/api/auth/**` require `Authorization: Bearer <token>` header.

## Running Tests

```bash
cd backend
mvn test
```

Test coverage includes:
- `AuthControllerTest` — register, login, validation, auth errors
- `JwtServiceTest` — token generation, validation, expiry
- `AnalysisServiceTest` — happy path, rate limit, PDF failure, JSON parse failure
- `PdfServiceTest` — file validation, size limits, content type checks
- `RateLimitServiceTest` — limit enforcement, TTL, remaining count

## Environment Variables

| Variable | Description | Required |
|---|---|---|
| `ANTHROPIC_API_KEY` | Anthropic API key | Yes |
| `DATABASE_URL` | PostgreSQL JDBC URL | Yes |
| `DATABASE_USERNAME` | DB username | Yes |
| `DATABASE_PASSWORD` | DB password | Yes |
| `REDIS_HOST` | Redis hostname | Yes |
| `REDIS_PORT` | Redis port (default: 6379) | No |
| `JWT_SECRET` | 64-char hex secret for JWT signing | Yes |
| `CORS_ORIGINS` | Comma-separated allowed origins | Yes |

## Deploy to AWS

See `.github/workflows/deploy.yml` for the full CI/CD pipeline.

Required GitHub Secrets:
- `AWS_ACCESS_KEY_ID`
- `AWS_SECRET_ACCESS_KEY`

AWS resources to create:
1. ECR repository: `banklens-backend`
2. ECS cluster: `banklens-cluster`
3. ECS service + task definition: `banklens-service` / `banklens`
4. RDS PostgreSQL instance
5. ElastiCache Redis cluster
6. Secrets Manager entries for all env vars
=======
# BankLens
>>>>>>> d33963707a83a6f2d261e2207b5497ace274369b
=======
# BankLens
>>>>>>> 8bce45ce596eac6888c14b1ea84d6f025f7facf6
