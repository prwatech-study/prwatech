# How to Create the First Owner User

## Problem
The API requires an existing OWNER to create new OWNER users, creating a chicken-and-egg problem for the first owner.

## Solutions

### Option 1: Direct MongoDB Update (Recommended for First Owner)

Connect to your MongoDB database and update an existing user:

```javascript
// In MongoDB shell or MongoDB Compass
use your_database_name

// Find a user to promote to OWNER
db.users.find({ email: "admin@example.com" })

// Update the user's role to OWNER
db.users.updateOne(
  { email: "admin@example.com" },
  { 
    $set: { 
      role: "OWNER",
      updatedAt: new Date()
    }
  }
)

// Verify the update
db.users.findOne({ email: "admin@example.com" })
```

### Option 2: Create Owner via API (After First Owner Exists)

Once you have at least one OWNER, you can create additional OWNER users via API:

**Endpoint:** `POST /skillama/api/admin/users`

**Headers:**
```
Authorization: Bearer <owner-jwt-token>
```

**Request Body:**
```json
{
  "name": "New Owner",
  "email": "newowner@example.com",
  "password": "securePassword123",
  "role": "OWNER",
  "active": true,
  "gender": "MALE"
}
```

### Option 3: Create Initial Owner Script

You can create a migration script similar to `PasswordMigrationScript.java` to create the first owner on application startup.

## Important Notes

1. **Security**: OWNER users have full system access including:
   - Creating ADMIN and OWNER users
   - Managing all users, courses, and assignments
   - Accessing all analytics
   - System settings

2. **Protection**: OWNER users cannot be:
   - Deleted (soft delete is prevented)
   - Have their role changed (except by another OWNER)

3. **Best Practice**: 
   - Create at least 2 OWNER users for redundancy
   - Use strong passwords
   - Keep OWNER credentials secure



