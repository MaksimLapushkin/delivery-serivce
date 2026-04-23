# delivery-service

`delivery-service` is a Spring Boot backend microservice responsible for managing the delivery lifecycle after an order is fulfilled.

It is designed as part of an event-driven system together with:

* **InventoryEngine** -> command-side service for products, stock, and orders
* **delivery-service** -> delivery lifecycle service
* **audit-projection-service** -> read-side projection and audit history

The service consumes `ORDER_FULFILLED` events, creates a delivery, manages delivery state transitions, stores an append-only delivery timeline, and publishes delivery lifecycle events through a transactional outbox.

---

## Architecture

The service follows a transactional, event-driven design:

* consumes order events from Kafka (`order.lifecycle.v1`)
* creates `Delivery` aggregates from fulfilled orders
* manages delivery lifecycle transitions through REST commands
* stores delivery history in `delivery_timeline`
* stores outbound integration events in `outbox_event`
* publishes delivery lifecycle events to Kafka (`delivery.lifecycle.v1`)

---

## Core responsibilities

The service handles:

* creation of delivery records from fulfilled orders
* storage of delivery/customer snapshot data
* delivery state transitions
* append-only delivery timeline
* transactional outbox for reliable event publishing
* idempotent consumption of upstream order events

---

## Delivery lifecycle

Supported delivery statuses:

* `ACCEPTED`
* `IN_TRANSIT`
* `DELIVERED`
* `RETURNED`
* `CANCELLED`

Allowed transitions:

```text
ACCEPTED   -> IN_TRANSIT
ACCEPTED   -> CANCELLED
IN_TRANSIT -> DELIVERED
IN_TRANSIT -> RETURNED
```

Invalid transitions are rejected by the service.

---

## Key concepts

### Delivery creation from order events

The service listens to:

```text
order.lifecycle.v1
```

When an `ORDER_FULFILLED` event is received:

* the service checks idempotency via `processed_event`
* creates a `Delivery` with status `ACCEPTED`
* writes `DELIVERY_ACCEPTED` to `delivery_timeline`
* writes `DELIVERY_ACCEPTED` to `outbox_event`

This allows the delivery workflow to start asynchronously without direct service-to-service calls.

---

### Idempotent event consumption

Kafka consumers may receive duplicate messages.

To prevent duplicate delivery creation:

* processed event IDs are stored in `processed_event`
* delivery creation also checks whether a delivery already exists for the same `orderId`

This protects both against duplicate messages and duplicate business effects.

---

### Delivery timeline

The service stores an append-only history of delivery events in:

```text
delivery_timeline
```

Examples:

* `DELIVERY_ACCEPTED`
* `DELIVERY_IN_TRANSIT`
* `DELIVERY_DELIVERED`
* `DELIVERY_RETURNED`
* `DELIVERY_CANCELLED`

This supports:

* auditability
* debugging
* downstream read models

---

### Transactional outbox pattern

Problem:

> database commit and Kafka publish are not atomic

Solution:

* delivery state changes and outbound events are stored in `outbox_event` in the same transaction
* background publisher reads pending outbox events
* publishes them to Kafka
* marks them as `PUBLISHED` after successful delivery

This makes event publication reliable and avoids lost messages.

---

## Kafka topics

### Consumes

```text
order.lifecycle.v1
```

### Publishes

```text
delivery.lifecycle.v1
```

Published delivery events include:

* `DELIVERY_ACCEPTED`
* `DELIVERY_IN_TRANSIT`
* `DELIVERY_DELIVERED`
* `DELIVERY_RETURNED`
* `DELIVERY_CANCELLED`

---

## Main entities

* `Delivery`
* `DeliveryTimeline`
* `ProcessedEvent`
* `OutboxEvent`

---

## REST API

Swagger UI:

```text
http://localhost:8083/swagger-ui.html
```

The API exposes:

### Query endpoints

* `GET /api/deliveries/{id}`
* `GET /api/deliveries/by-order/{orderId}`
* `GET /api/deliveries/{id}/timeline`

### Command endpoints

* `POST /api/deliveries/{id}/mark-in-transit`
* `POST /api/deliveries/{id}/deliver`
* `POST /api/deliveries/{id}/return`
* `POST /api/deliveries/{id}/cancel`

---

## How to run

### Prerequisites

* Java 21
* Maven
* Docker & Docker Compose
* PostgreSQL and Kafka infrastructure already running

---

### 1. Clone repository

```bash
git clone https://github.com/MaksimLapushkin/delivery-serivce.git
cd delivery-serivce
```

---

### 2. Start shared infrastructure

If you are running the full system through the main infrastructure setup, make sure these are available:

* PostgreSQL on `localhost:5433`
* Kafka on `localhost:9092`

The service expects database:

```text
delivery_db
```

---

### 3. Run application

```bash
mvn spring-boot:run
```

---

### 4. Access

* API: `http://localhost:8083`
* Swagger: `http://localhost:8083/swagger-ui.html`

---

## Demo flow

A minimal end-to-end delivery scenario:

1. Create and fulfill an order in **InventoryEngine**
2. Let `delivery-service` consume `ORDER_FULFILLED`
3. Verify that a delivery is created with status `ACCEPTED`
4. Move delivery to `IN_TRANSIT`
5. Complete delivery as `DELIVERED`
6. Verify timeline and published delivery events

<details>
<summary>PowerShell demo</summary>

```powershell
$deliveryBase = "http://localhost:8083"
$orderId = 1

$delivery = Invoke-RestMethod -Method GET -Uri "$deliveryBase/api/deliveries/by-order/$orderId"
$delivery

$deliveryId = $delivery.id

$timelineAfterCreate = Invoke-RestMethod -Method GET -Uri "$deliveryBase/api/deliveries/$deliveryId/timeline"
$timelineAfterCreate

$inTransit = Invoke-RestMethod -Method POST -Uri "$deliveryBase/api/deliveries/$deliveryId/mark-in-transit"
$inTransit

$deliveryAfterTransit = Invoke-RestMethod -Method GET -Uri "$deliveryBase/api/deliveries/$deliveryId"
$deliveryAfterTransit

$delivered = Invoke-RestMethod -Method POST -Uri "$deliveryBase/api/deliveries/$deliveryId/deliver"
$delivered

$finalDelivery = Invoke-RestMethod -Method GET -Uri "$deliveryBase/api/deliveries/$deliveryId"
$finalDelivery

$finalTimeline = Invoke-RestMethod -Method GET -Uri "$deliveryBase/api/deliveries/$deliveryId/timeline"
$finalTimeline
```

</details>

---

## Expected result

* fulfilled orders create deliveries automatically
* delivery starts in `ACCEPTED`
* delivery transitions are validated
* each state change is written to timeline
* each state change is written to outbox
* delivery events are published to Kafka
* downstream services can project delivery state asynchronously

---

## Testing

Tests focus on:

* lifecycle transition correctness
* invalid transition rejection
* idempotent consumption of `ORDER_FULFILLED`
* outbox publisher behavior
* integration of timeline and outbox writes

---

## Tech stack

* Java 21
* Spring Boot
* Spring Data JPA
* Hibernate
* PostgreSQL
* Flyway
* Kafka
* Maven

---

## Related services

* [InventoryEngine](https://github.com/MaksimLapushkin/InventoryEngine)
* [audit-projection-service](https://github.com/MaksimLapushkin/audit-projection-service)

### InventoryEngine

Publishes `ORDER_FULFILLED` with delivery/customer snapshot.

### audit-projection-service

Consumes order and delivery events and builds:

* audit history
* current state projection

---

## Author

Maksim Lapushkin
