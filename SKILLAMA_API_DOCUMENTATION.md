# Skillama Module - API Documentation

This document lists all available API endpoints in the Skillama module for the frontend team.

**Base URL:** The base URL depends on your server configuration. All endpoints are relative to the base URL.

**Authentication:** Most endpoints require JWT Bearer token in the Authorization header:
```
Authorization: Bearer <token>
```

**JWT Token Details:**
- **Token Source:** Obtained from `POST /skillama/users/login` response (`token` field)
- **Token Format:** JWT (JSON Web Token)
- **Algorithm:** HS256
- **Token Subject:** User email (used to identify user)
- **Token Claims:** Contains user email in `sub` claim
- **Token Storage:** Frontend should store in `localStorage` as `auth_token` or in user object
- **Token Usage:** Include in `Authorization: Bearer <token>` header for all authenticated endpoints
- **Token Expiration:** ⭐ **UPDATED**
  - **Access Token:** Valid for **37.5 days** (3,240,000,000 milliseconds)
  - **Refresh Token:** Valid for **62.5 days** (5,400,000,000 milliseconds)
  - **Expired Token Response:** Returns `401 Unauthorized`
  - **Frontend Handling:** On 401 response, redirect to login page

**Response Format Standards:**
- **New Endpoints** (`/skillama/api/*`): Use wrapped format `{ status: 200, data: {...} }`
- **Existing Endpoints** (`/skillama/*`): Use direct response format (legacy)
- **Error Responses** (`/skillama/api/*`): Use format `{ status: 400, error: "Bad Request", message: "..." }`

---

