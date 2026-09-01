# BeVest Mobile Application — Complete Development Plan

> **Scope:** Android mobile application only.  
> **Project:** BeVest: An IoT-Based Wearable System for Real-Time Construction Safety and Monitoring  
> **Development environment:** **Android Studio Iguana (2024-era release) + Kotlin**  
> **Web application:** Not included in this plan.  
> **Backend/cloud:** Firebase remains the cloud layer because it is part of the current BeVest system design.  
> **Status:** This plan is based on the current manuscript and should be updated when the manuscript is revised.

---

# 1. Mobile Application Goal

The BeVest Android application will serve as the main interface for authorized BeVest users to monitor construction workers, manage wearable vests, view worker locations, receive emergency alerts, manage personnel, and review safety records.

The mobile app connects to the BeVest IoT vest through Firebase. The ESP32-based vest sends sensor and GPS data to Firebase, and the Android application listens for changes in real time.

The application must support three roles:

1. **Administrator**
2. **Contractor**
3. **Site Safety Officer (SSO)**

Construction workers and foremen do **not** need a mobile account because they are monitored through the wearable BeVest device.

---

# 2. Core BeVest Data Shown in the Mobile App

Each paired BeVest unit can provide:

- Heart rate
- Body temperature
- Motion status
- Fall detection status
- GPS latitude and longitude
- Vest/device connection status
- Vest battery status
- Safety response status
- Last update timestamp

The app converts these readings into simple safety states:

- **NORMAL** — worker readings are within the safe range
- **WARNING** — abnormal reading or safety condition detected
- **DANGER** — warning persists or a serious event is detected
- **EMERGENCY** — worker does not acknowledge the safety response or manually requests assistance
- **OFFLINE** — vest has stopped transmitting data

---

# 3. Recommended Native Android Technology

The manuscript's original React Native/Expo mobile stack is intentionally ignored for this implementation.

## Required

- **Language:** Kotlin
- **IDE:** Android Studio Iguana
- **UI:** Jetpack Compose
- **Minimum Android:** Android 8.0 / API 26
- **Architecture:** MVVM
- **Navigation:** Navigation Compose
- **Asynchronous work:** Kotlin Coroutines + Flow
- **Local preferences:** DataStore
- **Cloud Authentication:** Firebase Authentication
- **Real-time IoT data:** Firebase Realtime Database
- **Application records:** Firebase Realtime Database or Cloud Firestore
- **Images/files:** Firebase Storage
- **Push notifications:** Firebase Cloud Messaging
- **Maps:** Google Maps SDK for Android / Maps Compose
- **QR scanning:** Google ML Kit Barcode Scanning
- **Dependency management:** Gradle Kotlin DSL

## Recommended Supporting Libraries

- Firebase BoM
- Lifecycle ViewModel
- Lifecycle Runtime Compose
- Material 3
- Navigation Compose
- Maps Compose
- Google Play Services Location
- ML Kit Barcode Scanning
- Coil Compose for profile images
- kotlinx.serialization or Gson where needed

---

# 4. Application Architecture

Use a clean MVVM structure so Firebase, UI, and business logic are separated.

```text
Android UI
    ↓
Screens / Composables
    ↓
ViewModels
    ↓
Use Cases / Business Logic
    ↓
Repositories
    ↓
Firebase + Google Maps + Local DataStore
```

## Main layers

### UI Layer
Contains:

- Screens
- Components
- Navigation
- Dialogs
- Forms
- Loading/error states

### ViewModel Layer
Responsible for:

- Screen state
- Form validation
- Starting repository operations
- Processing real-time data
- Exposing StateFlow to Compose

### Repository Layer
Responsible for:

- Firebase Authentication
- Reading/writing worker records
- Reading live sensor data
- Managing vest assignments
- Reading/writing incidents
- Notifications
- User management

### Data Layer
Contains:

- Firebase models
- DTOs
- Database paths
- Mapping functions
- Firebase listeners

---

# 5. Suggested Android Project Structure

