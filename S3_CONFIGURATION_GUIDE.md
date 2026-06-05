# AWS S3 Configuration Guide for Curriculum Image Upload

## Overview
The curriculum image upload feature now supports direct upload to AWS S3, following the path pattern:
```
courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.{ext}
```

## Configuration

### 1. Add AWS S3 Configuration to `application.properties`

Add the following properties to `src/main/resources/application.properties`:

```properties
# AWS S3 Configuration
# Note: For EC2 instances, IAM role credentials are used automatically
aws.s3.bucket-name=presentation-image-courses
aws.s3.region=ap-south-1
```

**Important:** If your EC2 instance has an IAM role with S3 permissions, you don't need to configure access keys. The application will automatically use the IAM role credentials via the default credential provider chain.

### 2. AWS S3 Bucket Setup

1. **Create S3 Bucket** (if not already created):
   - Bucket name: `presentation-image-courses`
   - Region: `ap-south-1` (Mumbai)
   - Public access: Configure based on your requirements

2. **Configure CORS** (if needed for frontend access):
   ```json
   [
       {
           "AllowedHeaders": ["*"],
           "AllowedMethods": ["GET", "PUT", "POST", "DELETE"],
           "AllowedOrigins": ["*"],
           "ExposeHeaders": []
       }
   ]
   ```

3. **Bucket Policy** (for public read access to images):
   ```json
   {
       "Version": "2012-10-17",
       "Statement": [
           {
               "Sid": "PublicReadGetObject",
               "Effect": "Allow",
               "Principal": "*",
               "Action": "s3:GetObject",
               "Resource": "arn:aws:s3:::presentation-image-courses/*"
           }
       ]
   }
   ```

### 3. IAM Role Setup (Recommended for EC2)

**For EC2 Instances with IAM Roles (Recommended):**

Attach an IAM role to your EC2 instance with the following policy:

**Policy JSON:**
```json
{
    "Version": "2012-10-17",
    "Statement": [
        {
            "Effect": "Allow",
            "Action": [
                "s3:PutObject",
                "s3:GetObject",
                "s3:DeleteObject"
            ],
            "Resource": "arn:aws:s3:::presentation-image-courses/*"
        },
        {
            "Effect": "Allow",
            "Action": [
                "s3:ListBucket"
            ],
            "Resource": "arn:aws:s3:::presentation-image-courses"
        }
    ]
}
```

**Steps:**
1. Go to AWS IAM Console
2. Create a new IAM role (e.g., `EC2-S3-Upload-Role`)
3. Attach the policy above
4. Attach the role to your EC2 instance
5. **No access keys needed!** The application will automatically use the IAM role credentials.

**For Local Development (Alternative):**

If you need to test locally, you can:
- Set environment variables: `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
- Or use AWS credentials file: `~/.aws/credentials`
- The default credential provider chain will pick these up automatically

### 4. How Credentials Work

The application uses AWS SDK's **Default Credential Provider Chain**, which checks credentials in this order:

1. **Environment Variables** (`AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`)
2. **Java System Properties**
3. **EC2 Instance Profile** (IAM role) - **This is what you're using!**
4. **ECS Container Credentials**
5. **Other credential sources**

**For EC2 with IAM Role:**
- No configuration needed in `application.properties`
- The IAM role attached to the EC2 instance is automatically used
- Most secure and recommended approach

**For Local Development:**
- Set environment variables: `AWS_ACCESS_KEY_ID` and `AWS_SECRET_ACCESS_KEY`
- Or use AWS credentials file: `~/.aws/credentials`

## Path Structure

The S3 upload follows this pattern:
```
courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.{ext}
```

**Example:**
```
courses/6817bbe4cb6b8135daecc428/modules/03/lessons/01/slides/01.png
```

Where:
- `courseId`: The course ID from the module
- `moduleOrder`: Module order (formatted as 2 digits: "01", "02", "03", etc.)
- `lessonOrder`: Submodule/lesson order (formatted as 2 digits: "01", "02", "28", etc.)
- `slideNumber`: Slide number (default: "01", formatted as 2 digits)
- `ext`: File extension (.png, .jpg, etc.)

## How It Works

The application uses AWS SDK's default credential provider chain:
- **On EC2:** Automatically uses the IAM role attached to the instance
- **Local Development:** Can use environment variables or AWS credentials file
- **No hardcoded credentials needed!**

The S3 client is always created and will use whatever credentials are available in the credential chain.

## Testing

### 1. Test S3 Upload
```bash
curl -X POST \
  http://localhost:9090/api/admin/curriculum/submodules/{moduleId}/{idx}/image \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -F "image=@/path/to/image.png"
