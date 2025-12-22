# Guest Access API Documentation

## Overview

This document describes the backend API endpoints for guest/non-logged-in user access to the LMS. These endpoints allow non-authenticated users to access limited course content (first module only) without requiring login.

## Base URL

All endpoints are under: `/skillama/courses`

**Note:** These endpoints are **public** and do **not require authentication**. They are accessible without any Authorization header.

---

## API Endpoints

### 1. Get Guest Course

Returns the default guest course available for non-logged-in users.

**Endpoint:** `GET /skillama/courses/guest`

**Authentication:** Not required (public endpoint)

**Request:**
```http
GET /skillama/courses/guest
```

**Success Response (200 OK):**
```json
{
  "id": "course-id-here",
  "name": "Python Fundamentals - Guest Access",
  "description": "Introduction to Python programming for non-logged-in users",
  "thumbnail": "https://example.com/thumbnail.jpg",
  "isGuestCourse": true,
  "isPublic": true,
  "createdBy": "admin-id",
  "updatedBy": "admin-id",
  "createdAt": "2024-01-15T10:00:00",
  "updatedAt": "2024-01-15T10:00:00"
}
```

**Error Response (404 Not Found):**
```json
"No guest course found. Please configure a default guest course."
```

**Usage Example:**
```javascript
// JavaScript/TypeScript
const response = await fetch('/skillama/courses/guest');
if (response.ok) {
  const guestCourse = await response.json();
  console.log('Guest course:', guestCourse);
} else {
  console.error('No guest course found');
}
```

---

### 2. Get Guest Course Curriculum

Returns the curriculum for the guest course. **Only returns the first module** for non-logged-in users.

**Endpoint:** `GET /skillama/courses/guest/curriculum`

**Authentication:** Not required (public endpoint)

**Request:**
```http
GET /skillama/courses/guest/curriculum
```

**Success Response (200 OK):**
```json
[
  {
    "id": "module-id-1",
    "courseId": "course-id-here",
    "moduleName": "Python Environment Setup & Essentials",
    "moduleAssetPath": "/assets/module1",
    "order": 0,
    "submodules": [
      {
        "label": "Introduction to Python",
        "imagePath": "/images/intro.jpg",
        "isPracticalRequired": false,
        "scriptText": "print('Hello, World!')",
        "order": 0
      },
      {
        "label": "Python Installation",
        "imagePath": "/images/install.jpg",
        "isPracticalRequired": false,
        "scriptText": "# Installation guide",
        "order": 1
      }
    ],
    "title": "Module 1: Python Basics",
    "content": "This module covers...",
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
]
```

**Note:** This endpoint **always returns only the first module** regardless of how many modules exist in the course.

**Error Response (404 Not Found):**
```json
"No guest course found. Please configure a default guest course."
```

**Usage Example:**
```javascript
// JavaScript/TypeScript
const response = await fetch('/skillama/courses/guest/curriculum');
if (response.ok) {
  const curriculum = await response.json();
  // curriculum will contain only the first module
  console.log('First module:', curriculum[0]);
} else {
  console.error('No guest course found');
}
```

---

### 3. Get All Public Courses

Returns all courses marked as public (accessible to non-logged-in users).

**Endpoint:** `GET /skillama/courses/public`

**Authentication:** Not required (public endpoint)

**Request:**
```http
GET /skillama/courses/public
```

**Success Response (200 OK):**
```json
[
  {
    "id": "course-id-1",
    "name": "Python Fundamentals",
    "description": "Introduction to Python programming",
    "thumbnail": "https://example.com/thumbnail1.jpg",
    "isGuestCourse": false,
    "isPublic": true,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  },
  {
    "id": "course-id-2",
    "name": "JavaScript Basics",
    "description": "Learn JavaScript fundamentals",
    "thumbnail": "https://example.com/thumbnail2.jpg",
    "isGuestCourse": false,
    "isPublic": true,
    "createdAt": "2024-01-15T10:00:00",
    "updatedAt": "2024-01-15T10:00:00"
  }
]
```

**Usage Example:**
```javascript
// JavaScript/TypeScript
const response = await fetch('/skillama/courses/public');
const publicCourses = await response.json();
console.log('Public courses:', publicCourses);
```