```text
com.bevest.app/
│
├── MainActivity.kt
├── BeVestApplication.kt
│
├── navigation/
│   ├── AppNavGraph.kt
│   ├── AuthNavGraph.kt
│   ├── AdminNavGraph.kt
│   ├── ContractorNavGraph.kt
│   └── SsoNavGraph.kt
│
├── data/
│   ├── model/
│   │   ├── User.kt
│   │   ├── Worker.kt
│   │   ├── Vest.kt
│   │   ├── SensorReading.kt
│   │   ├── LocationReading.kt
│   │   ├── Alert.kt
│   │   ├── Incident.kt
│   │   ├── Notification.kt
│   │   └── ProjectSite.kt
│   │
│   ├── repository/
│   │   ├── AuthRepository.kt
│   │   ├── UserRepository.kt
│   │   ├── WorkerRepository.kt
│   │   ├── VestRepository.kt
│   │   ├── MonitoringRepository.kt
│   │   ├── AlertRepository.kt
│   │   └── ReportRepository.kt
│   │
│   └── firebase/
│       ├── FirebasePaths.kt
│       ├── FirebaseAuthSource.kt
│       └── FirebaseDatabaseSource.kt
│
├── domain/
│   ├── model/
│   ├── usecase/
│   └── safety/
│       ├── SafetyStatusEngine.kt
│       └── ThresholdConfig.kt
│
├── ui/
│   ├── auth/
│   ├── admin/
│   ├── contractor/
│   ├── sso/
│   ├── worker/
│   ├── vest/
│   ├── alerts/
│   ├── map/
│   ├── reports/
│   ├── profile/
│   └── components/
│
├── notifications/
│   ├── BeVestMessagingService.kt
│   └── NotificationHelper.kt
│
└── utils/
    ├── Validators.kt
    ├── DateTimeUtils.kt
    └── Result.kt
```

---

# 6. Authentication and Role Routing

## Login Screen

Fields:

- Email or username
- Password
- Forgot Password
- Sign In

### Login Flow

```text
Open App
   ↓
Check existing Firebase session
   ↓
No session → Login
   ↓
Authenticate
   ↓
Load user record
   ↓
Read user role
   ↓
ADMIN → Admin Dashboard
CONTRACTOR → Contractor Dashboard
SSO → SSO Dashboard
```

## Required behavior

- Show loading state during login.
- Display clear error for invalid credentials.
- Prevent disabled users from entering.
- Keep authenticated session unless user logs out.
- Route automatically according to role.
- Never allow users to manually access screens belonging to another role.

---

# 7. Role 1 — Administrator Mobile Module

Even without a web dashboard, the Administrator can perform management functions through the Android application.

## Admin Bottom Navigation

Recommended:

1. **Dashboard**
2. **Contractors**
3. **System**
4. **Profile**

---

## 7.1 Admin Dashboard

Display:

- Total contractors
- Total Site Safety Officers
- Total workers
- Total registered vests
- Active vests
- Offline vests
- Recent activity
- System alerts
- Maintenance mode status

### Actions

- Add Contractor
- Open Contractor Management
- Open Device/System Management
- View recent activities

---

## 7.2 Contractor Management

### Contractor List

Display:

- Name
- Company/project
- Account status
- Last login
- Number of SSOs
- Number of workers

Functions:

- Search contractor
- Filter active/inactive
- View details
- Add contractor
- Edit contractor
- Disable/remove contractor

### Add Contractor

Fields:

- First name
- Last name
- Email
- Phone number
- Company/project
- Temporary password or account invitation
- Account status

---

## 7.3 System / Device Management

Display:

- Total vests
- Active
- Offline
- Available
- Maintenance
- Battery status
- Last device check-in

Possible actions:

- View vest information
- Mark vest for maintenance
- Enable/disable maintenance mode
- View system activity logs

System updates/firmware management can remain outside the first Android MVP if the hardware update mechanism is not yet finalized.

---

## 7.4 Admin Profile

Functions:

- View profile
- Edit profile
- Change password
- Log out

---

# 8. Role 2 — Contractor Mobile Module

The Contractor focuses on overall safety performance, personnel, incidents, and reports.

## Contractor Bottom Navigation

Recommended:

1. **Dashboard**
2. **Workers**
3. **Reports**
4. **Profile**

---

## 8.1 Contractor Dashboard

Display:

- Active workers
- Today's alerts
- Monthly incidents
- Overall safety score
- Monthly safety trends
- Worker status distribution:
  - Normal
  - Warning
  - Danger
- Recent incidents

Charts:

- Monthly safety trends bar chart
- Worker status donut chart

Keep charts simple and readable on a phone.

---

## 8.2 Worker Directory

Display workers under the Contractor.

Each worker card:

