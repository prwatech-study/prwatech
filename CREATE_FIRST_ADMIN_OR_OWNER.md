# How to Create the First Admin or Owner User

## Problem
The API requires an existing OWNER to create new ADMIN/OWNER users, creating a chicken-and-egg problem for the first owner/admin.

## Solutions

### Option 1: Automatic Startup Script (Recommended) ⭐

A startup script can automatically create the first OWNER on application startup.

**Steps:**

1. **Enable the script:**
   - Open `src/main/java/com/prwatech/skillama/script/FirstOwnerSetupScript.java`
   - Change `AUTO_CREATE_FIRST_OWNER` from `false` to `true`:
   ```java
   private static final boolean AUTO_CREATE_FIRST_OWNER = true;
   ```

2. **Configure the first owner details (optional):**
   ```java
   private static final String FIRST_OWNER_EMAIL = "owner@prwatech.com";
   private static final String FIRST_OWNER_PASSWORD = "ChangeThisPassword123!";
   private static final String FIRST_OWNER_NAME = "System Owner";
   ```

3. **Restart the application:**
   - The script will run on startup
   - Check the logs for the created owner credentials
   - **IMPORTANT:** Change the password immediately after first login!

**What it does:**
- Checks if any OWNER users exist
- If no OWNER exists, creates one with the configured email/password
- If a user with that email exists, promotes them to OWNER
- Logs the credentials to the console (check application logs)

**Security Note:** After creating the first owner, set `AUTO_CREATE_FIRST_OWNER = false` to disable the script.

---

### Option 2: Direct MongoDB Update (Quick & Easy) ⭐

Connect to your MongoDB database and update an existing user:

**Using MongoDB Shell:**
```javascript
// Connect to MongoDB
use skillamaDB  // or your database name

// Find a user to promote to OWNER
db.users.find({ email: "your-email@example.com" })

// Update the user's role to OWNER
db.users.updateOne(
  { email: "your-email@example.com" },
  { 
    $set: { 
      role: "OWNER",
      updatedAt: new Date()
    }
  }
)

// Verify the update
db.users.findOne({ email: "your-email@example.com" })
```

**Using MongoDB Compass (GUI):**
1. Connect to your MongoDB instance
2. Navigate to `skillamaDB` (or your database) → `users` collection
3. Find the user by email
4. Edit the document:
   - Set `role` field to `"OWNER"`
   - Update `updatedAt` to current date
5. Save the document

**To create ADMIN instead of OWNER:**
```javascript
db.users.updateOne(
  { email: "your-email@example.com" },
  { 
    $set: { 
      role: "ADMIN",  // Change to ADMIN
      updatedAt: new Date()
    }
  }
)
```

---

### Option 3: Create New User in MongoDB

If no users exist, create a new user directly in MongoDB:

```javascript
use skillamaDB

db.users.insertOne({
  name: "System Owner",
  email: "owner@prwatech.com",
  password: "$2a$10$HYunSfuYwLxf8CrqhW7QHO...", // You'll need to hash the password
  role: "OWNER",
  active: true,
  createdAt: new Date(),
  updatedAt: new Date(),
  createdBy: "SYSTEM",
  updatedBy: "SYSTEM"
})
```

**Note:** You'll need to hash the password. Use the login endpoint first to create a user, then update the role in MongoDB.

---

### Option 4: Use Login Endpoint + MongoDB Update

1. **Create a normal user first:**
   - Use the registration/login endpoint to create a user account
   - This will create a user with role "USER"

2. **Then promote to OWNER/ADMIN in MongoDB:**
   ```javascript
   db.users.updateOne(
     { email: "your-email@example.com" },
     { 
       $set: { 
         role: "OWNER",  // or "ADMIN"
         updatedAt: new Date()
       }
     }
   )
   ```

---

## Step-by-Step Guide (Recommended: MongoDB Update)

### For Creating First OWNER:

