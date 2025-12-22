# Access Control Endpoint - Test Commands

## Quick Test Commands

### 1. Guest User (No Authentication)
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control?courseId=6817bbe4cb6b8135daecc428' \
  -H 'Accept: application/json, text/plain, */*' \
  -H 'Accept-Language: en-US,en;q=0.9' \
  -H 'Cache-Control: no-cache' \
  -H 'Connection: keep-alive' \
  -H 'Origin: http://localhost:3000' \
  -H 'Pragma: no-cache' \
  -H 'Referer: http://localhost:3000/' \
  -H 'Sec-Fetch-Dest: empty' \
  -H 'Sec-Fetch-Mode: cors' \
  -H 'Sec-Fetch-Site: cross-site' \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36' \
  -H 'sec-ch-ua: "Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "macOS"' \
  -c cookies.txt \
  -v
```

**Expected Behavior:**
- Automatically creates a guest session if no session cookie exists
- Returns access control with full curriculum (all modules visible)
- Only first lecture is unlocked, rest are locked
- Sets `skillama_session_id` cookie in response

---

### 2. Guest User with Session Cookie
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control?courseId=6817bbe4cb6b8135daecc428' \
  -H 'Accept: application/json, text/plain, */*' \
  -H 'Accept-Language: en-US,en;q=0.9' \
  -H 'Cache-Control: no-cache' \
  -H 'Connection: keep-alive' \
  -H 'Origin: http://localhost:3000' \
  -H 'Pragma: no-cache' \
  -H 'Referer: http://localhost:3000/' \
  -H 'Sec-Fetch-Dest: empty' \
  -H 'Sec-Fetch-Mode: cors' \
  -H 'Sec-Fetch-Site: cross-site' \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36' \
  -H 'sec-ch-ua: "Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "macOS"' \
  -b cookies.txt \
  -v
```

**Expected Behavior:**
- Uses existing session from cookie
- Returns access control based on session's progress

---

### 3. Logged-in User (Bearer Token)
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control?courseId=6817bbe4cb6b8135daecc428' \
  -H 'Accept: application/json, text/plain, */*' \
  -H 'Accept-Language: en-US,en;q=0.9' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN_HERE' \
  -H 'Cache-Control: no-cache' \
  -H 'Connection: keep-alive' \
  -H 'Origin: http://localhost:3000' \
  -H 'Pragma: no-cache' \
  -H 'Referer: http://localhost:3000/' \
  -H 'Sec-Fetch-Dest: empty' \
  -H 'Sec-Fetch-Mode: cors' \
  -H 'Sec-Fetch-Site: cross-site' \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36' \
  -H 'sec-ch-ua: "Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "macOS"' \
  -v
```

**Expected Behavior:**
- Extracts userId from JWT token
- Creates user profile if doesn't exist
- Returns access control with progressive unlocking based on completed lectures

**To get JWT Token:**
```bash
# First login
curl -X POST 'https://prwatech.xyz/skillama/users/login' \
  -H 'Content-Type: application/json' \
  -d '{
    "email": "user@example.com",
    "password": "password123"
  }'
```

---

### 4. Without courseId (Uses Default/Guest Course)
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control' \
  -H 'Accept: application/json, text/plain, */*' \
  -H 'Accept-Language: en-US,en;q=0.9' \
  -H 'Cache-Control: no-cache' \
  -H 'Connection: keep-alive' \
  -H 'Origin: http://localhost:3000' \
  -H 'Pragma: no-cache' \
  -H 'Referer: http://localhost:3000/' \
  -H 'Sec-Fetch-Dest: empty' \
  -H 'Sec-Fetch-Mode: cors' \
  -H 'Sec-Fetch-Site: cross-site' \
  -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/143.0.0.0 Safari/537.36' \
  -H 'sec-ch-ua: "Google Chrome";v="143", "Chromium";v="143", "Not A(Brand";v="24"' \
  -H 'sec-ch-ua-mobile: ?0' \
  -H 'sec-ch-ua-platform: "macOS"' \
  -b cookies.txt \
  -v
```

**Expected Behavior:**
- Uses profile's current course or guest course as fallback

---

## Expected Response Format

### Success Response (200 OK)
```json
{
  "userId": null,
  "sessionId": "guest-session-xxx",
  "isGuest": true,
  "courseId": "6817bbe4cb6b8135daecc428",
  "courseName": "Course Name",
  "modules": [
    {
      "moduleId": "module-id",
      "moduleName": "Module 1",
      "moduleIndex": 0,
      "isAccessible": true,
      "isLocked": false,
      "lockReason": null,
      "lectures": [
        {
          "lectureLabel": "Lecture 1",
          "lectureId": "Lecture 1",
          "isAccessible": true,
          "isLocked": false,
          "isCompleted": false,
          "isInProgress": false,
          "lockReason": null,
          "completionPercentage": null,
          "unlockedAt": null,
          "completedAt": null
        },
        {
          "lectureLabel": "Lecture 2",
          "lectureId": "Lecture 2",
          "isAccessible": false,
          "isLocked": true,
          "isCompleted": false,
          "isInProgress": false,
          "lockReason": "Complete previous lectures to unlock this content",
          "completionPercentage": null,
          "unlockedAt": null,
          "completedAt": null
        }
      ]
    }
  ],
  "features": {
    "chat": {
      "accessible": true,
      "questionsRemaining": 5,
      "limitReached": false
    },
    "codeExecution": {
      "accessible": false,
      "reason": "Login required"
    },
    "debug": {
      "accessible": false,
      "reason": "Login required"
    }
  },
  "progress": {
    "totalLectures": 10,
    "completedLectures": 0,
    "inProgressLectures": 0,
    "lockedLectures": 9,
    "completionPercentage": 0
  }
}
```

### Error Response (404 Not Found)
```json
{
  "status": 404,
  "error": "NOT_FOUND",
  "message": "Course not found with ID: 6817bbe4cb6b8135daecc428"
}
```

---

## Simplified Commands (Minimal Headers)

### Guest User (Simplified)
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control?courseId=6817bbe4cb6b8135daecc428' \
  -H 'Accept: application/json' \
  -c cookies.txt \
  -v
```

### Logged-in User (Simplified)
```bash
curl -X GET 'https://prwatech.xyz/skillama/user-profile/access-control?courseId=6817bbe4cb6b8135daecc428' \
  -H 'Accept: application/json' \
  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \
  -v
```

---

## Troubleshooting

### Issue: 404 Not Found
- **Check:** Course ID exists in database
- **Check:** Course ID format is correct (MongoDB ObjectId)
- **Solution:** Verify course exists: `GET /skillama/courses/{courseId}`

### Issue: "Session ID or User ID required"
- **Check:** Cookie is being sent (use `-b cookies.txt` or `-c cookies.txt`)
- **Check:** JWT token is valid (for logged-in users)
- **Solution:** Endpoint should auto-create guest session, but check server logs

### Issue: Invalid Token
- **Check:** JWT token is not expired
- **Check:** Token format: `Bearer <token>` (with space)
- **Solution:** Re-login to get fresh token

