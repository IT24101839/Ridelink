# Fare Payment Service

Spring Boot service for fare calculation and payment records.

## Run
From the `RideLink` directory:

```powershell
mvn spring-boot:run -pl fare-payment-service
```

The service runs on `http://localhost:8083`.

## Endpoints

### Health check
```http
GET /api/fare-payment/health
```

### Calculate a fare
```http
POST /api/fare-payment/fares/calculate
Content-Type: application/json

{
  "rideId": 1,
  "distanceKm": 8.5,
  "durationMinutes": 20
}
```

Fare formula:

```text
100.00 + (distanceKm * 80.00) + (durationMinutes * 10.00)
```

### Find a fare
```http
GET /api/fare-payment/fares/ride/1
```

### Create a payment
```http
POST /api/fare-payment/payments
Content-Type: application/json

{
  "fareId": 1,
  "rideId": 1,
  "userId": 10,
  "paymentMethod": "CARD"
}
```

### Find a payment
```http
GET /api/fare-payment/payments/1
```

### Update payment status
```http
PUT /api/fare-payment/payments/1/status
Content-Type: application/json

{
  "status": "COMPLETED"
}
```

Allowed status values are `COMPLETED` and `FAILED`. A payment can only be updated while its current status is `PENDING`.
## Run services
cd "D:/SLIIT/year 3 sem 1/Application Development/Project/RideLink/fare-payment-service"

& "C:\Program Files\JetBrains\IntelliJ IDEA 2026.2\plugins\maven-plugin\lib\maven3\bin\mvn.cmd" spring-boot:run