- Photo
- Full name
- Worker ID
- Vest ID
- Project/site
- Current status

Filters:

- All
- Active
- Warning
- Danger
- Offline

Functions:

- Search by name
- Search by Worker ID
- Open Worker Details
- Add worker if allowed by final role rules

---

## 8.3 Site Safety Officer Management

The current manuscript assigns SSO account management to Contractors.

Functions:

- View SSO list
- Add SSO
- Edit SSO
- Remove/disable SSO
- Search SSO
- Assign SSO to a project/site

SSO record:

- First name
- Last name
- Email
- Phone
- Assigned project/site
- Status

---

## 8.4 Reports

### Monthly Reports

Display:

- Report month
- Total incidents
- Safety percentage
- Worker statistics

Actions:

- View report
- Generate report
- Export/download PDF
- Share PDF

### Incident History

Each incident should show:

- Date/time
- Worker
- Vest ID
- Type
- Severity
- Location
- Sensor values during incident
- Response status
- Resolution
- Resolved by

Filters:

- Date
- Worker
- Warning
- Danger
- Fall
- Temperature
- Heart rate
- Emergency

---

## 8.5 Contractor Profile

Functions:

- View profile
- Update profile
- Change password
- Log out

---

# 9. Role 3 — Site Safety Officer Mobile Module

This is the most important part of the mobile application because the SSO handles live worker monitoring and emergency response.

## SSO Bottom Navigation

Recommended:

1. **Dashboard**
2. **Workers**
3. **Map**
4. **Alerts**
5. **Vests**

Profile/settings can be opened from the top-right profile icon.

---

# 10. SSO Dashboard

The dashboard must prioritize emergencies over statistics.

## Dashboard sections

### Emergency Banner

If an active emergency exists:

```text
⚠ DANGER ALERT
Worker: Marcus Thorne
Reason: No Safety Response
[VIEW EMERGENCY]
```

### Summary Cards

- Workers on site
- Active vests
- Active warnings
- Active danger alerts

### Live Site Map Preview

Show worker markers.

Marker states:

- Normal
- Warning
- Danger
- Offline

### Critical Alert List

Examples:

- Elevated heart rate
- High body temperature
- Fall detected
- Prolonged inactivity
- Worker requested assistance
- Vest offline
- Low vest battery

---

# 11. SSO Worker Management

## Worker Directory

Each worker card:

- Photo
- Name
- Worker ID
- Vest ID
- Site
- Safety status
- Last update

Filters:

- All
- Active
- Warning
- Danger
- Offline
- Unassigned

Functions:

- Search
- Open Worker Details
- Add worker
- Edit worker
- Remove/deactivate worker

---

## Add Worker

Fields:

- Worker photo
- First name
- Last name
- Worker ID
- Phone number
- Email if applicable
- Project/site assignment
- Job/position if required
- Emergency contact if later approved

Do not require fields that are not finalized in the manuscript unless the team approves them.

---

# 12. Live Worker Details Screen

This is one of the core screens.

## Header

Show:

- Worker photo
- Worker name
- Worker ID
- Vest ID
- Status
- Last data update

## Live Sensor Cards

### Heart Rate

```text
Heart Rate
98 BPM
NORMAL
```

### Body Temperature

```text
Temperature
37.2 °C
NORMAL
```

### Motion

```text
Motion
MOVING
```

Possible states:

- Moving
- Stationary
- Fall detected
- Prolonged inactivity

### Vest

Display:

- Battery
- Connectivity
- Last check-in

## Location

Display worker location on map.

Show:

- GPS marker
- Last GPS update
- Latitude/longitude if needed

## Safety Response

A clear emergency control:

**Initiate Safety Response**

The app should ask for confirmation before manually starting a safety response.

---

# 13. Live Worker Map

Show all currently assigned workers.

## Marker information

When a marker is tapped:

- Worker name
- Worker ID
- Current safety status
- Heart rate
- Temperature
- Last update

Actions:

- View Worker
- View Alert if active

## Map requirements

- Auto-update worker positions
- Show status legend
- Center on project/site
- Allow zoom
- Avoid automatically moving the map every time a worker sends a new position
- Clearly identify stale/offline positions

---

# 14. Alert Management

## Alert History

Display newest alerts first.

Alert card:

- Severity
- Worker
- Alert type
- Time
- Status

Possible types:

