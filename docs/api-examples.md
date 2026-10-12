# API Usage Examples (Windows-safe curl commands)

These curl commands demonstrate every endpoint implemented under `/api/v1`.
They use `curl.exe -d @docs/examples/<file>.json` so they execute reliably on Windows Command Prompt and PowerShell without escaping or quoting issues.

> **Default Seed Trips (dev profile startup):**
> - **Trip 1**: `TRIP-A-NIGHT-SAFETY` (Woman dropped alone at 23:10 without escort)
> - **Trip 2**: `TRIP-B-ROUTE-DEVIATION` (Sustained deviation >500m for >120s)
> - **Trip 3**: `TRIP-C-OTP-MISMATCH` (Wrong OTP entered on drop stop, stop frozen)
> - **Trip 4**: `TRIP-D-OVERSPEED` (Sustained overspeeding >limit for >30s)
> - **Trip 5**: `TRIP-E-CLEAN` (Compliant trip with zero safety violations)

---

### 1. Create a New Trip
Creates a trip with driver, vehicle, employees, stops, and polyline route segments.

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips -H "Content-Type: application/json" -d @docs/examples/create-trip.json
```

**Response (201 Created):**
Returns `TripResponse` containing generated stop IDs, planned route details, and **never contains any OTP values**.

---

### 2. Ingest GPS Fixes (Batch)
Ingests a batch of GPS pings for an active trip. Duplicates (by trip and timestamp) are automatically ignored.

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/gps -H "Content-Type: application/json" -d @docs/examples/gps-batch.json
```

**Response (200 OK):**
```json
{
  "accepted": 3,
  "duplicatesIgnored": 0
}
```
*(Note: If called on a completed trip, returns `409 Conflict`.)*

---

### 3. Record Operational Events

#### (a) Submit Drop OTP
Checks entered OTP in constant time (`OtpVerifier`). If mismatched, freezes the stop (`FROZEN`); if matched, marks it `COMPLETED`.

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/events -H "Content-Type: application/json" -d @docs/examples/event-otp.json
```

**Response (201 Created):**
```json
{
  "eventId": 101,
  "stopStatus": "COMPLETED"
}
```

#### (b) Assign Mid-trip Escort
```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/events -H "Content-Type: application/json" -d @docs/examples/event-escort-assigned.json
```

#### (c) Remove Escort
```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/events -H "Content-Type: application/json" -d @docs/examples/event-escort-removed.json
```

#### (d) Mark Trip Completed
Transitions trip status to `COMPLETED` and sets `completedAt`.

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/events -H "Content-Type: application/json" -d @docs/examples/event-completed.json
```

*(Note: Any GPS fix or event posted after trip completion returns `409 Conflict`.)*

---

### 4. Trigger Emergency SOS
Immediately raises a `CRITICAL` violation and dispatches alerts to `CONTROL_ROOM` and `TRANSPORT_MANAGER` carrying the latest known GPS coordinates.

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/sos -H "Content-Type: application/json" -d @docs/examples/sos.json
```

**Response:**
- `201 Created` for a newly raised panic press.
- `200 OK` for an exact idempotent retry of the same press.

---

### 5. Run Compliance & Safety Audit
Runs all rules (`NIGHT_SAFETY`, `OTP_MISMATCH`, `ROUTE_DEVIATION`, `OVER_SPEED`). Safe to call repeatedly (idempotent).
Requires trip to be `COMPLETED` (returns `409 Conflict` if still active).

```cmd
curl.exe -X POST http://localhost:8080/api/v1/trips/1/audit
```

**Response (200 OK):**
```json
{
  "id": "e44dcfd3-5561-4608-8e6f-b2e1bf77b105",
  "tripId": 1,
  "kind": "AUDIT",
  "triggeredBy": "ADMIN",
  "startedAt": "2026-10-12T13:30:00Z",
  "finishedAt": "2026-10-12T13:30:01Z",
  "newViolations": 2
}
```

---

### 6. Query Trip Violations
Retrieves all violations recorded for a given trip.

```cmd
curl.exe -X GET http://localhost:8080/api/v1/trips/1/violations
```

---

### 7. Query Single Violation with Alert Deliveries
Retrieves full details for a violation along with the delivery status across notification channels.

```cmd
curl.exe -X GET http://localhost:8080/api/v1/violations/1
```
