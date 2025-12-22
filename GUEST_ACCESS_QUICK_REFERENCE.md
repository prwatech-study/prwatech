# Guest Access API - Quick Reference

## Endpoints Summary

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/skillama/courses/guest` | GET | ❌ | Get default guest course |
| `/skillama/courses/guest/curriculum` | GET | ❌ | Get guest curriculum (first module only) |
| `/skillama/courses/public` | GET | ❌ | Get all public courses |
| `/skillama/courses/public/first` | GET | ❌ | Get first public course |
| `/skillama/courses/{courseId}/curriculum?guest=true` | GET | ❌ | Get curriculum (first module only) |

## Quick Integration

```javascript
// Get guest course and curriculum
async function loadGuestCourse() {
  const course = await fetch('/skillama/courses/guest').then(r => r.json());
  const curriculum = await fetch('/skillama/courses/guest/curriculum').then(r => r.json());
  return { course, curriculum }; // curriculum contains only first module
}
```

## Response Examples

**Guest Course:**
```json
{
  "id": "course-id",
  "name": "Course Name",
  "isGuestCourse": true,
  "isPublic": true
}
```

**Guest Curriculum (First Module Only):**
```json
[
  {
    "id": "module-id",
    "moduleName": "Module 1",
    "order": 0,
    "submodules": [...]
  }
]
```

## Error Handling

- **404**: No guest course configured
- **200**: Success

See `GUEST_ACCESS_API_DOCUMENTATION.md` for full documentation.