- Heart Rate Warning
- Temperature Warning
- Fall Detected
- Motion/Inactivity
- Emergency Request
- No Safety Response
- Vest Offline
- Low Battery

## Alert details

Display:

- Worker
- Date/time
- Alert reason
- Sensor readings
- GPS location
- Safety response result
- Incident status
- Notes
- Resolution

Actions:

- Acknowledge alert
- Open worker
- Open location
- Mark/respond as resolved
- Add resolution notes

Never delete critical safety alerts from the normal user interface. Resolve/archive them instead.

---

# 15. Vest Management

## Vest List

Display:

- Vest ID
- Assigned worker
- Status
- Battery
- Connection
- Last update

Filters:

- All
- Active
- Available
- Offline
- Maintenance

---

## Pair Vest

Recommended 3-step process:

### Step 1 — Identify Vest

Options:

- Scan QR code
- Enter Vest ID manually

Validate:

- Vest exists
- Vest is not already paired
- Vest is online when possible

### Step 2 — Select Worker

Only display workers who do not currently have an assigned vest.

### Step 3 — Connection Test

Verify:

- Heart rate sensor
- Temperature sensor
- Motion sensor
- GPS
- Device connection

Then:

**Confirm Pairing**

---

## Assign / Replace Vest

Support:

- Assign available vest
- Unassign vest
- Replace damaged vest
- Preserve assignment history
- Do not erase old incident data when replacing a vest

---

# 16. Safety Monitoring Logic

The Android app must **display and react to** safety statuses, but authoritative detection should preferably be calculated in the cloud/IoT layer so alerts continue even if the mobile app is closed.

The manuscript currently mentions these physiological thresholds:

- Heart rate above **100 BPM**
- Body temperature above **38 °C**

These values should be stored as configurable system settings rather than hard-coded throughout the app.

## Basic physiological flow

```text
Sensor reading received
        ↓
Check threshold
        ↓
Within limits?
 ├── YES → NORMAL
 └── NO → WARNING
              ↓
       condition continues
              ↓
          5 minutes
              ↓
            DANGER
              ↓
     Vest buzzer/vibration
              ↓
   Wait for Safety Response
              ↓
          15 seconds
       ┌──────┴──────┐
       │             │
   Responds       No response
       │             │
Cancel alert      EMERGENCY
Resume monitor       ↓
                  Send FCM
                     ↓
              Attach GPS data
                     ↓
              Create incident
```

## Fall event

Recommended behavior for the current prototype:

```text
Fall detected
     ↓
DANGER
     ↓
Activate Safety Response
     ↓
Worker acknowledges?
 ├── YES → Record acknowledged event
 └── NO  → EMERGENCY + notify SSO
```

The exact fall-detection escalation timing should be finalized together with the IoT implementation.

---

# 17. Safety Response Button Logic

The physical BeVest button has two purposes in the current concept:

### During a system-generated alert

Worker presses the button to acknowledge that they are conscious/responding.

### Worker manually requests assistance

The button can trigger an emergency request.

The mobile application should display the result clearly:

- Waiting for response
- Worker acknowledged
- Emergency requested
- No response
- Escalated to SSO

---

# 18. Firebase Data Design

A simple structure for the prototype:

```text
users/
  {uid}/
    firstName
    lastName
    email
    phone
    role
    contractorId
    siteId
    status

workers/
  {workerId}/
    firstName
    lastName
    phone
    photoUrl
    contractorId
    siteId
    assignedVestId
    currentStatus
    active

vests/
  {vestId}/
    assignedWorkerId
    status
    battery
    online
    lastSeen

liveReadings/
  {workerId}/
    heartRate
    temperature
    motionState
    fallDetected
    latitude
    longitude
    timestamp

alerts/
  {alertId}/
    workerId
    vestId
    type
    severity
    status
    message
    createdAt
    acknowledgedAt
    resolvedAt
    resolvedBy

incidents/
  {incidentId}/
    workerId
    vestId
    alertId
    type
    severity
    heartRate
    temperature
    latitude
    longitude
    createdAt
    outcome
    resolutionNotes

vestAssignments/
  {assignmentId}/
    workerId
    vestId
    assignedAt
    unassignedAt
    assignedBy

projects/
  {projectId}/
    contractorId
    name
    location
    active

settings/
  thresholds/
    heartRateHigh
    temperatureHigh
    warningDurationSeconds
    responseTimeoutSeconds
```