1. **Find your MongoDB connection string:**
   - Check `application.properties`: `skillama.mongodb.uri`
   - Or connect to: `mongodb://prwatech:PrwaT3ch@13.126.60.121:27017/skillamaDB?authSource=admin`

2. **Connect using MongoDB Compass or MongoDB Shell:**
   ```bash
   # Using MongoDB Shell
   mongosh "mongodb://prwatech:PrwaT3ch@13.126.60.121:27017/skillamaDB?authSource=admin"
   ```

3. **Find a user (or create one via registration):**
   ```javascript
   db.users.find({ email: "your-email@example.com" })
   ```

4. **Update to OWNER:**
   ```javascript
   db.users.updateOne(
     { email: "your-email@example.com" },
     { 
       $set: { 
         role: "OWNER",
         updatedAt: new Date()
       }
     }
   )
   ```

5. **Verify:**
   ```javascript
   db.users.findOne({ email: "your-email@example.com" })
   // Should show: role: "OWNER"
   ```

6. **Login with that email:**
   - Use the login endpoint: `POST /skillama/users/login`
   - You'll get a JWT token
   - Use this token to access admin endpoints

---

## After Creating First Owner

Once you have an OWNER user, you can:

1. **Create additional OWNER users via API:**
   ```http
   POST /skillama/api/admin/users
   Authorization: Bearer <owner-jwt-token>
   
   {
     "name": "New Owner",
     "email": "newowner@example.com",
     "password": "securePassword123",
     "role": "OWNER",
     "active": true
   }
   ```

2. **Create ADMIN users via API:**
   ```http
   POST /skillama/api/admin/users
   Authorization: Bearer <owner-jwt-token>
   
   {
     "name": "Admin User",
     "email": "admin@example.com",
     "password": "securePassword123",
     "role": "ADMIN",
     "active": true
   }
   ```

3. **Promote existing users to ADMIN by email:**
   ```http
   PUT /skillama/api/admin/users/promote-to-admin?email=user@example.com
   Authorization: Bearer <owner-jwt-token>
   ```

---

## Quick Reference

### MongoDB Database Details:
- **Database:** `skillamaDB`
- **Collection:** `users`
- **Connection:** Check `application.properties` for `skillama.mongodb.uri`

### User Role Values:
- `"USER"` - Normal user
- `"ADMIN"` - Admin user (can manage users, courses, etc.)
- `"OWNER"` - Owner user (can create admins/owners, full access)

### Important Security Notes:

1. **Change Default Passwords:** If using the startup script, change the password immediately after first login

2. **Create Multiple Owners:** Create at least 2 OWNER users for redundancy

3. **Disable Startup Script:** After creating the first owner, disable the startup script:
   ```java
   private static final boolean AUTO_CREATE_FIRST_OWNER = false;
   ```

4. **Use Strong Passwords:** Always use strong, unique passwords for OWNER accounts

5. **Protect OWNER Accounts:** OWNER users cannot be deleted or have their role changed (except by another OWNER)

---

## Troubleshooting

### Issue: "User not found" in MongoDB
- Make sure you're using the correct database (`skillamaDB`)
- Check the email address is correct
- User might be in a different database

### Issue: "Role not updating"
- Make sure the role value is exactly `"OWNER"` or `"ADMIN"` (case-sensitive)
- Check MongoDB connection is working
- Verify the update query executed successfully

### Issue: "Still can't access admin endpoints"
- Make sure you're using the JWT token from login
- Token should be in format: `Bearer <token>`
- Check the user's role was actually updated in MongoDB
- Try logging out and logging back in to get a new token

---

## Summary

**Easiest Method:** Use MongoDB to update an existing user's role to `"OWNER"`, then login to get a JWT token.

**Automated Method:** Enable the startup script, restart the application, check logs for credentials.

**After First Owner:** Use the API endpoints to create additional owners/admins.

