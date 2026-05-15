# Frontend Documentation: Curriculum Image Upload

## Overview
This document provides frontend developers with complete API documentation and integration guide for the curriculum image upload feature. This allows administrators to upload and manage images for curriculum submodules.

**Base URL:** `/api/admin/curriculum/submodules`

**Authentication:** All endpoints require JWT Bearer token with ADMIN or OWNER role.

---

## API Endpoints

### 1. Upload Image for Submodule
Uploads an image file for a specific curriculum submodule and updates the submodule's `imagePath` field.

**Endpoint:** `POST /api/admin/curriculum/submodules/{moduleId}/{idx}/image`

**Path Parameters:**
- `moduleId` (string, required) - The module ID
- `idx` (integer, required) - The submodule index (0-based)

**Headers:**
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: multipart/form-data
```

**Request Body:**
- Form data with field name: `image`
- File type: Image file (JPG, PNG, GIF, WebP)
- Max file size: 5MB

**Success Response (200 OK):**
```json
{
  "status": 200,
  "data": {
    "moduleId": "6817d523d236429528da7e08",
    "submoduleIndex": 0,
    "imagePath": "https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png",
    "imageUrl": "https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png",
    "fileName": "01.png",
    "fileSize": 245678,
    "contentType": "image/png"
  }
}
```

**Error Responses:**

| Status Code | Error Code | Description |
|------------|------------|-------------|
| 400 | `INVALID_FILE_TYPE` | Invalid file type. Only JPG, PNG, GIF, and WebP are allowed. |
| 401 | `UNAUTHORIZED` | Missing or invalid JWT token, or user doesn't have ADMIN/OWNER role |
| 404 | `SUBMODULE_NOT_FOUND` | Submodule not found |
| 413 | `FILE_TOO_LARGE` | File size exceeds maximum allowed size of 5MB |
| 500 | `UPLOAD_ERROR` | Failed to upload image |

**Error Response Format:**
```json
{
  "status": 400,
  "error": "INVALID_FILE_TYPE",
  "message": "Invalid file type. Only JPG, PNG, GIF, and WebP are allowed."
}
```

---

### 2. Upload Image (Returns URL Only)
Uploads an image file and returns the image URL. This endpoint can be used when creating a new submodule or updating an existing one. The frontend can then include the returned URL in the submodule creation/update request.

**Endpoint:** `POST /api/admin/curriculum/submodules/image`

**Headers:**
```
Authorization: Bearer {JWT_TOKEN}
Content-Type: multipart/form-data
```

**Request Body:**
- Form data with field name: `image`
- File type: Image file (JPG, PNG, GIF, WebP)
- Max file size: 5MB

**Success Response (200 OK):**
```json
{
  "status": 200,
  "data": {
    "imagePath": "https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png",
    "imageUrl": "https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png",
    "fileName": "01.png",
    "fileSize": 245678,
    "contentType": "image/png"
  }
}
```

**Error Responses:** Same as endpoint #1 (except 404 SUBMODULE_NOT_FOUND)

---

### 3. Delete Submodule Image
Deletes the image associated with a submodule. The image file is removed from S3 and the submodule's `imagePath` field is set to null.

**Endpoint:** `DELETE /api/admin/curriculum/submodules/{moduleId}/{idx}/image`

**Path Parameters:**
- `moduleId` (string, required) - The module ID
- `idx` (integer, required) - The submodule index (0-based)

**Headers:**
```
Authorization: Bearer {JWT_TOKEN}
```

**Success Response (200 OK):**
```json
{
  "status": 200,
  "data": {
    "moduleId": "6817d523d236429528da7e08",
    "submoduleIndex": 0,
    "imagePath": null
  }
}
```

**Error Responses:**

| Status Code | Error Code | Description |
|------------|------------|-------------|
| 401 | `UNAUTHORIZED` | Missing or invalid JWT token, or user doesn't have ADMIN/OWNER role |
| 404 | `SUBMODULE_NOT_FOUND` | Submodule not found |
| 404 | `IMAGE_NOT_FOUND` | Image not found for this submodule |

---

## Frontend Integration Guide

### Step 1: File Upload Component

Create a file input component with image preview:

```jsx
import React, { useState } from 'react';