For a final production system, historical sensor data should be separated from constantly changing live readings.

---

# 19. Firebase Security Rules Plan

Rules must enforce role permissions.

## Admin

Can:

- Manage Contractors
- View users
- View system/device data
- Manage system configuration

## Contractor

Can:

- Access only their organization/project data
- Manage their SSOs
- View their workers
- View reports/incidents
- View monitoring information allowed by final requirements

## SSO

Can:

- Access assigned site/project workers
- Manage workers
- Pair/assign vests
- Monitor readings
- Receive/respond to alerts
- Manage incidents

## Worker

No Firebase mobile login is required for the prototype.

Never rely only on hidden UI buttons for security. Firebase rules must protect the records themselves.

---

# 20. Real-Time Data Handling

Do not continuously reload whole database collections.

Use listeners only for information that must be real time:

- Worker live readings
- Active alerts
- Vest connection state
- Worker GPS positions

Use one-time or paginated reads for:

- Historical incidents
- Monthly reports
- User lists
- Old notifications

The UI should display:

- Loading
- Loaded
- Empty
- Offline/stale
- Error

for every real-time screen.

---

# 21. Offline and Stale Data Behavior

The current manuscript acknowledges network connectivity as a limitation, so the mobile app must make stale data obvious.

Example:

```text
Heart Rate
98 BPM
Last updated 2m 14s ago
⚠ Connection lost
```

Recommended logic:

- If updates are recent → Online
- If data stops arriving beyond the agreed timeout → Stale
- If device is confirmed disconnected → Offline

Do not show old readings as if they are live.

---

# 22. Push Notification Design

Use Firebase Cloud Messaging.

## Notification categories

### Critical

- Fall detected
- No safety response
- Worker emergency request
- Danger status

### Warning

- High heart rate
- High temperature
- Prolonged inactivity
- Vest offline

### Maintenance

- Low battery
- Device requires service

## Notification behavior

Tapping a notification should deep-link directly to:

```text
Notification
   ↓
Alert Detail
   ↓
Worker Detail / Map
```

Critical alerts should use a high-priority Android notification channel.

---

# 23. UI/UX Rules

BeVest is a safety application. Speed and clarity are more important than decorative visuals.

## Visual hierarchy

1. Active emergency
2. Danger
3. Warning
4. Normal information
5. Historical/administrative information

## Rules

- Use Material 3.
- Keep the existing BeVest orange as the primary brand accent.
- Use clear status icons in addition to colors.
- Never communicate safety status using color alone.
- Use large touch targets.
- Use readable text outdoors.
- Keep important emergency actions reachable with one hand.
- Minimize unnecessary animations.
- Require confirmation for destructive actions.
- Do not require confirmation just to open critical emergency information.
- Show timestamps beside live data.
- Show connectivity state beside IoT data.
- Use skeleton/loading indicators without blocking emergency notifications.

---

# 24. Main Reusable UI Components

Create these once and reuse them:

```text
BeVestTopBar
BottomNavigationBar
StatusChip
WorkerCard
VestCard
MetricCard
SensorReadingCard
EmergencyBanner
AlertCard
MapWorkerMarker
EmptyState
LoadingState
ErrorState
ConfirmationDialog
SearchBar
FilterChips
SectionHeader
BatteryIndicator
ConnectionIndicator
LastUpdatedLabel
```

---

# 25. Screen Inventory

## Shared

- Splash
- Login
- Forgot Password
- Profile
- Edit Profile
- Change Password
- Notification Permission
- Logout Confirmation

## Admin

- Admin Dashboard
- Contractor List
- Contractor Details
- Add Contractor
- Edit Contractor
- System/Device Overview
- Vest/Device Details
- Maintenance Mode

## Contractor

- Contractor Dashboard
- Worker Directory
- Worker Details
- SSO List
- Add SSO
- Edit SSO
- Reports
- Monthly Report Details
- Incident History
- Incident Details

## Site Safety Officer

- SSO Dashboard
- Worker Directory
- Add Worker
- Edit Worker
- Worker Live Details
- Live Map
- Alert History
- Alert Details
- Vest List
- Vest Details
- Pair Vest
- Assign Vest
- Replace/Unassign Vest

---

# 26. Development Order

Do not build every screen randomly. Implement in dependency order.

## Phase 0 — Project Setup