---

### 4. Get First Public Course

Returns the first public course (useful as a default for guest access).

**Endpoint:** `GET /skillama/courses/public/first`

**Authentication:** Not required (public endpoint)

**Request:**
```http
GET /skillama/courses/public/first
```

**Success Response (200 OK):**
```json
{
  "id": "course-id-here",
  "name": "Python Fundamentals",
  "description": "Introduction to Python programming",
  "thumbnail": "https://example.com/thumbnail.jpg",
  "isGuestCourse": false,
  "isPublic": true,
  "createdAt": "2024-01-15T10:00:00",
  "updatedAt": "2024-01-15T10:00:00"
}
```

**Error Response (404 Not Found):**
```json
"No public course found."
```

**Usage Example:**
```javascript
// JavaScript/TypeScript
const response = await fetch('/skillama/courses/public/first');
if (response.ok) {
  const firstPublicCourse = await response.json();
  console.log('First public course:', firstPublicCourse);
} else {
  console.error('No public course found');
}
```

---

### 5. Get Course Curriculum (with Guest Access Support)

Returns the curriculum for a specific course. Supports an optional `guest` parameter to limit results to the first module only.

**Endpoint:** `GET /skillama/courses/{courseId}/curriculum`

**Authentication:** Not required (public endpoint)

**Path Parameters:**
- `courseId` (string, required) - The ID of the course

**Query Parameters:**
- `guest` (boolean, optional, default: `false`) - If `true`, returns only the first module

**Request Examples:**
```http
# Get full curriculum
GET /skillama/courses/course-id-here/curriculum

# Get only first module (guest access)
GET /skillama/courses/course-id-here/curriculum?guest=true
```

**Success Response (200 OK) - Full Curriculum:**
```json
[
  {
    "id": "module-id-1",
    "courseId": "course-id-here",
    "moduleName": "Python Environment Setup & Essentials",
    "order": 0,
    "submodules": [...]
  },
  {
    "id": "module-id-2",
    "courseId": "course-id-here",
    "moduleName": "Python Language Basic Constructs",
    "order": 1,
    "submodules": [...]
  }
]
```

**Success Response (200 OK) - Guest Access (first module only):**
```json
[
  {
    "id": "module-id-1",
    "courseId": "course-id-here",
    "moduleName": "Python Environment Setup & Essentials",
    "order": 0,
    "submodules": [...]
  }
]
```

**Usage Example:**
```javascript
// JavaScript/TypeScript
// For logged-in users - get full curriculum
const fullCurriculum = await fetch(`/skillama/courses/${courseId}/curriculum`)
  .then(res => res.json());

// For guest users - get only first module
const guestCurriculum = await fetch(`/skillama/courses/${courseId}/curriculum?guest=true`)
  .then(res => res.json());
```

---

## Frontend Integration Guide

### Recommended Flow for Guest Users

1. **On App Load (Non-logged-in user):**
   ```javascript
   // Step 1: Get guest course
   const guestCourseResponse = await fetch('/skillama/courses/guest');
   if (!guestCourseResponse.ok) {
     // Handle error - no guest course configured
     return;
   }
   const guestCourse = await guestCourseResponse.json();
   
   // Step 2: Get guest curriculum (first module only)
   const curriculumResponse = await fetch('/skillama/courses/guest/curriculum');
   const curriculum = await curriculumResponse.json();
   
   // Step 3: Display course and first module in UI
   setCurrentCourse(guestCourse);
   setModules(curriculum); // Only first module
   ```

2. **For Logged-in Users:**
   ```javascript
   // Use existing authenticated endpoints
   // GET /skillama/courses/{courseId}/curriculum (without guest parameter)
   ```

### Error Handling

All endpoints return appropriate HTTP status codes:
- **200 OK** - Success
- **404 Not Found** - No guest/public course found

**Example Error Handling:**
```javascript
try {
  const response = await fetch('/skillama/courses/guest');
  if (!response.ok) {
    if (response.status === 404) {
      // No guest course configured
      console.error('Guest course not available');
      // Show appropriate message to user
    }
    return;
  }
  const data = await response.json();
  // Process data
} catch (error) {
  console.error('Network error:', error);
  // Handle network errors
}
```

---