```

### 2. Verify Upload
Check the S3 bucket to confirm the file was uploaded with the correct path structure.

### 3. Test Image Access
Access the uploaded image via the returned URL:
```
https://presentation-image-courses.s3.ap-south-1.amazonaws.com/courses/{courseId}/modules/{moduleOrder}/lessons/{lessonOrder}/slides/{slideNumber}.png
```

## Troubleshooting

### Issue: "Failed to upload file to S3"
- **Check:** AWS credentials are correct
- **Check:** IAM user has proper permissions
- **Check:** Bucket name is correct
- **Check:** Region matches bucket region

### Issue: "S3Client is null"
- **Check:** S3Config bean is being created properly
- **Check:** IAM role is attached to EC2 instance
- **Check:** IAM role has S3 permissions
- **Solution:** Verify IAM role is correctly attached and has the required permissions

### Issue: "Access Denied"
- **Check:** IAM user has `s3:PutObject` permission
- **Check:** Bucket policy allows uploads
- **Check:** Bucket CORS configuration (if accessing from frontend)

## Security Best Practices

1. **Use IAM Roles (Recommended)**
   - ✅ **You're already doing this!** Using IAM roles is the most secure approach
   - No credentials to manage or rotate
   - Credentials are automatically rotated by AWS

2. **Limit Permissions**
   - Only grant necessary S3 permissions (PutObject, GetObject, DeleteObject)
   - Use bucket-specific policies
   - Follow principle of least privilege

3. **Monitor Access**
   - Enable CloudTrail for S3 API calls
   - Monitor S3 access logs
   - Set up alerts for unusual activity

4. **For Local Development**
   - Use environment variables or AWS credentials file
   - Never commit credentials to Git
   - Use `.gitignore` for credential files

## Files Modified

1. **build.gradle** - Added AWS S3 SDK dependency
2. **S3Config.java** - S3 client configuration
3. **FileStorageServiceImpl.java** - S3 upload/delete implementation
4. **AdminCurriculumImageController.java** - Updated to use S3 path structure
5. **application.properties** - Added S3 configuration properties

## Study materials bucket (downloadable course content)

Downloadable files (PDF, DOC, ZIP, etc.) use a **separate** bucket from curriculum images:

| Setting | Default |
|---------|---------|
| Bucket | `skillama-course-materials` |
| Region | `ap-south-1` |
| Base URL | `https://skillama-course-materials.s3.ap-south-1.amazonaws.com` |
| Key pattern | `courses/{courseId}/materials/{timestamp}-{uuid}-{name}.pdf` |

`application.properties`:

```properties
aws.s3.study-materials-bucket-name=skillama-course-materials
file.upload.s3.study-materials-base-url=https://skillama-course-materials.s3.ap-south-1.amazonaws.com
```

Override via env: `STUDY_MATERIALS_S3_BUCKET`, `STUDY_MATERIALS_S3_BASE_URL`.

Extend the EC2 IAM role policy with:

```json
{
    "Effect": "Allow",
    "Action": ["s3:PutObject", "s3:GetObject", "s3:DeleteObject"],
    "Resource": "arn:aws:s3:::skillama-course-materials/*"
}
```

Apply the same CORS / public-read bucket policy pattern as `presentation-image-courses` if learners download directly from S3 URLs.

## Next Steps

1. Create `skillama-course-materials` bucket in AWS (if not exists)
2. Configure AWS S3 credentials / IAM role for both buckets
3. Test image upload endpoint (`presentation-image-courses`)
4. Test study material upload (`skillama-course-materials`)
5. Verify files are accessible via S3 URLs