## Table of Contents
1. [User Management APIs](#user-management-apis)
2. [Course Management APIs](#course-management-apis)
3. [Course Curriculum APIs](#course-curriculum-apis)
4. [Review APIs](#review-apis)
5. [User Course Progress APIs](#user-course-progress-apis) ⭐ NEW
6. [Admin Panel APIs](#admin-panel-apis) ⭐ NEW

---

## User Management APIs

### 1. Register User
**Endpoint:** `POST /skillama/users/register`

**Description:** Register a new user

**Authentication:** Not required

**Request Body:**
```json
{
  "name": "John Doe",
  "email": "john@example.com",
  "password": "password123",
  "gender": "MALE"
}
```

**Response:** `200 OK`
```json
{
  "id": "user-id",
  "name": "John Doe",
  "email": "john@example.com",
  "active": false,
  "createdAt": "2024-01-15T10:00:00"
}
```

---

### 2. User Login ⭐ UPDATED
**Endpoint:** `POST /skillama/users/login`

**Description:** Authenticate user and get user details with JWT token and role

**Authentication:** Not required

**Request Body:**
```json
{
  "email": "john@example.com",
  "password": "password123"
}
```

**Response:** `200 OK`
```json
{
  "id": "user-id",
  "name": "John Doe",
  "email": "john@example.com",
  "role": "USER",
  "active": true,
  "gender": "MALE",
  "createdAt": "2024-01-15T10:00:00Z",
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."
}
```

**Response Fields:**
- `id`: User ID
- `name`: User name
- `email`: User email
- `role`: User role (USER, ADMIN, OWNER) ⭐ NEW
- `active`: Account activation status
- `gender`: User gender (MALE, FEMALE, OTHER)
- `createdAt`: Account creation timestamp
- `token`: JWT access token ⭐ NEW - Use this token for authenticated API calls

**Error Responses:**
- `401 Unauthorized` - Invalid credentials
- `403 Forbidden` - Account not activated

**Important Notes:**
- The `token` field contains the JWT access token needed for all authenticated endpoints
- Include this token in the `Authorization` header as: `Authorization: Bearer <token>`
- The token subject contains the user's email
- Default role is `USER` if not explicitly set

---

### 3. Forgot Password
**Endpoint:** `POST /skillama/users/forgot-password`

**Description:** Request password reset (dummy implementation)

**Authentication:** Not required

**Request Body:**
```json
{
  "email": "john@example.com"
}
```

**Response:** `200 OK`
```
"Password reset link sent to email (dummy)"
```

---

### 4. Get All Users
**Endpoint:** `GET /skillama/users`

**Description:** Get paginated list of all users

**Authentication:** Not required (may require authentication based on your security config)

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 10) - Page size
- `sortBy` (default: "createdAt") - Field to sort by
- `order` (default: "desc") - Sort order (asc/desc)

**Response:** `200 OK`
```json
{
  "content": [...],
  "totalElements": 100,
  "totalPages": 10,
  "size": 10,
  "number": 0
}
```

---

### 5. Get User by ID
**Endpoint:** `GET /skillama/users/{id}`

**Description:** Get user details by user ID

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `id` - User ID

**Response:** `200 OK`
```json
{
  "id": "user-id",
  "name": "John Doe",
  "email": "john@example.com",
  "active": true,
  "createdAt": "2024-01-15T10:00:00"
}
```

**Error Response:** `404 Not Found` - User not found

---

### 6. Activate User (Admin)
**Endpoint:** `POST /skillama/users/admin/activate`

**Description:** Activate a user account

**Authentication:** Not required (may require admin authentication)

**Query Parameters:**
- `email` - User email to activate

**Response:** `200 OK`
```
"User activated successfully"
```

**Error Response:** `404 Not Found` - User not found

---

### 7. Deactivate User (Admin)
**Endpoint:** `POST /skillama/users/admin/deactivate`

**Description:** Deactivate a user account

**Authentication:** Not required (may require admin authentication)

**Query Parameters:**
- `email` - User email to deactivate

**Response:** `200 OK`
```
"User deactivated successfully"
```

**Error Response:** `404 Not Found` - User not found

---

### 8. Migrate Passwords (Admin)
**Endpoint:** `POST /skillama/users/admin/migrate-passwords`

**Description:** Migrate all user passwords to encoded format

**Authentication:** Not required (may require admin authentication)

**Response:** `200 OK`
```json
{
  "status": "success",
  "totalUsers": 100,
  "passwordsEncoded": 50,
  "alreadyEncoded": 45,
  "errors": 5,
  "message": "Migration completed: 50 encoded, 45 already encoded, 5 errors"
}
```

---

## Course Management APIs

### 1. Create Course
**Endpoint:** `POST /skillama/courses`

**Description:** Create a new course

**Authentication:** Not required (may require authentication based on your security config)

**Request Body:**
```json
{
  "name": "Python Fundamentals",
  "description": "Learn Python from scratch",
  "thumbnail": "https://example.com/course-thumbnail.jpg",
  "createdBy": "admin-id",
  "updatedBy": "admin-id"
}
```

**Response:** `200 OK`
```json
{
  "id": "course-id",
  "name": "Python Fundamentals",
  "description": "Learn Python from scratch",
  "thumbnail": "https://example.com/course-thumbnail.jpg",
  "createdBy": "admin-id",
  "createdAt": "2024-01-15T10:00:00"
}
```

**Note:** `thumbnail` field is optional. If not provided, frontend will use a default image.

---

### 2. Get All Courses
**Endpoint:** `GET /skillama/courses`

**Description:** Get paginated list of all courses

**Authentication:** Not required

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 10) - Page size
- `sortBy` (default: "createdAt") - Field to sort by
- `order` (default: "desc") - Sort order (asc/desc)

**Response:** `200 OK`
```json
{
  "content": [
      {
        "id": "course-id",
        "name": "Python Fundamentals",
        "description": "Learn Python from scratch",
        "thumbnail": "https://example.com/course-thumbnail.jpg",
        "createdAt": "2024-01-15T10:00:00"
      }
  ],
  "totalElements": 50,
  "totalPages": 5,
  "size": 10,
  "number": 0
}
```

---

### 3. Get Course by ID
**Endpoint:** `GET /skillama/courses/{id}`

**Description:** Get course details by course ID

**Authentication:** Not required

**Path Parameters:**
- `id` - Course ID

**Response:** `200 OK`
```json
      {
        "id": "course-id",
        "name": "Python Fundamentals",
        "description": "Learn Python from scratch",
        "thumbnail": "https://example.com/course-thumbnail.jpg",
        "createdAt": "2024-01-15T10:00:00"
      }
```

**Error Response:** `404 Not Found` - Course not found

---

### 4. Get Course Curriculum
**Endpoint:** `GET /skillama/courses/{courseId}/curriculum`

**Description:** Get all curriculum modules for a course

**Authentication:** Not required

**Path Parameters:**
- `courseId` - Course ID

**Response:** `200 OK`
```json
[
  {
    "id": "module-id",
    "courseId": "course-id",
    "moduleName": "Introduction",
    "submodules": [
      {
        "label": "Getting Started",
        "imagePath": "/images/getting-started.jpg",
        "isPracticalRequired": false,
        "scriptText": "Welcome to the course...",
        "order": 1
      }
    ],
    "order": 1
  }
]
```

---

### 5. Update Course
**Endpoint:** `PUT /skillama/courses/{id}`

**Description:** Update course details

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `id` - Course ID

**Request Body:**
```json
{
  "name": "Advanced Python",
  "description": "Master advanced Python concepts",
  "updatedBy": "admin-id"
}
```

**Response:** `200 OK`
```json
{
  "id": "course-id",
  "name": "Advanced Python",
  "description": "Master advanced Python concepts",
  "updatedAt": "2024-01-20T10:00:00"
}
```

**Error Response:** `404 Not Found` - Course not found

---

### 6. Delete Course
**Endpoint:** `DELETE /skillama/courses/{id}`

**Description:** Delete a course

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `id` - Course ID

**Response:** `204 No Content`

---

## Course Curriculum APIs

### 1. Add Module
**Endpoint:** `POST /skillama/curriculum/module`

**Description:** Add a new curriculum module

**Authentication:** Not required (may require authentication based on your security config)

**Request Body:**
```json
{
  "courseId": "course-id",
  "moduleName": "Introduction",
  "moduleAssetPath": "/assets/intro.mp4",
  "submodules": [
    {
      "label": "Getting Started",
      "imagePath": "/images/getting-started.jpg",
      "isPracticalRequired": false,
      "scriptText": "Welcome...",
      "order": 1
    }
  ],
  "order": 1,
  "createdBy": "admin-id"
}
```

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "courseId": "course-id",
  "moduleName": "Introduction",
  "submodules": [...],
  "createdAt": "2024-01-15T10:00:00"
}
```

---

### 2. Get Module by ID
**Endpoint:** `GET /skillama/curriculum/{moduleId}`

**Description:** Get curriculum module details

**Authentication:** Not required

**Path Parameters:**
- `moduleId` - Module ID

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "courseId": "course-id",
  "moduleName": "Introduction",
  "submodules": [...]
}
```

**Error Response:** `404 Not Found` - Module not found

---

### 3. Update Module
**Endpoint:** `PUT /skillama/curriculum/module/{moduleId}`

**Description:** Update a curriculum module

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `moduleId` - Module ID

**Request Body:**
```json
{
  "moduleName": "Updated Introduction",
  "submodules": [...],
  "updatedBy": "admin-id"
}
```

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "moduleName": "Updated Introduction",
  "updatedAt": "2024-01-20T10:00:00"
}
```

**Error Response:** `404 Not Found` - Module not found

---

### 4. Delete Module
**Endpoint:** `DELETE /skillama/curriculum/module/{moduleId}`

**Description:** Delete a curriculum module

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `moduleId` - Module ID

**Response:** `204 No Content`

---

### 5. Add Submodule
**Endpoint:** `POST /skillama/curriculum/module/{moduleId}/submodule`

**Description:** Add a submodule to a module

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `moduleId` - Module ID

**Request Body:**
```json
{
  "label": "New Lecture",
  "imagePath": "/images/new-lecture.jpg",
  "isPracticalRequired": true,
  "scriptText": "Lecture content...",
  "order": 2
}
```

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "submodules": [
    {
      "label": "New Lecture",
      "order": 2
    }
  ]
}
```

**Error Response:** `404 Not Found` - Module not found

---

### 6. Update Submodule
**Endpoint:** `PUT /skillama/curriculum/module/{moduleId}/submodule/{idx}`

**Description:** Update a submodule in a module

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `moduleId` - Module ID
- `idx` - Submodule index (0-based)

**Request Body:**
```json
{
  "label": "Updated Lecture",
  "imagePath": "/images/updated.jpg",
  "isPracticalRequired": false,
  "scriptText": "Updated content...",
  "order": 2
}
```

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "submodules": [
    {
      "label": "Updated Lecture",
      "order": 2
    }
  ]
}
```

**Error Response:** `404 Not Found` - Module not found

---

### 7. Delete Submodule
**Endpoint:** `DELETE /skillama/curriculum/module/{moduleId}/submodule/{idx}`

**Description:** Delete a submodule from a module

**Authentication:** Not required (may require authentication based on your security config)

**Path Parameters:**
- `moduleId` - Module ID
- `idx` - Submodule index (0-based)

**Response:** `200 OK`
```json
{
  "id": "module-id",
  "submodules": [...]
}
```

**Error Response:** `404 Not Found` - Module not found

---

## Review APIs

### 1. Create Review
**Endpoint:** `POST /skillama/review`

**Description:** Create a new review

**Authentication:** Not required (may require authentication based on your security config)

**Request Body:**
```json
{
  "courseId": "course-id",
  "userId": "user-id",
  "rating": 5,
  "comment": "Great course!",
  "createdAt": "2024-01-15T10:00:00"
}
```

**Response:** `200 OK`
```json
{
  "id": "review-id",
  "courseId": "course-id",
  "userId": "user-id",
  "rating": 5,
  "comment": "Great course!",
  "createdAt": "2024-01-15T10:00:00"
}
```

---

### 2. Get All Reviews
**Endpoint:** `GET /skillama/review`

**Description:** Get paginated list of all reviews

**Authentication:** Not required

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 10) - Page size
- `latestFirst` (default: true) - Sort by latest first

**Response:** `200 OK`
```json
{
  "content": [
    {
      "id": "review-id",
      "courseId": "course-id",
      "userId": "user-id",
      "rating": 5,
      "comment": "Great course!",
      "createdAt": "2024-01-15T10:00:00"
    }
  ],
  "totalElements": 50,
  "totalPages": 5,
  "size": 10,
  "number": 0
}
```

---

## User Course Progress APIs ⭐ NEW

These are the newly added endpoints for user dashboard and course progress tracking.

**Base Path:** `/skillama/api/users/me`

**Authentication:** Required - All endpoints require JWT Bearer token in Authorization header

---

### 1. Get User Courses with Progress
**Endpoint:** `GET /skillama/api/users/me/courses`

**Description:** Returns all courses assigned or purchased by the currently authenticated user, including progress information.

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": [
      {
        "id": "course-id-1",
        "name": "Python Fundamentals",
        "description": "Learn Python from scratch",
        "thumbnail": "https://example.com/course1.jpg",
        "progress": 45,
        "totalLectures": 20,
        "completedLectures": 9,
        "status": "in-progress",
        "enrolledAt": "2024-01-15T10:00:00Z",
        "lastAccessed": "2024-01-20T14:30:00Z"
      },
      {
        "id": "course-id-2",
        "name": "Advanced Python",
        "description": "Master advanced Python concepts",
        "thumbnail": "https://example.com/course2.jpg",
        "progress": 100,
        "totalLectures": 15,
        "completedLectures": 15,
        "status": "completed",
        "enrolledAt": "2024-01-10T09:00:00Z",
        "lastAccessed": "2024-01-25T16:00:00Z"
      }
  ]
}
```

**Response Fields:**
- `id`: Course ID
- `name`: Course name
- `description`: Course description
- `thumbnail`: Course thumbnail image URL (optional) ⭐ NEW
- `progress`: Completion percentage (0-100)
- `totalLectures`: Total number of lectures/submodules across all modules
- `completedLectures`: Number of completed lectures by the user
- `status`: "not-started" (progress = 0), "in-progress" (0 < progress < 100), "completed" (progress = 100)
- `enrolledAt`: ISO 8601 timestamp when user enrolled/assigned
- `lastAccessed`: ISO 8601 timestamp of last access to the course

**Error Responses:**
- `401 Unauthorized` - Invalid or missing JWT token

---

### 2. Get Course Progress (Detailed)
**Endpoint:** `GET /skillama/api/users/me/courses/{courseId}/progress`

**Description:** Returns detailed progress information for a specific course, including lecture-level details.

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Path Parameters:**
- `courseId` - The course ID (String)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "courseId": "course-id-1",
    "progress": 45,
    "totalLectures": 20,
    "completedLectures": 9,
    "lastAccessed": "2024-01-20T14:30:00Z",
    "lectures": [
      {
        "lectureId": "lecture-1",
        "moduleName": "Introduction",
        "lectureName": "Getting Started",
        "completed": true,
        "completedAt": "2024-01-15T10:30:00Z",
        "timeSpent": 1800
      },
      {
        "lectureId": "lecture-2",
        "moduleName": "Introduction",
        "lectureName": "Python Basics",
        "completed": false,
        "completedAt": null,
        "timeSpent": null
      }
    ]
  }
}
```

**Response Fields:**
- `courseId`: Course ID
- `progress`: Completion percentage (0-100)
- `totalLectures`: Total number of lectures
- `completedLectures`: Number of completed lectures
- `lastAccessed`: ISO 8601 timestamp of last access
- `lectures`: Array of lecture progress details
  - `lectureId`: Lecture/submodule ID (label from curriculum)
  - `moduleName`: Name of the module containing this lecture
  - `lectureName`: Name/label of the lecture
  - `completed`: Boolean indicating if lecture is completed
  - `completedAt`: ISO 8601 timestamp when completed (null if not completed)
  - `timeSpent`: Time spent in seconds (null if not tracked)

**Error Responses:**
- `401 Unauthorized` - Invalid or missing JWT token
- `404 Not Found` - Course not found

---

### 3. Update Course Progress
**Endpoint:** `PUT /skillama/api/users/me/courses/{courseId}/progress`

**Description:** Updates the progress when a user completes a lecture. This should be called when a lecture is marked as complete.

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Path Parameters:**
- `courseId` - The course ID (String)

**Request Body:**
```json
{
  "lectureId": "lecture-id-123",
  "completed": true,
  "timeSpent": 1800
}
```

**Request Fields:**
- `lectureId`: The lecture/submodule ID (label from curriculum) - Required
- `completed`: Boolean indicating completion status - Required
- `timeSpent`: Time spent in seconds (optional)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "courseId": "course-id-1",
    "progress": 50,
    "totalLectures": 20,
    "completedLectures": 10,
    "lastAccessed": "2024-01-20T15:00:00Z",
    "message": "Progress updated successfully",
    "lectures": [...]
  }
}
```