## Course Model Fields

### Course Object Structure

```typescript
interface Course {
  id: string;
  name: string;
  description: string;
  thumbnail?: string;
  isGuestCourse: boolean;  // true if this is the default guest course
  isPublic: boolean;        // true if accessible to non-logged-in users
  createdBy?: string;
  updatedBy?: string;
  createdAt: string;       // ISO 8601 datetime
  updatedAt: string;       // ISO 8601 datetime
}
```

### CourseCurriculum Object Structure

```typescript
interface CourseCurriculum {
  id: string;
  courseId: string;
  moduleName: string;
  moduleAssetPath?: string;
  order: number;
  submodules: Submodule[];
  title?: string;
  content?: string;
  createdAt: string;       // ISO 8601 datetime
  updatedAt: string;       // ISO 8601 datetime
}

interface Submodule {
  label: string;
  imagePath?: string;
  isPracticalRequired: boolean;
  scriptText?: string;
  order: number;
}
```

---

## Important Notes

1. **No Authentication Required:** All guest endpoints are public and do not require any Authorization header.

2. **First Module Only:** The `/guest/curriculum` endpoint always returns only the first module, regardless of how many modules exist in the course.

3. **Fallback Logic:** The `/guest` endpoint uses fallback logic:
   - First tries to find a course with `isGuestCourse: true`
   - If not found, falls back to the first course with `isPublic: true`
   - Returns 404 if neither exists

4. **Guest Parameter:** The regular curriculum endpoint (`/courses/{courseId}/curriculum`) supports a `guest` query parameter to limit results to the first module.

5. **CORS:** All endpoints support CORS and can be called from any origin.

---

## Testing

### Test Guest Course Endpoint
```bash
curl -X GET http://localhost:8080/skillama/courses/guest
```

### Test Guest Curriculum Endpoint
```bash
curl -X GET http://localhost:8080/skillama/courses/guest/curriculum
```

### Test Public Courses Endpoint
```bash
curl -X GET http://localhost:8080/skillama/courses/public
```

### Test Curriculum with Guest Parameter
```bash
# Full curriculum
curl -X GET http://localhost:8080/skillama/courses/{courseId}/curriculum

# First module only
curl -X GET "http://localhost:8080/skillama/courses/{courseId}/curriculum?guest=true"
```

---

## Backend Configuration & Migration

### Automatic Migration Script

A migration script is available to automatically set up a guest course. The script follows this strategy:

1. **Check if guest course exists** - If a course with `isGuestCourse: true` exists, skip migration
2. **Use existing public course** - If any course with `isPublic: true` exists, mark the first one as guest course
3. **Use first available course** - If any course exists, mark the first one as guest course and make it public
4. **Create default course** - If no courses exist, create a default "Python Fundamentals - Guest Access" course

### Running Migration

#### Option 1: Automatic Migration on Startup
Edit `GuestCourseMigrationScript.java` and set:
```java
private static final boolean AUTO_RUN_MIGRATION = true;
```

#### Option 2: Manual Migration via Admin API
Call the admin endpoint to trigger migration:
```http
POST /skillama/api/admin/courses/setup-guest-course
Authorization: Bearer <admin-token>
```

#### Option 3: Set Specific Course as Guest Course
```http
PUT /skillama/api/admin/courses/{courseId}/set-guest
Authorization: Bearer <admin-token>
```

### Manual Database Configuration

You can also manually configure a guest course in MongoDB:

```javascript
// Set a course as guest course
db.courses.updateOne(
  { _id: ObjectId("course-id") },
  { $set: { isGuestCourse: true, isPublic: true } }
)
```

### Important Notes

- **Only one guest course** should exist at a time. Setting a new course as guest will automatically unset the previous one.
- **Guest course must have curriculum** - At least one module must be added to the guest course for it to work properly.
- The migration script is **disabled by default** (`AUTO_RUN_MIGRATION = false`) to prevent accidental changes.

---

## Support

For questions or issues, please contact the backend team or refer to the backend implementation in:
- `src/main/java/com/prwatech/skillama/controller/CourseController.java`
- `src/main/java/com/prwatech/skillama/service/CourseService.java`

---

**Last Updated:** 2024-01-15  
**API Version:** 1.0

