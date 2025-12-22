# Guest Course Migration Guide

## Overview

This guide explains how to set up and configure a guest course for non-logged-in users in the Skillama LMS.

## Migration Options

### Option 1: Automatic Migration Script (Recommended for First-Time Setup)

The `GuestCourseMigrationScript` can automatically set up a guest course on application startup.

**To Enable:**
1. Open `src/main/java/com/prwatech/skillama/script/GuestCourseMigrationScript.java`
2. Change `AUTO_RUN_MIGRATION` from `false` to `true`:
   ```java
   private static final boolean AUTO_RUN_MIGRATION = true;
   ```
3. Restart the application

**What It Does:**
- Checks if a guest course already exists (skips if found)
- If no guest course exists, it will:
  1. Look for any course with `isPublic: true` and mark the first one as guest course
  2. If no public courses exist, mark the first available course as guest course
  3. If no courses exist at all, create a default "Python Fundamentals - Guest Access" course

**Important:** After running the migration, you must add at least one module to the guest course curriculum for it to work properly.

---

### Option 2: Admin API Endpoints (Recommended for Ongoing Management)

#### Setup Guest Course Automatically

```http
POST /skillama/api/admin/courses/setup-guest-course
Authorization: Bearer <admin-jwt-token>
```

**Response:**
```json
{
  "status": 200,
  "data": "Guest course setup completed successfully"
}
```

This endpoint runs the same migration logic as Option 1, but can be triggered on-demand without restarting the application.

#### Set Specific Course as Guest Course

```http
PUT /skillama/api/admin/courses/{courseId}/set-guest
Authorization: Bearer <admin-jwt-token>
```

**Example:**
```http
PUT /skillama/api/admin/courses/507f1f77bcf86cd799439011/set-guest
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

**Response:**
```json
{
  "status": 200,
  "data": {
    "id": "507f1f77bcf86cd799439011",
    "name": "Python Fundamentals",
    "isGuestCourse": true,
    "isPublic": true,
    ...
  }
}
```

**What It Does:**
- Unsets any existing guest course
- Sets the specified course as the new guest course
- Makes the course public (`isPublic: true`)

---

### Option 3: Direct Database Update

If you prefer to update the database directly:

```javascript
// MongoDB Shell
use skillamaDB

// Set a specific course as guest course
db.courses.updateOne(
  { _id: ObjectId("your-course-id") },
  { 
    $set: { 
      isGuestCourse: true, 
      isPublic: true 
    } 
  }
)

// Unset existing guest course (if changing to a different course)
db.courses.updateMany(
  { isGuestCourse: true },
  { $set: { isGuestCourse: false } }
)

// Then set the new one
db.courses.updateOne(
  { _id: ObjectId("new-course-id") },
  { 
    $set: { 
      isGuestCourse: true, 
      isPublic: true 
    } 
  }
)
```

---

## Post-Migration Steps

### 1. Verify Guest Course Exists

```http
GET /skillama/courses/guest
```

Should return the guest course object.

### 2. Add Curriculum to Guest Course

The guest course **must have at least one module** in its curriculum. Use the curriculum management endpoints:

```http
POST /skillama/curriculum/module
Authorization: Bearer <admin-token>
Content-Type: application/json

{
  "courseId": "guest-course-id",
  "moduleName": "Introduction Module",
  "order": 0,
  "submodules": [
    {
      "label": "Welcome to the Course",
      "isPracticalRequired": false,
      "order": 0
    }
  ]
}
```

### 3. Verify Guest Curriculum Endpoint

```http
GET /skillama/courses/guest/curriculum
```

Should return at least one module (the first module only for guest users).

---

## Troubleshooting

### Error: "No guest course found"

**Solution:** Run the migration script or set a course as guest course using one of the methods above.

### Error: "Guest course curriculum is empty"

**Solution:** Add at least one module to the guest course curriculum using the curriculum management endpoints.

### Multiple Guest Courses

**Solution:** Only one course should have `isGuestCourse: true`. Use the admin API endpoint to set a specific course, which will automatically unset others.

### Guest Course Not Accessible

**Check:**
1. Course has `isGuestCourse: true` OR `isPublic: true`
2. Course has at least one module in curriculum
3. Module has at least one submodule
4. Application is running and endpoints are accessible

---

## Best Practices

1. **Use Admin API** for ongoing management (Option 2) rather than direct database updates
2. **Test after migration** by calling the guest endpoints
3. **Keep migration script disabled** (`AUTO_RUN_MIGRATION = false`) after initial setup to prevent accidental changes
4. **Document which course** is set as the guest course for your team
5. **Ensure curriculum is complete** before making a course the guest course

---

## Migration Script Location

- **File:** `src/main/java/com/prwatech/skillama/script/GuestCourseMigrationScript.java`
- **Admin Endpoints:** `src/main/java/com/prwatech/skillama/controller/AdminController.java`

---

## Support

For issues or questions, refer to:
- `GUEST_ACCESS_API_DOCUMENTATION.md` - Full API documentation
- `GUEST_ACCESS_QUICK_REFERENCE.md` - Quick endpoint reference

