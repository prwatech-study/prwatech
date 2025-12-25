# Debugging Empty Courses Response

## Problem
The endpoint `GET /skillama/api/users/me/courses` returns empty data `{"status": 200, "data": []}`.

## Root Cause
The endpoint queries the `user_course_enrollments` collection for **ACTIVE** enrollments. If the user has no active enrollments, an empty array is returned. This is **expected behavior**, not an error.

## How the Endpoint Works

1. **Extracts User ID from JWT Token**
   - The JWT token contains the user's email in the `sub` claim
   - The system looks up the user by email to get their MongoDB `_id`
   - This `_id` is used as `userId` in queries

2. **Queries for Active Enrollments**
   ```java
   List<UserCourseEnrollment> enrollments = enrollmentRepository.findByUserIdAndStatus(
       userId, UserCourseEnrollment.EnrollmentStatus.ACTIVE);
   ```

3. **Returns Course Data**
   - For each active enrollment, fetches course details and progress
   - If no enrollments exist, returns empty array `[]`

## Possible Reasons for Empty Data

### 1. **No Enrollments Exist** (Most Common)
   - The user has never enrolled in any courses
   - **Solution**: Enroll the user in a course

### 2. **Enrollments Exist but Status is INACTIVE**
   - User was enrolled but enrollment was deactivated
   - **Solution**: Re-activate enrollment or create a new one

### 3. **User ID Mismatch**
   - The `userId` extracted from JWT doesn't match the `userId` in enrollment records
   - **Solution**: Verify user lookup is working correctly

## Solutions

### Solution 1: Enroll User in a Course (Self-Enrollment)

**Endpoint:** `POST /skillama/api/users/me/courses/enroll`

**Request:**
```bash
curl -X POST 'https://prwatech.xyz/skillama/api/users/me/courses/enroll' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \
  -H 'Content-Type: application/json' \
  -d '{
    "courseId": "YOUR_COURSE_ID",
    "enrollmentType": "ASSIGNED"
  }'
```

**Response:**
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

### Solution 2: Admin Enrolls User (Admin Only)

**Endpoint:** `POST /skillama/api/admin/courses/{courseId}/enroll/{userId}`

**Request:**
```bash
curl -X POST 'https://prwatech.xyz/skillama/api/admin/courses/COURSE_ID/enroll/USER_ID?enrollmentType=ASSIGNED' \
  -H 'Authorization: Bearer ADMIN_JWT_TOKEN'
```

### Solution 3: Check Database Directly

Connect to MongoDB and check:

```javascript
// 1. Find the user by email (from JWT token)
db.users.findOne({ email: "jitendrachandwani4@gmail.com" })

// 2. Check enrollments for this user
db.user_course_enrollments.find({ 
  userId: "USER_ID_FROM_STEP_1",
  status: "ACTIVE"
})

// 3. Check all enrollments (including inactive)
db.user_course_enrollments.find({ userId: "USER_ID_FROM_STEP_1" })
```

## Debugging Steps

### Step 1: Verify JWT Token is Valid
```bash
# Decode your JWT token (use jwt.io or decode it)
# The "sub" field should contain: "jitendrachandwani4@gmail.com"
```

### Step 2: Verify User Exists
```bash
# Check if user exists in database
# The email from JWT should match a user record
```

### Step 3: Check for Enrollments
```bash
# Query MongoDB:
db.user_course_enrollments.find({ 
  userId: "YOUR_USER_ID",
  status: "ACTIVE" 
})
```

### Step 4: Verify Course Exists
```bash
# Make sure the course you're trying to enroll in exists:
db.courses.find({ _id: "COURSE_ID" })
```

## Testing the Fix

After enrolling in a course, test again:

```bash
curl 'https://prwatech.xyz/skillama/api/users/me/courses' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \
  -H 'Accept: application/json'
```

You should now see:
```json
{
  "status": 200,
  "data": [
    {
      "id": "course-id",
      "name": "Course Name",
      "description": "Course Description",
      "progress": 0,
      "totalLectures": 10,
      "completedLectures": 0,
      "status": "not-started",
      "enrolledAt": "2024-01-15T10:00:00Z",
      "lastAccessed": "2024-01-15T10:00:00Z"
    }
  ]
}
```

## Common Issues

### Issue: "User not found" Error
- **Cause**: The email in JWT token doesn't match any user in database
- **Fix**: Verify user exists or create the user account

### Issue: "Course not found" Error
- **Cause**: The courseId doesn't exist
- **Fix**: Verify course exists in database or use correct courseId

### Issue: Enrollment Created but Still Empty
- **Cause**: Enrollment might have been created with INACTIVE status
- **Fix**: Check enrollment status in database and update to ACTIVE if needed

## Database Schema Reference

### UserCourseEnrollment Collection
```javascript
{
  _id: ObjectId("..."),
  userId: "user-mongodb-id",
  courseId: "course-mongodb-id",
  enrollmentType: "ASSIGNED" | "PURCHASED",
  enrolledAt: ISODate("..."),
  status: "ACTIVE" | "INACTIVE" | "COMPLETED"
}
```

**Important**: The `userId` must match the MongoDB `_id` of the user, not the email.