**Response Fields:**
- Same as GET `/skillama/api/users/me/courses/{courseId}/progress` response
- `message`: Success message

**Error Responses:**
- `401 Unauthorized` - Invalid or missing JWT token
- `404 Not Found` - Course not found

**Note:** 
- When `completed` is set to `true`, the `completedAt` timestamp is automatically set to the current time
- The course progress percentage and `lastAccessed` timestamp are automatically recalculated and updated
- If a lecture progress record doesn't exist, it will be created

---

### 4. Enroll User to Course
**Endpoint:** `POST /skillama/api/users/me/courses/enroll`

**Description:** Enrolls the authenticated user to a course. This creates an enrollment record and initializes progress tracking.

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Request Body:**
```json
{
  "courseId": "course-id-1",
  "enrollmentType": "ASSIGNED"
}
```

**Request Fields:**
- `courseId`: The course ID to enroll in - Required
- `enrollmentType`: "ASSIGNED" or "PURCHASED" (optional, defaults to "ASSIGNED")

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "id": "enrollment-id",
    "userId": "user-id",
    "courseId": "course-id-1",
    "enrollmentType": "ASSIGNED",
    "enrolledAt": "2024-01-15T10:00:00Z",
    "status": "ACTIVE"
  }
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid or missing JWT token
- `404 Not Found` - Course not found

**Note:** 
- If the user is already enrolled, the existing enrollment is returned (no error)
- Enrollment automatically initializes progress tracking for the user-course combination

---

### 5. Unenroll User from Course
**Endpoint:** `DELETE /skillama/api/users/me/courses/{courseId}/enroll`

