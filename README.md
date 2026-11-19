# LinkSnip - URL Shortener

A simple URL shortener web application built with Java 21 and Spring Boot 3, deployed on Render with Neon PostgreSQL.

## Features

- Shorten long URLs with optional custom alias
- QR code generation for each short link
- Click tracking and statistics
- Link expiry support
- Clean, responsive UI with Bootstrap 5

## Tech Stack

- **Backend:** Java 21, Spring Boot 3.2, Spring Data JPA
- **Frontend:** Thymeleaf, Bootstrap 5
- **Database:** PostgreSQL (Neon)
- **QR Codes:** ZXing library
- **Deployment:** Docker, Render

## Local Development

### Prerequisites
- Java 21
- Maven 3.9+

### Run with H2 (in-memory database)
```bash
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

The app will be available at `http://localhost:8080`.

### Run with Docker
```bash
docker build -t linkSnip .
docker run -p 8080:8080 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://YOUR_HOST/YOUR_DB?sslmode=require \
  -e SPRING_DATASOURCE_USERNAME=YOUR_USERNAME \
  -e SPRING_DATASOURCE_PASSWORD=YOUR_PASSWORD \
  -e APP_BASE_URL=http://localhost:8080 \
  linkSnip
```

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/` | Home page with form and recent links |
| POST | `/shorten` | Create a short URL |
| GET | `/{code}` | Redirect to original URL |
| GET | `/stats/{code}` | View click statistics |
| GET | `/qr/{code}.png` | Get QR code image |

## Environment Variables

| Variable | Description |
|----------|-------------|
| `SPRING_DATASOURCE_URL` | JDBC connection string for PostgreSQL |
| `SPRING_DATASOURCE_USERNAME` | Database username |
| `SPRING_DATASOURCE_PASSWORD` | Database password |
| `APP_BASE_URL` | Public URL of the application |

## Project Structure

```
src/main/java/com/jgu/urlshortener/
├── UrlshortenerApplication.java  # Main entry point
├── UrlMapping.java               # JPA entity
├── UrlRepository.java            # Database repository
├── UrlService.java               # Business logic
├── UrlController.java            # HTTP routes
└── LinkException.java            # Custom exception

src/main/resources/templates/
├── home.html                     # Home page
├── result.html                   # Short URL result
├── stats.html                    # Statistics page
└── error.html                    # Error page
```

## Live Demo

[View Live Demo](https://url-shortner-jg.onrender.com)

> Note: Free tier on Render spins down after 15 minutes of inactivity. First request may take ~30 seconds to wake up.

## License

This project was created as part of an Integrated Project for B.Tech CSE Semester V at JG University, Ahmedabad.