- [ ] Install/open Android Studio Iguana
- [ ] Create Kotlin Android project
- [ ] Configure Git
- [ ] Create development branch strategy
- [ ] Configure Firebase project
- [ ] Add `google-services.json`
- [ ] Add Firebase dependencies
- [ ] Add Google Maps API key securely
- [ ] Create application package structure
- [ ] Add Material 3 theme
- [ ] Create navigation skeleton

**Output:** App builds and launches.

---

## Phase 1 — Authentication

- [ ] Login UI
- [ ] Firebase Authentication
- [ ] Forgot Password
- [ ] Load user profile
- [ ] Role detection
- [ ] Role-based navigation
- [ ] Session persistence
- [ ] Logout
- [ ] Disabled-account handling

**Output:** Admin, Contractor, and SSO can log in and reach the correct dashboard.

---

## Phase 2 — Firebase Models and Repositories

- [ ] User model
- [ ] Worker model
- [ ] Vest model
- [ ] Sensor reading model
- [ ] Alert model
- [ ] Incident model
- [ ] Project/site model
- [ ] Repository interfaces
- [ ] Firebase repository implementations
- [ ] Error handling

**Output:** App can reliably read/write core BeVest records.

---

## Phase 3 — Site Safety Officer MVP

Build this before Contractor analytics because SSO monitoring is the primary safety function.

- [ ] SSO Dashboard
- [ ] Worker directory
- [ ] Worker details
- [ ] Live sensor listener
- [ ] Live worker status
- [ ] Live GPS map
- [ ] Alert history
- [ ] Alert details

**Output:** SSO can monitor a worker in real time.

---

## Phase 4 — Vest Management

- [ ] Vest list
- [ ] QR scanning
- [ ] Manual Vest ID entry
- [ ] Pair vest
- [ ] Assign worker
- [ ] Validate assignments
- [ ] Sensor connection test
- [ ] Unassign vest
- [ ] Replace vest
- [ ] Assignment history

**Output:** An SSO can pair a physical vest and assign it to a worker.

---

## Phase 5 — Emergency Response

- [ ] Warning status display
- [ ] Danger status display
- [ ] Emergency status display
- [ ] Safety Response screen/state
- [ ] FCM setup
- [ ] Notification channels
- [ ] Critical push notifications
- [ ] Deep linking
- [ ] Alert acknowledgment
- [ ] Resolve incident
- [ ] Incident creation
- [ ] GPS snapshot on emergency

**Output:** A hardware-generated emergency reaches the SSO phone and opens the correct worker/alert.

---

## Phase 6 — Worker Management

- [ ] Add worker
- [ ] Edit worker
- [ ] Deactivate worker
- [ ] Worker photo
- [ ] Site/project assignment
- [ ] Search/filter

**Output:** SSOs can maintain the worker roster.

---

## Phase 7 — Contractor Features

- [ ] Contractor Dashboard
- [ ] Worker status summary
- [ ] Safety trends
- [ ] Incident summary
- [ ] Worker directory
- [ ] SSO management
- [ ] Monthly reports
- [ ] Incident history
- [ ] PDF generation/export

**Output:** Contractor can review project safety performance from mobile.

---

## Phase 8 — Admin Features

- [ ] Admin Dashboard
- [ ] Contractor management
- [ ] System/device overview
- [ ] Maintenance mode
- [ ] Activity log
- [ ] Admin profile

**Output:** Core system administration can be performed without a web interface.

---

## Phase 9 — Hardening

- [ ] Firebase security rules
- [ ] Input validation
- [ ] Permission handling
- [ ] Offline/stale indicators
- [ ] Retry logic
- [ ] Empty states
- [ ] Error states
- [ ] Network loss behavior
- [ ] App restart recovery
- [ ] Notification behavior when app is closed
- [ ] Battery/network efficiency review

---

# 27. Testing Plan

## Authentication Testing

- Correct login
- Wrong password
- Unknown user
- Disabled user
- Password reset
- Logout
- Session restore
- Role access restriction

## Worker Testing

- Add worker
- Edit worker
- Search
- Filter
- Deactivate worker
- Worker without vest
- Worker with vest

## Vest Testing

- Valid QR
- Invalid QR
- Duplicate pairing
- Vest already assigned
- Worker already has vest
- Offline vest
- Sensor connection failure
- Successful pairing

## Monitoring Testing