**Description:** Unenrolls the authenticated user from a course. Sets enrollment status to INACTIVE (does not delete for history).

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Path Parameters:**
- `courseId` - The course ID to unenroll from

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": "Successfully unenrolled from course"
}
```

**Error Responses:**
- `401 Unauthorized` - Invalid or missing JWT token
- `404 Not Found` - Enrollment not found

---

### 6. Update User Profile ⭐ NEW
**Endpoint:** `PUT /skillama/api/users/me/profile`

**Description:** Updates the authenticated user's profile information (name, email, gender).

**Authentication:** Required (Bearer token)

**Headers:**
```
Authorization: Bearer <jwt-token>
```

**Request Body:**
```json
{
  "name": "John Doe Updated",
  "email": "john.updated@example.com",
  "gender": "MALE"
}
```

**Request Fields:**
- `name` (optional) - Updated user name
- `email` (optional) - Updated email (must be unique)
- `gender` (optional) - Updated gender (MALE, FEMALE, OTHER)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "id": "user-id",
    "name": "John Doe Updated",
    "email": "john.updated@example.com",
    "role": "USER",
    "active": true,
    "gender": "MALE",
    "createdAt": "2024-01-15T10:00:00Z",
    "updatedAt": "2024-01-20T15:00:00Z"
  }
}
```

**Error Responses:**
- `400 Bad Request` - Email already exists or invalid data
- `401 Unauthorized` - Invalid or missing JWT token
- `404 Not Found` - User not found

**Note:** Password update should be done through a separate endpoint for security.

---

## Admin Course Enrollment APIs

These endpoints are for administrators to manage course enrollments, including bulk enrollment operations for migration purposes.

**Base Path:** `/skillama/api/admin/courses`

**Authentication:** Required - Admin access (add security annotations as needed)

---

### 1. Enroll User to Course (Admin)
**Endpoint:** `POST /skillama/api/admin/courses/{courseId}/enroll/{userId}`

**Description:** Admin endpoint to enroll a specific user to a specific course.

**Authentication:** Required (Admin)

**Path Parameters:**
- `courseId` - Course ID
- `userId` - User ID

**Query Parameters:**
- `enrollmentType` (optional) - "ASSIGNED" or "PURCHASED" (defaults to "ASSIGNED")

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "id": "enrollment-id",
    "userId": "user-id",
    "courseId": "course-id",
    "enrollmentType": "ASSIGNED",
    "enrolledAt": "2024-01-15T10:00:00Z",
    "status": "ACTIVE"
  }
}
```

---

### 2. Enroll All Users to a Course (Admin - Migration Utility)
**Endpoint:** `POST /skillama/api/admin/courses/{courseId}/enroll-all`

**Description:** Enrolls all existing users to a specific course. Useful for migrating existing data.

**Authentication:** Required (Admin)

**Path Parameters:**
- `courseId` - Course ID

**Query Parameters:**
- `enrollmentType` (optional) - "ASSIGNED" or "PURCHASED" (defaults to "ASSIGNED")

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "courseId": "course-id",
    "courseName": "Python Fundamentals",
    "totalUsers": 100,
    "newlyEnrolled": 85,
    "alreadyEnrolled": 15,
    "errors": 0,
    "message": "Enrollment completed: 85 newly enrolled, 15 already enrolled, 0 errors"
  }
}
```

**Use Case:** Use this endpoint to enroll all existing users to a course when migrating to the new enrollment system.

---

### 3. Enroll User to All Courses (Admin - Migration Utility)
**Endpoint:** `POST /skillama/api/admin/courses/enroll-all/{userId}`

**Description:** Enrolls a specific user to all existing courses. Useful for migrating existing data.

**Authentication:** Required (Admin)

**Path Parameters:**
- `userId` - User ID

**Query Parameters:**
- `enrollmentType` (optional) - "ASSIGNED" or "PURCHASED" (defaults to "ASSIGNED")

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "userId": "user-id",
    "userEmail": "user@example.com",
    "totalCourses": 10,
    "newlyEnrolled": 8,
    "alreadyEnrolled": 2,
    "errors": 0,
    "message": "Enrollment completed: 8 newly enrolled, 2 already enrolled, 0 errors"
  }
}
```

---

### 4. Enroll All Users to All Courses (Admin - Migration Utility)
**Endpoint:** `POST /skillama/api/admin/courses/enroll-all-to-all`

**Description:** Enrolls all users to all courses. Use with caution - this creates enrollments for every user-course combination.

**Authentication:** Required (Admin)

**Query Parameters:**
- `enrollmentType` (optional) - "ASSIGNED" or "PURCHASED" (defaults to "ASSIGNED")

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "totalUsers": 100,
    "totalCourses": 10,
    "newlyEnrolled": 850,
    "alreadyEnrolled": 150,
    "errors": 0,
    "message": "Bulk enrollment completed: 850 newly enrolled, 150 already enrolled, 0 errors"
  }
}
```

**Warning:** This endpoint can create a large number of enrollment records. Use only when necessary for migration.

---

### 5. Get Enrollment Statistics (Admin)
**Endpoint:** `GET /skillama/api/admin/courses/enrollments/stats`

**Description:** Get statistics about course enrollments.