const ImageUploadComponent = ({ moduleId, submoduleIndex, onUploadSuccess, onError }) => {
  const [selectedFile, setSelectedFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [uploading, setUploading] = useState(false);

  const handleFileSelect = (event) => {
    const file = event.target.files[0];
    
    // Validate file type
    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      onError('Invalid file type. Only JPG, PNG, GIF, and WebP are allowed.');
      return;
    }
    
    // Validate file size (5MB)
    const maxSize = 5 * 1024 * 1024; // 5MB in bytes
    if (file.size > maxSize) {
      onError('File size exceeds maximum allowed size of 5MB');
      return;
    }
    
    setSelectedFile(file);
    
    // Create preview
    const reader = new FileReader();
    reader.onloadend = () => {
      setPreview(reader.result);
    };
    reader.readAsDataURL(file);
  };

  const handleUpload = async () => {
    if (!selectedFile) return;
    
    setUploading(true);
    
    try {
      const formData = new FormData();
      formData.append('image', selectedFile);
      
      const token = localStorage.getItem('token'); // or your token storage method
      
      const response = await fetch(
        `/api/admin/curriculum/submodules/${moduleId}/${submoduleIndex}/image`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`
          },
          body: formData
        }
      );
      
      const data = await response.json();
      
      if (response.ok) {
        onUploadSuccess(data.data);
        setSelectedFile(null);
        setPreview(null);
      } else {
        onError(data.message || 'Upload failed');
      }
    } catch (error) {
      onError('Network error: ' + error.message);
    } finally {
      setUploading(false);
    }
  };

  return (
    <div className="image-upload-component">
      <input
        type="file"
        accept="image/jpeg,image/jpg,image/png,image/gif,image/webp"
        onChange={handleFileSelect}
        disabled={uploading}
      />
      
      {preview && (
        <div className="preview-container">
          <img src={preview} alt="Preview" style={{ maxWidth: '300px', maxHeight: '300px' }} />
        </div>
      )}
      
      {selectedFile && (
        <div>
          <p>Selected: {selectedFile.name} ({(selectedFile.size / 1024).toFixed(2)} KB)</p>
          <button onClick={handleUpload} disabled={uploading}>
            {uploading ? 'Uploading...' : 'Upload Image'}
          </button>
        </div>
      )}
    </div>
  );
};

export default ImageUploadComponent;
```

### Step 2: Using Axios (Alternative)

```javascript
import axios from 'axios';

const uploadSubmoduleImage = async (moduleId, submoduleIndex, imageFile, token) => {
  const formData = new FormData();
  formData.append('image', imageFile);
  
  try {
    const response = await axios.post(
      `/api/admin/curriculum/submodules/${moduleId}/${submoduleIndex}/image`,
      formData,
      {
        headers: {
          'Authorization': `Bearer ${token}`,
          'Content-Type': 'multipart/form-data'
        }
      }
    );
    
    return response.data;
  } catch (error) {
    if (error.response) {
      // Server responded with error
      throw new Error(error.response.data.message || 'Upload failed');
    } else if (error.request) {
      // Request made but no response
      throw new Error('Network error. Please check your connection.');
    } else {
      // Something else happened
      throw new Error('An error occurred: ' + error.message);
    }
  }
};

// Usage
try {
  const result = await uploadSubmoduleImage(moduleId, submoduleIndex, imageFile, token);
  console.log('Image uploaded:', result.data.imageUrl);
  // Update your submodule with the imagePath
} catch (error) {
  console.error('Upload error:', error.message);
  // Show error to user
}
```

### Step 3: Upload Before Creating Submodule

If you want to upload the image first, then create/update the submodule:

```javascript
// Step 1: Upload image
const uploadImageOnly = async (imageFile, token) => {
  const formData = new FormData();
  formData.append('image', imageFile);
  
  const response = await fetch('/api/admin/curriculum/submodules/image', {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`
    },
    body: formData
  });
  
  const data = await response.json();
  
  if (!response.ok) {
    throw new Error(data.message || 'Upload failed');
  }
  
  return data.data.imagePath; // Returns the S3 URL
};

// Step 2: Create/Update submodule with imagePath
const createSubmodule = async (moduleId, submoduleData, token) => {
  const response = await fetch(`/skillama/curriculum/module/${moduleId}/submodule`, {
    method: 'POST',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      ...submoduleData,
      imagePath: submoduleData.imagePath // Use the URL from step 1
    })
  });
  
  return response.json();
};