- Normal reading
- High heart rate
- High temperature
- No motion
- Fall
- GPS update
- Device stops sending
- Old/stale sensor value

## Emergency Testing

- Warning begins
- Warning returns to normal
- Warning reaches 5 minutes
- Danger starts
- Worker responds within 15 seconds
- Worker does not respond
- Worker manually requests help
- Push notification arrives
- Notification works with app closed
- Correct alert opens after tapping notification
- Incident is recorded once only

## Map Testing

- Single worker
- Multiple workers
- No GPS
- Invalid GPS
- Rapid movement
- Offline worker
- Marker opens correct worker

## Security Testing

Attempt to confirm:

- SSO cannot view another site's workers
- Contractor cannot view another contractor's data
- Contractor cannot perform Admin functions
- Firebase client cannot edit protected role fields
- Disabled user cannot regain access using cached state

---

# 28. Acceptance Criteria for the Mobile Prototype

The mobile application can be considered functionally complete when:

- [ ] All three authorized roles can log in.
- [ ] The app routes users according to role.
- [ ] SSO can manage workers.
- [ ] SSO can pair and assign a BeVest unit.
- [ ] ESP32 sensor readings appear in the app in real time.
- [ ] Heart rate is displayed.
- [ ] Body temperature is displayed.
- [ ] Motion state is displayed.
- [ ] Fall detection is displayed.
- [ ] GPS worker location appears on the map.
- [ ] Normal/Warning/Danger states update correctly.
- [ ] Safety Response status is visible.
- [ ] Emergency push notifications reach the SSO.
- [ ] An emergency stores a corresponding alert/incident record.
- [ ] Contractor can view worker status and safety reports.
- [ ] Contractor can manage Site Safety Officer accounts.
- [ ] Admin can manage Contractor accounts.
- [ ] Profile and password management work.
- [ ] Role permissions are protected at Firebase level.
- [ ] The app identifies stale/offline IoT data.
- [ ] Core functions work on Android 8.0 or higher.

---

# 29. IoT–Mobile Integration Contract

Before the Android and IoT parts are built separately, agree on the exact Firebase fields.

Example live payload:

```json
{
  "workerId": "WRK-0023",
  "vestId": "BV-0023",
  "heartRate": 98,
  "temperature": 37.2,
  "motionState": "MOVING",
  "fallDetected": false,
  "latitude": 10.3157,
  "longitude": 123.8854,
  "battery": 78,
  "safetyResponse": "NONE",
  "timestamp": 1787932800000
}
```

The Android team and IoT team must agree on:

- Field names
- Data types
- Units
- Timestamp format
- Worker ID format
- Vest ID format
- Motion states
- Safety response states
- Alert types
- Error/null behavior

Do this **before** implementing the Firebase listeners.

---

# 30. Recommended Enumerations

Avoid random strings throughout the project.

```kotlin
enum class UserRole {
    ADMIN,
    CONTRACTOR,
    SSO
}

enum class SafetyStatus {
    NORMAL,
    WARNING,
    DANGER,
    EMERGENCY,
    OFFLINE
}

enum class MotionState {
    MOVING,
    STATIONARY,
    INACTIVE,
    FALL_DETECTED,
    UNKNOWN
}

enum class VestStatus {
    AVAILABLE,
    ASSIGNED,
    ACTIVE,
    OFFLINE,
    MAINTENANCE
}

enum class AlertSeverity {
    WARNING,
    DANGER,
    EMERGENCY
}

enum class AlertStatus {
    ACTIVE,
    ACKNOWLEDGED,
    RESOLVED
}
```

---

# 31. Important Implementation Rules

1. **Do not put safety monitoring logic only inside a screen.** It must continue even if the user navigates elsewhere.
2. **Do not depend on the Android app being open to create emergencies.**
3. **Do not treat Firebase listeners as permanent without removing them when no longer needed.**
4. **Do not show old data as live data.**
5. **Do not allow one worker to have multiple active vest assignments.**
6. **Do not allow one vest to be actively assigned to multiple workers.**
7. **Do not delete incident history when a worker or vest is changed.**
8. **Do not hard-code project-wide safety thresholds in multiple files.**
9. **Do not use UI visibility as authorization.**
10. **Keep a timestamp for every critical IoT reading and emergency action.**
11. **Use server timestamps for records whenever possible.**
12. **Use clear confirmation dialogs for account removal, vest replacement, and alert resolution.**
13. **Avoid storing sensitive credentials directly in the app.**
14. **The app is a monitoring prototype and must not present physiological readings as a medical diagnosis.**