**Authentication:** Required (Admin)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "totalEnrollments": 1000,
    "activeEnrollments": 950,
    "inactiveEnrollments": 50
  }
}
```

---

## Data Models

### UserCourseEnrollment
Tracks which courses a user is enrolled in (assigned or purchased).

**Collection:** `user_course_enrollments`

**Fields:**
- `id`: MongoDB document ID
- `userId`: User ID (MongoDB ObjectId)
- `courseId`: Course ID
- `enrollmentType`: "ASSIGNED" or "PURCHASED"
- `enrolledAt`: Enrollment timestamp
- `status`: "ACTIVE", "INACTIVE", or "COMPLETED"

### UserCourseProgress
Stores aggregated progress for a user-course combination.

**Collection:** `user_course_progress`

**Fields:**
- `id`: MongoDB document ID
- `userId`: User ID
- `courseId`: Course ID
- `progress`: Completion percentage (0-100)
- `totalLectures`: Total number of lectures
- `completedLectures`: Number of completed lectures
- `enrolledAt`: Enrollment timestamp
- `lastAccessed`: Last access timestamp
- `createdAt`: Document creation timestamp
- `updatedAt`: Document update timestamp

### UserLectureProgress
Tracks individual lecture completion status.

**Collection:** `user_lecture_progress`

**Fields:**
- `id`: MongoDB document ID
- `userId`: User ID
- `courseId`: Course ID
- `lectureId`: Lecture/submodule ID (label from curriculum)
- `moduleName`: Name of the module
- `lectureName`: Name of the lecture
- `completed`: Boolean completion status
- `completedAt`: Completion timestamp (null if not completed)
- `timeSpent`: Time spent in seconds
- `createdAt`: Document creation timestamp
- `updatedAt`: Document update timestamp

---

## Notes for Frontend Team

1. **Authentication:** ⭐ **UPDATED** - The login endpoint (`POST /skillama/users/login`) now returns a JWT token in the response. 
   - Extract the `token` field from the login response
   - Store it in `localStorage` as `auth_token` or in the user object
   - Include it in the `Authorization` header as `Bearer <token>` for all authenticated endpoints
   - The token is valid for the duration specified in `JwtUtils.JWT_TOKEN_VALIDITY`

2. **User Role:** ⭐ **UPDATED** - The login response now includes the `role` field (USER, ADMIN, OWNER).
   - Use this to route users to appropriate dashboards:
     - `ADMIN` or `OWNER` → `/admin`
     - `USER` → `/lms` or `/dashboard`
   - Default role is `USER` if not set

3. **User ID Extraction:** The backend automatically extracts the user ID from the JWT token (the token subject contains the user's email, which is used to find the user and get their MongoDB ID).

3. **Progress Calculation:** Progress is calculated as `(completedLectures / totalLectures) * 100`. The status is determined as:
   - `not-started`: progress = 0
   - `in-progress`: 0 < progress < 100
   - `completed`: progress = 100

4. **Lecture ID:** The `lectureId` in progress tracking corresponds to the `label` field of submodules in the course curriculum.

5. **Course Thumbnail:** ⭐ **UPDATED** - Course responses now include a `thumbnail` field.
   - This field is optional and may be `null` or empty
   - Frontend should use a default image if thumbnail is not provided
   - Available in: `GET /skillama/api/users/me/courses`, `GET /skillama/courses`, `GET /skillama/api/admin/courses`

6. **Enrollment:** ⚠️ **IMPORTANT** - Before a user can track progress or see courses in their dashboard, they must be enrolled in a course. 
   - Users can enroll themselves using `POST /skillama/api/users/me/courses/enroll`
   - Admins can enroll users using admin endpoints
   - For existing users/courses, use the migration endpoints to bulk enroll

7. **Response Format:** All new progress APIs return responses in the format:
   ```json
   {
     "status": 200,
     "data": {...}
   }
   ```

8. **Error Handling:** Handle the following error scenarios:
   - `401 Unauthorized`: Token missing, invalid, or expired
   - `404 Not Found`: Course or resource not found
   - `500 Internal Server Error`: Server-side error

---

## Migration Guide for Existing Users and Courses

### Problem
Existing users and courses don't have enrollment records, so:
- `GET /skillama/api/users/me/courses` will return an empty array
- Users won't see any courses in their dashboard

### Solution

#### Option 1: Bulk Enroll All Users to All Courses (Recommended for Migration)
```bash
POST /skillama/api/admin/courses/enroll-all-to-all?enrollmentType=ASSIGNED
```
This will enroll every user to every course. Use this when you want all users to have access to all courses.

#### Option 2: Enroll All Users to Specific Course
```bash
POST /skillama/api/admin/courses/{courseId}/enroll-all?enrollmentType=ASSIGNED
```
Use this to enroll all users to a specific course.

#### Option 3: Enroll Specific User to All Courses
```bash
POST /skillama/api/admin/courses/enroll-all/{userId}?enrollmentType=ASSIGNED
```
Use this to enroll a specific user to all courses.

#### Option 4: Manual Enrollment (For Selective Access)
Use the regular enrollment endpoints:
- User self-enrollment: `POST /skillama/api/users/me/courses/enroll`
- Admin enrollment: `POST /skillama/api/admin/courses/{courseId}/enroll/{userId}`

### Migration Steps

1. **Backup your database** (recommended before bulk operations)

2. **Run bulk enrollment** based on your needs:
   - If all users should have access to all courses: Use `enroll-all-to-all`
   - If only specific courses should be accessible: Use `enroll-all` for each course
   - If selective access is needed: Use manual enrollment

3. **Verify enrollment** using:
   - `GET /skillama/api/admin/courses/enrollments/stats` - Check enrollment statistics
   - `GET /skillama/api/users/me/courses` - Verify users can see their courses

4. **Test progress tracking**:
   - Enroll a test user to a course
   - Update progress using `PUT /skillama/api/users/me/courses/{courseId}/progress`
   - Verify progress appears in `GET /skillama/api/users/me/courses`

### Important Notes

- Enrollment records are required before progress can be tracked
- If a user is already enrolled, the enrollment endpoint will return the existing enrollment (no error)
- Unenrollment sets status to INACTIVE but doesn't delete the record (for history)
- Progress records are automatically initialized when a user is enrolled

---

## Admin Panel APIs ⭐ NEW

These endpoints are for administrators (ADMIN and OWNER roles) to manage the Skillama LMS system.

**Base Path:** `/skillama/api/admin`

**Authentication:** Required - All endpoints require JWT Bearer token with ADMIN or OWNER role

**Role Hierarchy:**
- **OWNER**: Highest level, can create admins and perform all operations
- **ADMIN**: Can manage users, courses, curriculum, and assignments (except creating other admins/owners)
- **USER**: Regular user, can only access LMS features

---

### Authentication & Authorization

#### 1. Check Admin Access
**Endpoint:** `GET /skillama/api/admin/check-access`

**Description:** Verifies if the current user has admin/owner access and returns permissions.

**Authentication:** Required

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "hasAccess": true,
    "role": "ADMIN",
    "permissions": [
      "CREATE_USER",
      "MANAGE_USERS",
      "CREATE_COURSE",
      "MANAGE_COURSES",
      "MANAGE_CURRICULUM",
      "ASSIGN_COURSES",
      "VIEW_ANALYTICS",
      "SYSTEM_SETTINGS"
    ]
  }
}
```

**Error Response:** `401 Unauthorized` - Invalid or missing JWT token

---

### User Management (Admin)

#### 1. Get All Users (Admin)
**Endpoint:** `GET /skillama/api/admin/users`

**Description:** Returns all users in the system (paginated) with filtering options.

**Authentication:** Required (ADMIN or OWNER)

**Query Parameters:**
- `page` (default: 0) - Page number (0-based)
- `size` (default: 20) - Page size
- `search` (optional) - ⭐ **IMPLEMENTED** - Search term (searches in name and email fields, case-insensitive)
- `role` (optional) - ⭐ **IMPLEMENTED** - Filter by role (USER, ADMIN, OWNER)
- `active` (optional) - ⭐ **IMPLEMENTED** - Filter by status (true/false)

**Examples:**
```
GET /skillama/api/admin/users?search=john&role=USER&active=true&page=0&size=20
GET /skillama/api/admin/users?search=admin&role=ADMIN
GET /skillama/api/admin/users?active=false
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "content": [
      {
        "id": "user-id-1",
        "name": "John Doe",
        "email": "john@example.com",
        "role": "USER",
        "active": true,
        "gender": "MALE",
        "createdAt": "2024-01-15T10:00:00Z",
        "createdBy": "admin-id-1",
        "updatedAt": "2024-01-15T10:00:00Z",
        "updatedBy": "admin-id-1"
      }
    ],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0
  }
}
```

---

#### 2. Create User (Admin)
**Endpoint:** `POST /skillama/api/admin/users`

