# Code Map — Trip Safety & Compliance Audit Engine

This document maps every file in the repository to its architectural layer, purpose, and interview reading priority:
- **Priority 1**: Must understand deeply (core design, business rules, concurrency/transactions, math/DSA).
- **Priority 2**: Understand the conceptual idea (supporting domain entities, fixtures, adapters, config).
- **Priority 3**: Skip / scan briefly (boilerplate enums, simple DTOs, basic repositories, standard exceptions).

---

## 1. Complete File Directory & Classification

| File | Layer | Purpose | Priority |
|---|---|---|:---:|
| [AuditService.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/AuditService.java) | `service` | Main orchestrator: loads trip facts with explicit queries (no open-in-view), executes all `SafetyRule` beans, handles dedup collisions, and triggers alerts for new violations. | **1** |
| [ViolationRecorder.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/ViolationRecorder.java) | `service` | Isolated `@Transactional` boundary: atomically commits one violation and its PENDING `AlertDelivery` rows (Transactional Outbox) per call. | **1** |
| [SosService.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/SosService.java) | `service` | Emergency fast-path: validates passenger, finds latest GPS fix at/before press, creates CRITICAL violation and triggers immediate alert dispatch. | **1** |
| [RecordOutcome.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/RecordOutcome.java) | `service` | Result record holding commit outcome (`NEW` vs `EXISTING`), saved entity, and pending delivery records. | **2** |
| [SafetyRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/SafetyRule.java) | `rules` | Strategy interface for compliance rules: pure function contract `TripContext -> List<Violation>`. DB-free and side-effect-free. | **1** |
| [NightSafetyRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/NightSafetyRule.java) | `rules` | Evaluates rule 1: female first pickup or female last drop alone during night window without escort; tracks escort timeline. | **1** |
| [OtpAtDropRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/OtpAtDropRule.java) | `rules` | Evaluates rule 2: validates OTP entered at drop stops, flags `MISSING` or `MISMATCH`, and ignores duplicate events. | **1** |
| [RouteDeviationRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/RouteDeviationRule.java) | `rules` | Evaluates rule 3: detects sustained off-route runs (> 500m for >= 120s) via `SustainedConditionDetector`. | **1** |
| [OverSpeedRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/OverSpeedRule.java) | `rules` | Evaluates rule 4: detects sustained over-speeding (>= 30s) against nearest segment limit; flags CRITICAL if > 1.5x limit. | **1** |
| [TripContext.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/TripContext.java) | `rules` | Immutable snapshot of all loaded trip facts, pre-matched fixes, and threshold configurations provided to rules. | **2** |
| [TripContextFactory.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/TripContextFactory.java) | `rules` | Factory that prepares `TripContext`: sorts stops/segments, normalizes GPS trail, and matches fixes to route segments. | **2** |
| [NightWindow.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/NightWindow.java) | `rules` | Time window value object supporting daily intervals `[start, end)` that cross midnight (e.g. 22:00 to 06:00). | **2** |
| [MatchedFix.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/MatchedFix.java) | `rules` | Tuple of normalized `GpsFix` and its pre-calculated `SegmentMatch` (distance and segment index). | **2** |
| [GeoMath.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/GeoMath.java) | `detect` | Geometric math on geographic coordinates: local flat-plane projection, point-to-segment distance with clamped parameter $t \in [0, 1]$. | **1** |
| [SustainedConditionDetector.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/SustainedConditionDetector.java) | `detect` | Reusable $O(N)$ sliding-window detector for sustained conditions with duration threshold and max inter-sample gap splitting. | **1** |
| [RouteMatcher.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/RouteMatcher.java) | `detect` | Finds nearest planned route segment to a coordinate using `GeoMath.pointToSegmentMeters`. | **2** |
| [GpsTrail.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/GpsTrail.java) | `detect` | GPS trail sanitizer: sorts fixes by timestamp and deduplicates identical timestamps. | **2** |
| [Run.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/Run.java) | `detect` | Generic record representing a continuous temporal run of samples meeting a condition. | **2** |
| [SegmentMatch.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/SegmentMatch.java) | `detect` | Record holding the index and perpendicular distance in metres to the nearest route segment. | **2** |
| [Violation.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Violation.java) | `domain` | Core audit entity: `@Immutable`, append-only, constructor derives deterministic `dedupKey` for idempotent persistence. | **1** |
| [ViolationRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/ViolationRepository.java) | `repository` | Append-only repository extending bare Spring Data `Repository`: exposes only query and insert methods (no update/delete). | **1** |
| [DedupKeys.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/audit/DedupKeys.java) | `audit` | Generates deterministic idempotency keys: `sha256(tripId | type | subjectKey)`. | **1** |
| [Hashing.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/audit/Hashing.java) | `audit` | Safe SHA-256 helper converting byte digests to 64-character lowercase hexadecimal strings. | **1** |
| [AlertRoutingPolicy.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/AlertRoutingPolicy.java) | `alert` | Routing matrix: CRITICAL $\to$ CONTROL_ROOM + TRANSPORT_MANAGER, HIGH $\to$ TRANSPORT_MANAGER, MEDIUM $\to$ DASHBOARD. | **1** |
| [AlertDispatcher.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/AlertDispatcher.java) | `alert` | Dispatches pending alerts; isolates notifier failures to preserve violations; executes startup crash-recovery. | **1** |
| [Notifier.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/Notifier.java) | `alert` | Strategy interface for notification delivery channels. | **2** |
| [NotifierFactory.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/NotifierFactory.java) | `alert` | Factory resolving `Notifier` bean by `AlertChannel`. | **2** |
| [AlertMessage.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/AlertMessage.java) | `alert` | Sanitized notification payload free of PII (contains coordinates only for emergency SOS). | **2** |
| [DashboardNotifier.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/DashboardNotifier.java) | `alert` | Log-based notifier stub for `DASHBOARD` channel. | **2** |
| [EmailNotifierStub.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/EmailNotifierStub.java) | `alert` | Log-based notifier stub for `TRANSPORT_MANAGER` channel. | **2** |
| [SmsNotifierStub.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/SmsNotifierStub.java) | `alert` | Log-based notifier stub for `CONTROL_ROOM` channel. | **2** |
| [Trip.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Trip.java) | `domain` | JPA entity representing an operational trip with driver, vehicle, status, and lifecycle timestamps. | **2** |
| [Stop.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Stop.java) | `domain` | JPA entity representing an ordered pickup or drop stop with expected OTP and status (`PENDING`, `COMPLETED`, `FROZEN`). | **2** |
| [RouteSegment.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/RouteSegment.java) | `domain` | JPA entity representing a planned polyline segment with start/end coordinates and speed limit. | **2** |
| [GpsFix.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/GpsFix.java) | `domain` | JPA entity representing raw GPS fix (latitude, longitude, speed, timestamp). | **2** |
| [TripEvent.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/TripEvent.java) | `domain` | JPA entity capturing trip events (`OTP_ENTERED`, `ESCORT_ASSIGNED`, `ESCORT_REMOVED`, `TRIP_COMPLETED`, `SOS`). Entered OTP is never stored. | **2** |
| [AuditRun.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/AuditRun.java) | `domain` | JPA entity tracking each audit execution or SOS trigger (auditRunId UUID, actor, timestamps, count of new violations). | **2** |
| [AlertDelivery.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/AlertDelivery.java) | `domain` | JPA entity tracking status (`PENDING`, `SENT`, `FAILED`) of an alert attempt per channel. | **2** |
| [Employee.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Employee.java) | `domain` | JPA entity for passenger reference (employee code and gender; no home addresses or phone numbers stored). | **2** |
| [Driver.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Driver.java) | `domain` | JPA entity for driver (driver code, name). | **2** |
| [Vehicle.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Vehicle.java) | `domain` | JPA entity for vehicle (registration number). | **2** |
| [GeoPoint.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/GeoPoint.java) | `domain` | Immutable lat/lon coordinate tuple. | **3** |
| [AlertChannel.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/AlertChannel.java) | `domain` | Enum: `CONTROL_ROOM`, `TRANSPORT_MANAGER`, `DASHBOARD`. | **3** |
| [AuditRunKind.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/AuditRunKind.java) | `domain` | Enum: `AUDIT`, `SOS`. | **3** |
| [DeliveryStatus.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/DeliveryStatus.java) | `domain` | Enum: `PENDING`, `SENT`, `FAILED`. | **3** |
| [Gender.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Gender.java) | `domain` | Enum: `FEMALE`, `MALE`, `OTHER`. | **3** |
| [Severity.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Severity.java) | `domain` | Enum: `CRITICAL`, `HIGH`, `MEDIUM`. | **3** |
| [StopStatus.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/StopStatus.java) | `domain` | Enum: `PENDING`, `COMPLETED`, `FROZEN`. | **3** |
| [StopType.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/StopType.java) | `domain` | Enum: `PICKUP`, `DROP`. | **3** |
| [TripEventType.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/TripEventType.java) | `domain` | Enum: `OTP_ENTERED`, `ESCORT_ASSIGNED`, `ESCORT_REMOVED`, `TRIP_COMPLETED`, `SOS`. | **3** |
| [TripStatus.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/TripStatus.java) | `domain` | Enum: `ACTIVE`, `COMPLETED`. | **3** |
| [ViolationType.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/ViolationType.java) | `domain` | Enum: `NIGHT_SAFETY`, `OTP_MISMATCH`, `ROUTE_DEVIATION`, `OVER_SPEED`, `SOS`. | **3** |
| [AuditProperties.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/config/AuditProperties.java) | `config` | Strongly-typed configuration properties mapped from `application.yml` (`audit.*`). | **2** |
| [AuditMetrics.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/config/AuditMetrics.java) | `config` | Micrometer metric wrapper exposing counters (`violations_total`, `audits_total`, `alerts_failed_total`, `sos_total`). | **2** |
| [SeedDataLoader.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/config/SeedDataLoader.java) | `config` | Ingests sample trips from JSON resources at startup if database is empty. | **2** |
| [AlertDeliveryRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/AlertDeliveryRepository.java) | `repository` | Spring Data repository for alert deliveries. | **3** |
| [AuditRunRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/AuditRunRepository.java) | `repository` | Spring Data repository for audit runs. | **3** |
| [DriverRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/DriverRepository.java) | `repository` | Spring Data repository for drivers. | **3** |
| [EmployeeRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/EmployeeRepository.java) | `repository` | Spring Data repository for employees. | **3** |
| [GpsFixRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/GpsFixRepository.java) | `repository` | Spring Data repository for GPS fixes. | **3** |
| [RouteSegmentRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/RouteSegmentRepository.java) | `repository` | Spring Data repository for planned route segments. | **3** |
| [StopRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/StopRepository.java) | `repository` | Spring Data repository for stops. | **3** |
| [TripEventRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/TripEventRepository.java) | `repository` | Spring Data repository for trip events. | **3** |
| [TripRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/TripRepository.java) | `repository` | Spring Data repository for trips. | **3** |
| [VehicleRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/VehicleRepository.java) | `repository` | Spring Data repository for vehicles. | **3** |
| [ConflictException.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/exception/ConflictException.java) | `exception` | Thrown when trip state is invalid for the requested action (HTTP 409). | **3** |
| [ForbiddenTripAccessException.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/exception/ForbiddenTripAccessException.java) | `exception` | Thrown when an employee acts on an unauthorized trip (HTTP 403). | **3** |
| [NotFoundException.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/exception/NotFoundException.java) | `exception` | Thrown when requested entity does not exist (HTTP 404). | **3** |
| [TripAuditApplication.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/TripAuditApplication.java) | `root` | Spring Boot main application class. | **3** |
| [application.yml](file:///c:/Projects/trip-audit/src/main/resources/application.yml) | `resources` | Primary configuration: H2 file database, open-in-view disabled, safety audit defaults. | **2** |
| `seed/*.json` | `resources` | Five reference trip datasets (trips A–E) illustrating all violation types and a clean baseline. | **3** |
| [pom.xml](file:///c:/Projects/trip-audit/pom.xml) | `build` | Maven build definition: Spring Boot 4.1.1, Java 21, dependencies. | **2** |
| [DESIGN.md](file:///c:/Projects/trip-audit/docs/DESIGN.md) | `docs` | Architectural design specification, ER diagrams, data flow, algorithm explanations. | **1** |
| [ASSUMPTIONS.md](file:///c:/Projects/trip-audit/docs/ASSUMPTIONS.md) | `docs` | Comprehensive log of Q1–Q35 engineering decisions and trade-offs. | **2** |
| [AGENTS.md](file:///c:/Projects/trip-audit/AGENTS.md) | `rules` | Development instructions and non-negotiable architectural rules. | **2** |
| [SafetyRulesAcceptanceTest.java](file:///c:/Projects/trip-audit/src/test/java/com/example/tripaudit/rules/SafetyRulesAcceptanceTest.java) | `test` | High-level acceptance test validating all 5 case study scenarios against rules. | **2** |
| [AuditServiceConcurrencyTest.java](file:///c:/Projects/trip-audit/src/test/java/com/example/tripaudit/service/AuditServiceConcurrencyTest.java) | `test` | Concurrency test proving deduplication under parallel audit executions. | **2** |
| [SustainedConditionDetectorTest.java](file:///c:/Projects/trip-audit/src/test/java/com/example/tripaudit/detect/SustainedConditionDetectorTest.java) | `test` | Unit tests for detector edge cases: single spikes, gap splits, duration thresholds. | **2** |
| [GeoMathTest.java](file:///c:/Projects/trip-audit/src/test/java/com/example/tripaudit/detect/GeoMathTest.java) | `test` | Unit tests for point projection, parameter clamping, polyline distances. | **2** |
| [ViolationImmutabilityTest.java](file:///c:/Projects/trip-audit/src/test/java/com/example/tripaudit/domain/ViolationImmutabilityTest.java) | `test` | Invariant tests verifying `Violation` has no setters and is annotated `@Immutable`. | **3** |
| *Other test files* | `test` | Focused unit and integration tests verifying individual rules, repos, and notifiers. | **3** |

---

## 2. Priority-1 Reading Order & Interview Guide

Read the 15 Priority-1 files in the following exact sequence. Each file builds on the concepts established by the preceding ones:

```
[1. SafetyRule] ───► [2. Violation] ───► [3. DedupKeys / Hashing] ───► [4. ViolationRepository]
                                                                                │
                                                                                ▼
[7. RouteDeviationRule] ◄── [6. SustainedDetector] ◄── [5. GeoMath] ◄──────────┘
       │
       ▼
[8. OverSpeedRule] ───► [9. NightSafetyRule] ───► [10. OtpAtDropRule]
                                                         │
                                                         ▼
[14. AlertDispatcher] ◄─── [13. AlertRoutingPolicy] ◄─── [12. SosService & AuditService] ◄─── [11. ViolationRecorder]
```

### Step 1: The Rule Contract
1. **[SafetyRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/SafetyRule.java)**
   - **What to understand**: The Strategy interface: `List<Violation> evaluate(TripContext ctx)`.
   - **Why it matters**: Safety rules are pure functions. They never access repositories, send alerts, or log PII. Adding a new rule requires creating one new class with zero changes to existing orchestration.
   - **Interview talking point**: *"Rules in, actions out: rules produce candidate facts; orchestration handles persistence, deduplication, and alerting."*

### Step 2: Immutability and Identity
2. **[Violation.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/domain/Violation.java)**
   - **What to understand**: The append-only design (`@Immutable`, no setters, `updatable = false` on every column).
   - **Why it matters**: A violation is an immutable historical finding. The constructor automatically derives the unique `dedupKey`, preventing any rule from forgetting or miscalculating it.
   - **Interview talking point**: *"Audit violations cannot be mutated or deleted at application level; duplicate findings are identified by their deterministic hash key."*

3. **[DedupKeys.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/audit/DedupKeys.java)** & **[Hashing.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/audit/Hashing.java)**
   - **What to understand**: The idempotency formula: `sha256(tripId + "|" + type + "|" + subjectKey)`.
   - **Why it matters**: Pipe separators prevent prefix/suffix collision (e.g. `(1, 23)` vs `(12, 3)`). SHA-256 provides a uniform 64-character database key.
   - **Interview talking point**: *"Using a deterministic dedupKey allows re-auditing trips safely at any time without duplicate records or re-alerting."*

4. **[ViolationRepository.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/repository/ViolationRepository.java)**
   - **What to understand**: Extends the bare Spring Data marker interface `Repository<Violation, Long>` rather than `JpaRepository` or `CrudRepository`.
   - **Why it matters**: By declaring only `save`, `findById`, `findByTripId`, and `existsByDedupKey`, the repository physically disallows callers from invoking `delete`, `deleteAll`, or update methods.
   - **Interview talking point**: *"We restrict the Spring Data interface to maintain an append-only invariant at compile time."*

### Step 3: Core Algorithms & Geo Math
5. **[GeoMath.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/GeoMath.java)**
   - **What to understand**: Local flat-plane projection onto meters and point-to-segment distance via scalar projection parameter $t = \frac{\vec{AP} \cdot \vec{AB}}{|\vec{AB}|^2}$ clamped to $[0, 1]$.
   - **Why it matters**: City-scale cab movements have $<0.1\%$ planar projection error while computing thousands of distances in microseconds without heavy spatial GIS libraries.
   - **Interview talking point**: *"Clamping $t \in [0, 1]$ handles endpoints when the vehicle sits before or beyond a segment, avoiding treating line segments as infinite lines."*

6. **[SustainedConditionDetector.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/detect/SustainedConditionDetector.java)**
   - **What to understand**: The generic single-pass $O(N)$ sliding run detector.
   - **Why it matters**: Both route deviation and over-speed rules share this logic. It filters out transient spikes/blips, splits runs when GPS pings have a gap $> 60\text{s}$, and checks if continuous duration $\ge \text{minDuration}$.
   - **Interview talking point**: *"Single-pass $O(N)$ time with $O(1)$ auxiliary space; reusable predicate-based sliding window."*

### Step 4: The Four Core Safety Rules
7. **[RouteDeviationRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/RouteDeviationRule.java)**
   - **What to understand**: Evaluates distance to nearest route segment $> 500\text{m}$ lasting $\ge 120\text{s}$.
   - **Why it matters**: Single off-route fixes (e.g. GPS jitter) are ignored; returns maximum deviation in meters and deviating segment index.
   - **Interview talking point**: *"Uses SustainedConditionDetector with distance predicate; stable subjectKey is run.startTime().toEpochMilli()."*

8. **[OverSpeedRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/OverSpeedRule.java)**
   - **What to understand**: Compares device speed against the matched segment speed limit for $\ge 30\text{s}$.
   - **Why it matters**: Elevates severity from `MEDIUM` to `CRITICAL` if peak speed $> 1.5 \times$ speed limit.
   - **Interview talking point**: *"Dynamic threshold lookup per segment; severity escalation based on peak speed ratio."*

9. **[NightSafetyRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/NightSafetyRule.java)**
   - **What to understand**: Night window evaluation ($[22:00, 06:00)$), first female pickup, last female drop alone, and temporal escort tracking.
   - **Why it matters**: Handles escort status dynamically by walking `ESCORT_ASSIGNED` / `ESCORT_REMOVED` events up to the stop's planned time.
   - **Interview talking point**: *"Escort status is not static: it reflects the chronological state at the exact planned time of the stop."*

10. **[OtpAtDropRule.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/rules/OtpAtDropRule.java)**
    - **What to understand**: Checks OTP compliance for every `DROP` stop: detects `MISMATCH` (wrong OTP entered) vs `MISSING` (completed drop with zero OTP events).
    - **Why it matters**: Suppresses duplicate event logs by `occurredAt`; preserves security by never handling raw OTPs in violations or logs.
    - **Interview talking point**: *"Entered OTP is never persisted or logged; verification happens at ingest and only a match boolean is recorded."*

### Step 5: Persistence, Transactions, & Orchestration
11. **[ViolationRecorder.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/ViolationRecorder.java)**
    - **What to understand**: Dedicated `@Component` with `@Transactional` method `recordIfAbsent`.
    - **Why it matters**: Called from non-transactional services. Commits each violation and its PENDING `AlertDelivery` rows in an isolated transaction. Flushes explicitly to trigger unique constraint collisions early.
    - **Interview talking point**: *"Each violation commit is isolated so one failure or duplicate collision does not abort other valid violations."*

12. **[AuditService.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/AuditService.java)**
    - **What to understand**: Orchestration pipeline: validates status (COMPLETED), queries facts explicitly (`open-in-view=false`), executes `List<SafetyRule>`, passes candidates to `ViolationRecorder`, updates `AuditRun`, and triggers alerts only for NEW outcomes.
    - **Why it matters**: `auditTrip` is deliberately NOT `@Transactional` to prevent long-running transactions and lazy loading errors.
    - **Interview talking point**: *"AuditService coordinates without an overarching transaction; Spring injects all rules via List<SafetyRule>."*

13. **[SosService.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/service/SosService.java)**
    - **What to understand**: Immediate emergency endpoint. Validates passenger stop, queries latest GPS fix at or before press time, and raises CRITICAL alert.
    - **Why it matters**: Bypasses the batch audit pipeline; safe to trigger multiple times thanks to `employeeId:pressedAt` dedup key.
    - **Interview talking point**: *"Fast-path emergency handling: coordinates are captured from the nearest preceding fix; duplicate button presses are deduplicated."*

### Step 6: Alert Routing & Outbox Delivery
14. **[AlertRoutingPolicy.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/AlertRoutingPolicy.java)**
    - **What to understand**: Pure mapping from `Severity` to designated `AlertChannel` sets.
    - **Why it matters**: Decouples rules from notification channels.
    - **Interview talking point**: *"Clean separation of concerns: rules determine severity; routing policy determines recipients."*

15. **[AlertDispatcher.java](file:///c:/Projects/trip-audit/src/main/java/com/example/tripaudit/alert/AlertDispatcher.java)**
    - **What to understand**: Transactional Outbox processor. Updates `AlertDelivery` status to `SENT` or `FAILED` without failing the parent violation.
    - **Why it matters**: Listens to `ApplicationReadyEvent` to pick up and recover any pending alert deliveries left behind after a crash.
    - **Interview talking point**: *"Transactional Outbox pattern ensures resilience: third-party notification outages never roll back database violations."*