---

# 32. MVP Priority

If development time becomes limited, build in this order:

### P0 — Must Work

- Authentication
- Role routing
- SSO dashboard
- Worker list
- Worker live details
- Heart rate
- Temperature
- Motion/fall
- GPS map
- Vest pairing
- Vest assignment
- Safety status
- Emergency alerts
- FCM notifications
- Incident record

### P1 — Important

- Worker CRUD
- Alert history
- Incident resolution
- Contractor dashboard
- SSO account management
- Admin Contractor management
- Profiles
- Password change

### P2 — Can Follow

- Advanced analytics
- PDF reports
- Safety score
- Maintenance mode
- Detailed activity logs
- Advanced filtering
- Rich charts
- Non-critical UI animations

---

# 33. Suggested Team Milestone Demo Sequence

For demonstrations, build the system so the following scenario can be shown from end to end:

```text
1. SSO logs in.
2. SSO registers Worker A.
3. SSO scans Vest BV-001.
4. SSO assigns BV-001 to Worker A.
5. ESP32 starts transmitting readings.
6. Worker A appears as NORMAL.
7. Worker A moves and GPS updates.
8. Simulated temperature/heart-rate threshold is exceeded.
9. Worker changes to WARNING.
10. Condition escalates to DANGER.
11. Vest activates its local warning.
12. Worker fails to acknowledge.
13. Firebase creates an emergency.
14. SSO receives a push notification.
15. SSO taps notification.
16. App opens Worker A's emergency details.
17. SSO sees worker location and readings.
18. SSO records/resolves the incident.
19. Contractor can later see the incident in reports/history.
```

If this full scenario works reliably, the core BeVest concept is successfully demonstrated.

---

# 34. Items That Need Confirmation During Manuscript Revision

The current manuscript is still under revision, so keep these configurable or easy to change:

- Exact heart-rate threshold
- Exact body-temperature threshold
- Exact five-minute Warning → Danger duration
- Exact 15-second Safety Response timeout
- Fall detection escalation timing
- Meaning of short press vs long press on the physical button
- Whether Contractor can directly add workers
- Whether Admin must manage all vests or only system-level data
- Which role is allowed to change safety thresholds
- Exact worker information fields
- Exact report contents
- Exact safety-score formula
- Exact offline timeout
- Exact incident resolution workflow

Do not bury these rules inside UI code.

---

# 35. Definition of Done

A feature is not considered complete just because the screen is visible.

For each feature, confirm:

- [ ] UI complete
- [ ] Input validation complete
- [ ] ViewModel complete
- [ ] Repository complete
- [ ] Firebase read/write complete
- [ ] Loading state complete
- [ ] Empty state complete
- [ ] Error state complete
- [ ] Permission/security rule complete
- [ ] Tested with real/simulated data
- [ ] Tested after app restart
- [ ] Tested with slow/no internet where relevant
- [ ] No duplicate database actions
- [ ] Critical actions contain timestamps
- [ ] Navigation works
- [ ] Role restrictions work

---

# 36. Final Mobile Development Target

The finished Kotlin Android application should provide one native BeVest application that adapts its interface according to the authenticated user's role:

```text
                         ┌───────────────┐
                         │ BeVest Android│
                         │ Kotlin App    │
                         └───────┬───────┘
                                 │
                         Firebase Login
                                 │
              ┌──────────────────┼──────────────────┐
              │                  │                  │
           ADMIN             CONTRACTOR            SSO
              │                  │                  │
      Account/System       Safety Overview    Real-Time Safety
        Management           & Reports          Monitoring
              │                  │                  │
              └──────────────────┼──────────────────┘
                                 │
                              Firebase
                                 │
                         ESP32 BeVest Units
```

The **primary success condition** is not the number of screens. It is whether live data can reliably travel from the BeVest wearable to Firebase and then reach the correct Site Safety Officer through the Android application quickly enough to support a real safety response.

---

## Source Basis

This development plan was derived from the current BeVest manuscript's described roles, workflows, mobile UI concepts, IoT monitoring functions, alert behavior, vest management, Firebase communication, GPS tracking, and emergency-response features. The original mobile technology stack in the manuscript was intentionally replaced with a native Kotlin/Android Studio Iguana approach as requested.