// Usage flow
const handleCreateSubmodule = async () => {
  try {
    // 1. Upload image first
    const imagePath = await uploadImageOnly(selectedFile, token);
    
    // 2. Create submodule with imagePath
    const submodule = {
      label: 'New Lecture',
      scriptText: 'Lecture content',
      isPracticalRequired: false,
      order: 1,
      imagePath: imagePath // Use the uploaded image URL
    };
    
    await createSubmodule(moduleId, submodule, token);
    
    // Success!
  } catch (error) {
    // Handle error
    console.error('Error:', error.message);
  }
};
```

### Step 4: Delete Image

```javascript
const deleteSubmoduleImage = async (moduleId, submoduleIndex, token) => {
  const response = await fetch(
    `/api/admin/curriculum/submodules/${moduleId}/${submoduleIndex}/image`,
    {
      method: 'DELETE',
      headers: {
        'Authorization': `Bearer ${token}`
      }
    }
  );
  
  const data = await response.json();
  
  if (!response.ok) {
    throw new Error(data.message || 'Delete failed');
  }
  
  return data;
};

// Usage
try {
  await deleteSubmoduleImage(moduleId, submoduleIndex, token);
  // Image deleted successfully
  // Update UI to remove image preview
} catch (error) {
  console.error('Delete error:', error.message);
}
```

---

## Error Handling

### Common Error Scenarios

1. **Invalid File Type**
   ```javascript
   if (error.response?.status === 400 && error.response?.data?.error === 'INVALID_FILE_TYPE') {
     showError('Please select a valid image file (JPG, PNG, GIF, or WebP)');
   }
   ```

2. **File Too Large**
   ```javascript
   if (error.response?.status === 413) {
     showError('File is too large. Maximum size is 5MB.');
   }
   ```

3. **Unauthorized**
   ```javascript
   if (error.response?.status === 401) {
     // Redirect to login or show unauthorized message
     redirectToLogin();
   }
   ```

4. **Submodule Not Found**
   ```javascript
   if (error.response?.status === 404) {
     showError('Submodule not found. Please refresh the page.');
   }
   ```

### Complete Error Handler

```javascript
const handleUploadError = (error) => {
  if (error.response) {
    const { status, data } = error.response;
    
    switch (status) {
      case 400:
        showError(data.message || 'Invalid request');
        break;
      case 401:
        showError('Unauthorized. Please login again.');
        redirectToLogin();
        break;
      case 404:
        showError('Submodule not found');
        break;
      case 413:
        showError('File is too large. Maximum size is 5MB.');
        break;
      case 500:
        showError('Server error. Please try again later.');
        break;
      default:
        showError(data.message || 'An error occurred');
    }
  } else if (error.request) {
    showError('Network error. Please check your connection.');
  } else {
    showError('An unexpected error occurred');
  }
};
```

---

## Image Path Structure

The uploaded images follow this S3 path pattern:
```
courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.{ext}
```

**Example:**
```
https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png
```

Where:
- `courseId`: The course ID from the module
- `moduleOrder`: Module order (2 digits: "01", "02", "03", etc.)
- `lessonOrder`: Submodule/lesson order (2 digits: "01", "02", "28", etc.)
- `slideNumber`: Slide number (default: "01")
- `ext`: File extension (.png, .jpg, etc.)

**Note:** The path is automatically generated by the backend based on the module and submodule order. You don't need to construct it manually.

---

## UI/UX Recommendations

### 1. File Selection
- Show file type restrictions clearly
- Display max file size (5MB)
- Provide drag-and-drop support
- Show file size before upload

### 2. Image Preview
- Show preview before upload
- Display current image if exists
- Allow replacing existing image
- Show loading state during upload

### 3. Upload Progress
- Show upload progress indicator
- Disable form during upload
- Show success message after upload
- Handle errors gracefully

### 4. Example UI Component

```jsx
const SubmoduleImageUpload = ({ moduleId, submoduleIndex, currentImageUrl, onImageChange }) => {
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState(null);
  const [preview, setPreview] = useState(null);

  const handleFileChange = async (event) => {
    const file = event.target.files[0];
    if (!file) return;

    // Validation
    const maxSize = 5 * 1024 * 1024; // 5MB
    if (file.size > maxSize) {
      setError('File size exceeds 5MB');
      return;
    }

    const allowedTypes = ['image/jpeg', 'image/jpg', 'image/png', 'image/gif', 'image/webp'];
    if (!allowedTypes.includes(file.type)) {
      setError('Invalid file type. Only JPG, PNG, GIF, and WebP are allowed.');
      return;
    }

    setError(null);
    setUploading(true);

    try {
      const formData = new FormData();
      formData.append('image', file);

      const token = getAuthToken();
      const response = await fetch(
        `/api/admin/curriculum/submodules/${moduleId}/${submoduleIndex}/image`,
        {
          method: 'POST',
          headers: {
            'Authorization': `Bearer ${token}`
          },
          body: formData
        }
      );

      const data = await response.json();

      if (response.ok) {
        onImageChange(data.data.imageUrl);
        setPreview(data.data.imageUrl);
        setError(null);
      } else {
        setError(data.message || 'Upload failed');
      }
    } catch (err) {
      setError('Network error: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async () => {
    if (!confirm('Are you sure you want to delete this image?')) return;

    setUploading(true);
    try {
      const token = getAuthToken();
      const response = await fetch(
        `/api/admin/curriculum/submodules/${moduleId}/${submoduleIndex}/image`,
        {
          method: 'DELETE',
          headers: {
            'Authorization': `Bearer ${token}`
          }
        }
      );

      if (response.ok) {
        onImageChange(null);
        setPreview(null);
      } else {
        const data = await response.json();
        setError(data.message || 'Delete failed');
      }
    } catch (err) {
      setError('Network error: ' + err.message);
    } finally {
      setUploading(false);
    }
  };

  const displayImage = preview || currentImageUrl;

  return (
    <div className="submodule-image-upload">
      <label>Submodule Image</label>
      
      {displayImage && (
        <div className="image-preview">
          <img src={displayImage} alt="Submodule" />
          <button onClick={handleDelete} disabled={uploading}>
            {uploading ? 'Deleting...' : 'Delete Image'}
          </button>
        </div>
      )}
      
      <input
        type="file"
        accept="image/jpeg,image/jpg,image/png,image/gif,image/webp"
        onChange={handleFileChange}
        disabled={uploading}
      />
      
      {uploading && <div className="upload-progress">Uploading...</div>}
      {error && <div className="error-message">{error}</div>}
      
      <small>
        Supported formats: JPG, PNG, GIF, WebP | Max size: 5MB
      </small>
    </div>
  );
};
```

---

## Testing Checklist

### Manual Testing
- [ ] Upload JPG image (valid)
- [ ] Upload PNG image (valid)
- [ ] Upload GIF image (valid)
- [ ] Upload WebP image (valid)
- [ ] Upload invalid file type (should show error)
- [ ] Upload file > 5MB (should show error)
- [ ] Upload image for non-existent submodule (should show error)
- [ ] Delete image from submodule
- [ ] Verify image URL is accessible
- [ ] Test with ADMIN role (should work)
- [ ] Test with OWNER role (should work)
- [ ] Test with unauthorized user (should show 401 error)
- [ ] Test network error handling
- [ ] Test with missing token (should show 401 error)

### Integration Testing
- [ ] Upload image → Verify imagePath is updated in submodule
- [ ] Upload image → Replace with new image → Verify old image is replaced
- [ ] Delete image → Verify imagePath is set to null
- [ ] Upload image → Create submodule → Verify image is included
- [ ] Test image preview before upload
- [ ] Test upload progress indicator

---

## API Base URL

**Development:**
```
http://localhost:9090
```

**Production:**
```
https://your-production-domain.com
```

**Full Endpoint Examples:**
```
POST http://localhost:9090/api/admin/curriculum/submodules/{moduleId}/{idx}/image
POST http://localhost:9090/api/admin/curriculum/submodules/image
DELETE http://localhost:9090/api/admin/curriculum/submodules/{moduleId}/{idx}/image
```

---

## Support

For questions or issues:
1. Check error messages in the response
2. Verify authentication token is valid
3. Ensure user has ADMIN or OWNER role
4. Check network connectivity
5. Verify file meets requirements (type, size)

---

## Related Documentation

- Backend Implementation: `CURRICULUM_IMAGE_UPLOAD_IMPLEMENTATION.md`
- S3 Configuration: `S3_CONFIGURATION_GUIDE.md`
- API Documentation: `SKILLAMA_API_DOCUMENTATION.md`

