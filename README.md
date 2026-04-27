# Family Messenger Backend

Real-time family messaging backend built with Spring Boot.

## 🛠 Tech Stack

- Java 17
- Spring Boot 3.5.14
- Spring WebSocket (STOMP)
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Redis
- MinIO (file storage)
- Docker

## 🚀 Features

- ✅ User authentication (JWT)
- ✅ Real-time messaging (WebSocket)
- ✅ Chat rooms
- ✅ File uploads (images, voice messages)
- ✅ Message history
- 🔄 Push notifications (coming soon)

## 📋 Prerequisites

- Docker & Docker Compose
- Java 17
- Gradle 8.5+

## 📁 Project Structure

src/main/java/com/familymessenger/backend/
- ├── config/        # Spring configurations
- ├── controller/    # REST & WebSocket controllers
- ├── dto/          # Data transfer objects
- ├── entity/       # JPA entities
- ├── repository/   # Data repositories
- ├── security/     # JWT & Security config
- └── service/      # Business logic

## 🔗 API Endpoints

- Method	Endpoint			Description
- POST	/api/auth/register	User registration
- POST	/api/auth/login		User login
- GET	/api/users/me		Get current user
- WS	/ws			WebSocket connection

## 🗄️ Environment Variables

- Create application-local.properties:
- jwt.secret=your_secret_key
- jwt.expiration=86400000

## 📄 License 

MIT

## 👨‍💻 Author

Mykhailo Vorontsov

## 🔧 Quick Start

```bash
# Clone repository
git clone https://github.com/michw85/family-messenger-backend.git
cd family-messenger-backend

# Start services (PostgreSQL, Redis, MinIO)
docker compose up -d

# Run application
./gradlew bootRun