**Description:** Creates a new user. Only ADMIN/OWNER can create users. Only OWNER can create ADMIN/OWNER.

**Authentication:** Required (ADMIN or OWNER)

**Request Body:**
```json
{
  "name": "Jane Smith",
  "email": "jane@example.com",
  "password": "securePassword123",
  "role": "USER",
  "active": true,
  "gender": "FEMALE"
}
```

**Response:** `201 Created`
```json
{
  "status": 201,
  "data": {
    "id": "user-id-2",
    "name": "Jane Smith",
    "email": "jane@example.com",
    "role": "USER",
    "active": true,
    "gender": "FEMALE",
    "createdAt": "2024-01-20T10:00:00Z",
    "createdBy": "admin-id-1"
  }
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request or email already exists
- `401 Unauthorized` - Invalid or missing JWT token
- `403 Forbidden` - Only OWNER can create ADMIN/OWNER users

---

#### 3. Create Admin User (Owner Only)
**Endpoint:** `POST /skillama/api/admin/users/create-admin`

**Description:** Creates a new admin user. Only OWNER can access this endpoint.

**Authentication:** Required (OWNER only)

**Request Body:**
```json
{
  "name": "Admin User",
  "email": "admin@example.com",
  "password": "securePassword123",
  "role": "ADMIN",
  "active": true
}
```

**Response:** `201 Created`
```json
{
  "status": 201,
  "data": {
    "id": "admin-id-2",
    "name": "Admin User",
    "email": "admin@example.com",
    "role": "ADMIN",
    "active": true,
    "createdAt": "2024-01-20T10:00:00Z",
    "createdBy": "owner-id-1"
  }
}
```

**Error Responses:**
- `403 Forbidden` - Only OWNER can create admin users
- `401 Unauthorized` - Invalid or missing JWT token

---

#### 4. Update User (Admin)
**Endpoint:** `PUT /skillama/api/admin/users/{userId}`

**Description:** Updates user information. Only OWNER can change roles to ADMIN/OWNER.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `userId` - User ID

**Request Body:**
```json
{
  "name": "Jane Smith Updated",
  "email": "jane.updated@example.com",
  "role": "USER",
  "active": true,
  "gender": "FEMALE"
}
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "id": "user-id-2",
    "name": "Jane Smith Updated",
    "email": "jane.updated@example.com",
    "role": "USER",
    "active": true,
    "updatedAt": "2024-01-21T10:00:00Z",
    "updatedBy": "admin-id-1"
  }
}
```

**Error Responses:**
- `400 Bad Request` - Invalid request or email already exists, or cannot change OWNER role
- `404 Not Found` - User not found
- `401 Unauthorized` - Invalid or missing JWT token

---

#### 5. Delete User (Admin)
**Endpoint:** `DELETE /skillama/api/admin/users/{userId}`

**Description:** Soft deletes a user (sets active to false). Cannot delete OWNER.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `userId` - User ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": null
}
```

**Error Responses:**
- `400 Bad Request` - Cannot delete OWNER user
- `404 Not Found` - User not found

---

### Course Management (Admin)

**Note:** These endpoints wrap existing course management endpoints. The original endpoints at `/skillama/courses` can also be used by admins.

#### 1. Get All Courses (Admin)
**Endpoint:** `GET /skillama/api/admin/courses`

**Description:** Returns all courses with pagination. Wraps existing `/skillama/courses` endpoint.

**Authentication:** Required (ADMIN or OWNER)

**Query Parameters:**
- `page` (default: 0) - Page number
- `size` (default: 20) - Page size
- `sortBy` (default: "createdAt") - Field to sort by
- `order` (default: "desc") - Sort order (asc/desc)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "content": [
      {
        "id": "course-id-1",
        "name": "Python Fundamentals",
        "description": "Learn Python from scratch",
        "thumbnail": "https://example.com/course-thumbnail.jpg",
        "createdAt": "2024-01-10T10:00:00Z"
      }
    ],
    "totalElements": 50,
    "totalPages": 3,
    "size": 20,
    "number": 0
  }
}
```

**Alternative Endpoint:** `GET /skillama/courses` (same functionality, can be used by admins, but uses legacy response format)

---

#### 2. Create Course (Admin)
**Endpoint:** `POST /skillama/api/admin/courses`

**Description:** Creates a new course. Wraps existing `/skillama/courses` endpoint.

**Authentication:** Required (ADMIN or OWNER)

**Request Body:**
```json
{
  "name": "Advanced Python",
  "description": "Master advanced Python concepts",
  "createdBy": "admin-id-1",
  "updatedBy": "admin-id-1"
}
```

**Response:** `201 Created`
```json
{
  "status": 201,
  "data": {
    "id": "course-id-2",
    "name": "Advanced Python",
    "description": "Master advanced Python concepts",
    "createdAt": "2024-01-20T10:00:00Z"
  }
}
```

**Alternative Endpoint:** `POST /skillama/courses` (same functionality, can be used by admins, but uses legacy response format)

---

#### 3. Update Course (Admin)
**Endpoint:** `PUT /skillama/api/admin/courses/{courseId}`

**Description:** Updates course information. Wraps existing `/skillama/courses/{id}` endpoint.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `courseId` - Course ID

**Request Body:**
```json
{
  "name": "Advanced Python Updated",
  "description": "Updated description",
  "updatedBy": "admin-id-1"
}
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "id": "course-id-2",
    "name": "Advanced Python Updated",
    "description": "Updated description",
    "updatedAt": "2024-01-21T10:00:00Z"
  }
}
```

**Alternative Endpoint:** `PUT /skillama/courses/{id}` (same functionality, can be used by admins, but uses legacy response format)

---

#### 4. Delete Course (Admin)
**Endpoint:** `DELETE /skillama/api/admin/courses/{courseId}`

**Description:** Deletes a course. Wraps existing `/skillama/courses/{id}` endpoint.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `courseId` - Course ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": null
}
```

**Alternative Endpoint:** `DELETE /skillama/courses/{id}` (same functionality, can be used by admins, but uses legacy response format)

---

### Course Curriculum Management (Admin)

**Note:** The existing curriculum endpoints at `/skillama/curriculum` can be used by admins. No separate admin endpoints are needed as the existing ones serve the same purpose.

**Reusable Endpoints:**
- `GET /skillama/courses/{courseId}/curriculum` - Get course curriculum
- `POST /skillama/curriculum/module` - Create module
- `GET /skillama/curriculum/{moduleId}` - Get module by ID
- `PUT /skillama/curriculum/module/{moduleId}` - Update module
- `DELETE /skillama/curriculum/module/{moduleId}` - Delete module
- `POST /skillama/curriculum/module/{moduleId}/submodule` - Add submodule
- `PUT /skillama/curriculum/module/{moduleId}/submodule/{idx}` - Update submodule
- `DELETE /skillama/curriculum/module/{moduleId}/submodule/{idx}` - Delete submodule

