# RideLink

RideLink is a microservice-based ride-sharing platform built with Spring Boot and MySQL.

## Services
- Account Service - user account management
- Driver Vehicle Service - drivers and vehicles
- Fare Payment Service - billing and payments
- Ride Management Service - ride lifecycle

## Ports
- Account Service: 8081
- Driver Vehicle Service: 8082
- Fare Payment Service: 8083
- Ride Management Service: 8084

## MySQL setup
Create a MySQL database for each service or keep a shared database if preferred.

Example:
```sql
CREATE DATABASE ridelink_account_db;
CREATE DATABASE ridelink_driver_vehicle_db;
CREATE DATABASE ridelink_fare_payment_db;
CREATE DATABASE ridelink_ride_management_db;
```

Then update the credentials in each service's `application.properties` file.

## Run services
From the RideLink folder:

```bash
mvn clean install
mvn spring-boot:run -pl account-service
mvn spring-boot:run -pl driver-vehicle-service
mvn spring-boot:run -pl fare-payment-service
mvn spring-boot:run -pl ride-management-service
```