All these endpoints can be used by admins. See [Course Curriculum APIs](#course-curriculum-apis) section for details.

---

### Course Assignment

#### 1. Assign Courses to User
**Endpoint:** `POST /skillama/api/admin/assignments/assign`

**Description:** Assigns one or more courses to a user. Creates enrollment records and initializes progress tracking.

**Authentication:** Required (ADMIN or OWNER)

**Request Body:**
```json
{
  "userId": "user-id-1",
  "courseIds": ["course-id-1", "course-id-2", "course-id-3"]
}
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "userId": "user-id-1",
    "assignedCourses": 3,
    "enrollments": [
      {
        "courseId": "course-id-1",
        "courseName": "Python Fundamentals",
        "enrolledAt": "2024-01-20T10:00:00Z"
      },
      {
        "courseId": "course-id-2",
        "courseName": "Advanced Python",
        "enrolledAt": "2024-01-20T10:00:00Z"
      },
      {
        "courseId": "course-id-3",
        "courseName": "Data Science Basics",
        "enrolledAt": "2024-01-20T10:00:00Z"
      }
    ]
  }
}
```

**Response Fields:**
- `userId`: User ID
- `assignedCourses`: Number of courses assigned
- `enrollments`: Array of enrollment details
  - `courseId`: Course ID
  - `courseName`: Course name ⭐ NEW
  - `enrolledAt`: Enrollment timestamp

**Note:** If a user is already enrolled in a course, that course is skipped (no error).

---

#### 2. Unassign Course from User
**Endpoint:** `DELETE /skillama/api/admin/assignments/unassign`

**Description:** Unassigns a course from a user. Sets enrollment status to INACTIVE (soft delete).

**Authentication:** Required (ADMIN or OWNER)

**Request Body:**
```json
{
  "userId": "user-id-1",
  "courseId": "course-id-1"
}
```

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": null
}
```

**Error Response:** `404 Not Found` - Enrollment not found

---

#### 3. Get User Assignments
**Endpoint:** `GET /skillama/api/admin/assignments/user/{userId}`

**Description:** Returns all courses assigned to a user with progress information.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `userId` - User ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "userId": "user-id-1",
    "userName": "John Doe",
    "courses": [
      {
        "courseId": "course-id-1",
        "courseName": "Python Fundamentals",
        "enrolledAt": "2024-01-15T10:00:00Z",
        "progress": 45
      },
      {
        "courseId": "course-id-2",
        "courseName": "Advanced Python",
        "enrolledAt": "2024-01-10T09:00:00Z",
        "progress": 100
      }
    ]
  }
}
```

---

#### 4. Get Course Assignments
**Endpoint:** `GET /skillama/api/admin/assignments/course/{courseId}`

**Description:** Returns all users assigned to a course with progress information.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `courseId` - Course ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "courseId": "course-id-1",
    "courseName": "Python Fundamentals",
    "users": [
      {
        "userId": "user-id-1",
        "userName": "John Doe",
        "userEmail": "john@example.com",
        "enrolledAt": "2024-01-15T10:00:00Z",
        "progress": 45
      },
      {
        "userId": "user-id-2",
        "userName": "Jane Smith",
        "userEmail": "jane@example.com",
        "enrolledAt": "2024-01-16T10:00:00Z",
        "progress": 80
      }
    ],
    "totalEnrollments": 2
  }
}
```

---

### Analytics & Reports

#### 1. Get Dashboard Statistics
**Endpoint:** `GET /skillama/api/admin/analytics/dashboard`

**Description:** Returns statistics for admin dashboard.

**Authentication:** Required (ADMIN or OWNER)

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "totalUsers": 500,
    "activeUsers": 450,
    "totalCourses": 25,
    "activeCourses": 25,
    "totalEnrollments": 1500,
    "averageProgress": 65.5,
    "recentUsers": 10,
    "recentCourses": 5
  }
}
```

**Response Fields:**
- `totalUsers`: Total number of users in the system
- `activeUsers`: Number of active users
- `totalCourses`: Total number of courses
- `activeCourses`: Number of active courses
- `totalEnrollments`: Total number of course enrollments
- `averageProgress`: Average progress across all courses (0-100)
- `recentUsers`: Number of users created in the last 7 days
- `recentCourses`: Number of courses created in the last 7 days

---

#### 2. Get User Progress Report
**Endpoint:** `GET /skillama/api/admin/analytics/users/{userId}/progress`

**Description:** Returns detailed progress report for a user. Reuses the same data as `GET /skillama/api/admin/assignments/user/{userId}`.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `userId` - User ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "userId": "user-id-1",
    "userName": "John Doe",
    "courses": [
      {
        "courseId": "course-id-1",
        "courseName": "Python Fundamentals",
        "enrolledAt": "2024-01-15T10:00:00Z",
        "progress": 45
      }
    ]
  }
}
```

---

#### 3. Get Course Analytics
**Endpoint:** `GET /skillama/api/admin/analytics/courses/{courseId}`

**Description:** Returns analytics for a specific course.

**Authentication:** Required (ADMIN or OWNER)

**Path Parameters:**
- `courseId` - Course ID

**Response:** `200 OK`
```json
{
  "status": 200,
  "data": {
    "courseId": "course-id-1",
    "courseName": "Python Fundamentals",
    "totalEnrollments": 150,
    "activeEnrollments": 120,
    "completedEnrollments": 30,
    "averageProgress": 45.5,
    "completionRate": 20.0
  }
}
```

**Response Fields:**
- `courseId`: Course ID
- `courseName`: Course name
- `totalEnrollments`: Total number of enrollments (all statuses)
- `activeEnrollments`: Number of active enrollments
- `completedEnrollments`: Number of completed enrollments (progress = 100)
- `averageProgress`: Average progress across all users (0-100)
- `completionRate`: Percentage of users who completed the course (0-100)

---

### Reusable Endpoints for Admin Panel

The following existing endpoints can be used by admins without separate admin endpoints:

#### Course Management
- `GET /skillama/courses` - Get all courses (same as `GET /skillama/api/admin/courses`)
- `POST /skillama/courses` - Create course (same as `POST /skillama/api/admin/courses`)
- `GET /skillama/courses/{id}` - Get course by ID
- `PUT /skillama/courses/{id}` - Update course (same as `PUT /skillama/api/admin/courses/{courseId}`)
- `DELETE /skillama/courses/{id}` - Delete course (same as `DELETE /skillama/api/admin/courses/{courseId}`)
- `GET /skillama/courses/{courseId}/curriculum` - Get course curriculum

#### Curriculum Management
- `POST /skillama/curriculum/module` - Create module
- `GET /skillama/curriculum/{moduleId}` - Get module by ID
- `PUT /skillama/curriculum/module/{moduleId}` - Update module
- `DELETE /skillama/curriculum/module/{moduleId}` - Delete module
- `POST /skillama/curriculum/module/{moduleId}/submodule` - Add submodule
- `PUT /skillama/curriculum/module/{moduleId}/submodule/{idx}` - Update submodule
- `DELETE /skillama/curriculum/module/{moduleId}/submodule/{idx}` - Delete submodule

#### User Management
- `GET /skillama/users` - Get all users (paginated)
- `GET /skillama/users/{id}` - Get user by ID
- `POST /skillama/users/admin/activate` - Activate user
- `POST /skillama/users/admin/deactivate` - Deactivate user

**Note:** The admin endpoints at `/skillama/api/admin/*` provide the same functionality with consistent response format (`ApiResponse` wrapper) and additional authentication checks. You can use either set of endpoints.

---

## Error Response Format ⭐ NEW

All `/skillama/api/*` endpoints use a consistent error response format:

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed error message here"
}
```

**Error Status Codes:**
- `400 Bad Request` - Invalid request data
- `401 Unauthorized` - Invalid or missing token
- `403 Forbidden` - Insufficient permissions
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Server error

**Exception Handling:**
- `ResourceNotFoundException` → 404
- `IllegalArgumentException` → 400
- `RuntimeException` → 400/403/404/401 (based on message)
- Generic `Exception` → 500

**Note:** Existing `/skillama/*` endpoints use a different error format (`ResponseMessage`). New `/skillama/api/*` endpoints use the `ErrorResponse` format above.

---

## Delete Behaviors ⭐ NEW

### User Deletion
- **Type:** Soft Delete
- **Behavior:** Sets `active: false` (does not delete from database)
- **Preserved Data:**
  - User enrollments (status set to INACTIVE)
  - User progress records
  - All historical data
- **Restoration:** Can be restored by setting `active: true`
- **Restriction:** OWNER users cannot be deleted

### Course Deletion
- **Type:** Hard Delete
- **Behavior:** Permanently removes course from database
- **Preserved Data:**
  - User enrollments remain (orphaned references)
  - User progress records remain (orphaned references)
- **Restoration:** Cannot be restored
- **Recommendation:** Consider implementing soft delete for courses in future

---

## Pagination Parameters ⭐ NEW

All paginated endpoints support the following parameters:

**Query Parameters:**
- `page` (default: 0) - Page number (0-based indexing)
- `size` (default: 20 for admin, 10 for public) - Number of items per page
- `sortBy` (optional) - Field to sort by (default: "createdAt")
- `order` (optional) - Sort order: "asc" or "desc" (default: "desc")

**Sortable Fields:**
- **Users:** `createdAt`, `name`, `email`, `role`
- **Courses:** `createdAt`, `name`

**Response Format:**
```json
{
  "status": 200,
  "data": {
    "content": [...],
    "totalElements": 100,
    "totalPages": 5,
    "size": 20,
    "number": 0,
    "first": true,
    "last": false
  }
}
```

---

## Analytics Data Refresh ⭐ NEW

**Calculation Method:** Real-time (no caching)

All analytics endpoints calculate data in real-time on each request:
- `GET /skillama/api/admin/analytics/dashboard` - Calculates stats on each request
- `GET /skillama/api/admin/analytics/courses/{courseId}` - Calculates course analytics on each request
- `GET /skillama/api/admin/analytics/users/{userId}/progress` - Fetches progress data on each request

**Performance Note:** For large datasets, consider implementing caching in future for better performance.

---

## Bulk Operations Guidelines ⭐ NEW

**Bulk Enrollment Endpoints:**
- `POST /skillama/api/admin/courses/{courseId}/enroll-all` - Enroll all users to a course
- `POST /skillama/api/admin/courses/enroll-all/{userId}` - Enroll user to all courses
- `POST /skillama/api/admin/courses/enroll-all-to-all` - Enroll all users to all courses

**Usage Guidelines:**
1. **Safe for Production:** Yes, with proper admin authentication
2. **Role Restriction:** Should be restricted to ADMIN/OWNER (add `@PreAuthorize` in production)
3. **Recommended Approach:**
   - Use `enroll-all` for specific courses/users
   - Use `enroll-all-to-all` only for initial migration
   - Test with small datasets first

**Warning:** `enroll-all-to-all` can create a large number of enrollment records. Use with caution.

---

## Summary

**Total Endpoints in Skillama Module:** 50+

- User Management: 8 endpoints (public)
  - ⭐ **UPDATED**: Login endpoint now returns JWT token and role
- Course Management: 6 endpoints (public, reusable by admin)
  - ⭐ **UPDATED**: Course model now includes `thumbnail` field
- Course Curriculum: 7 endpoints (public, reusable by admin)
- Review: 2 endpoints
- User Course Progress: 5 endpoints ⭐ NEW
  - Get user courses with progress (includes thumbnail)
  - Get detailed course progress
  - Update course progress
  - Enroll user to course
  - Unenroll user from course
- Admin Course Enrollment: 5 endpoints ⭐ NEW
  - Enroll user to course (admin)
  - Enroll all users to course (migration)
  - Enroll user to all courses (migration)
  - Enroll all users to all courses (migration)
  - Get enrollment statistics
- Admin Panel: 15 endpoints ⭐ NEW
  - Authentication & Authorization: 1 endpoint
  - User Management: 5 endpoints
  - Course Management: 4 endpoints (wrappers for existing endpoints)
  - Course Assignment: 4 endpoints (includes course names in response)
  - Analytics: 3 endpoints

All endpoints are ready for frontend integration. The new Admin Panel APIs are fully implemented and ready to use.

**⭐ Recent Updates (2024-01-20):**
- ✅ Login endpoint returns JWT token and user role
- ✅ Course responses include thumbnail field
- ✅ Assignment responses include course names
- ✅ **Search and filter implemented** for user management (`GET /skillama/api/admin/users?search=...&role=...&active=...`)
- ✅ **User profile update endpoint** added (`PUT /skillama/api/users/me/profile`)
- ✅ **Error response format standardized** for `/skillama/api/*` endpoints
- ✅ **Token expiration documented** (37.5 days for access token)
- ✅ **Delete behaviors documented** (soft delete for users, hard delete for courses)
- ✅ **Pagination parameters documented**
- ✅ **Analytics refresh behavior documented** (real-time calculation)

**⚠️ Migration Required:** Before using the progress tracking features, existing users must be enrolled to courses using the enrollment endpoints or migration utilities.

**📝 Note on Endpoint Reusability:** Many existing endpoints at `/skillama/*` can be used by admins. The admin endpoints at `/skillama/api/admin/*` provide the same functionality with consistent `ApiResponse` wrapper format. You can use either set based on your preference.

**📝 Response Format:**
- **New Endpoints** (`/skillama/api/*`): Use wrapped format `{ status: 200, data: {...} }`
- **Existing Endpoints** (`/skillama/*`): Use direct response format (legacy)
- **Error Responses** (`/skillama/api/*`): Use format `{ status: 400, error: "Bad Request", message: "..." }`

---

**Last Updated:** 2024-01-20
**Status:** ✅ Ready for Testing

