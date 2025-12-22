#!/bin/bash

# Test Access Control Endpoint
# This script tests the /skillama/user-profile/access-control endpoint

BASE_URL="https://prwatech.xyz"
COURSE_ID="6817bbe4cb6b8135daecc428"

echo "=========================================="
echo "Testing Access Control Endpoint"
echo "=========================================="
echo ""

# Test 1: Guest User (No Authentication)
echo "Test 1: Guest User (No Auth)"
echo "----------------------------------------"
curl -X GET "${BASE_URL}/skillama/user-profile/access-control?courseId=${COURSE_ID}" \
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
  -v \
  | jq '.' 2>/dev/null || cat

echo ""
echo ""

# Test 2: Guest User with Session Cookie (from previous request)
echo "Test 2: Guest User with Session Cookie"
echo "----------------------------------------"
if [ -f cookies.txt ]; then
  curl -X GET "${BASE_URL}/skillama/user-profile/access-control?courseId=${COURSE_ID}" \
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
    -v \
    | jq '.' 2>/dev/null || cat
else
  echo "No cookies file found. Run Test 1 first."
fi

echo ""
echo ""

# Test 3: Logged-in User (with Bearer Token)
echo "Test 3: Logged-in User (Bearer Token)"
echo "----------------------------------------"
echo "Replace YOUR_JWT_TOKEN with actual token from login"
echo ""
read -p "Enter JWT Token (or press Enter to skip): " JWT_TOKEN

if [ -n "$JWT_TOKEN" ]; then
  curl -X GET "${BASE_URL}/skillama/user-profile/access-control?courseId=${COURSE_ID}" \
    -H 'Accept: application/json, text/plain, */*' \
    -H 'Accept-Language: en-US,en;q=0.9' \
    -H 'Authorization: Bearer '"${JWT_TOKEN}" \
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
    -v \
    | jq '.' 2>/dev/null || cat
else
  echo "Skipped. To test logged-in user:"
  echo "curl -X GET '${BASE_URL}/skillama/user-profile/access-control?courseId=${COURSE_ID}' \\"
  echo "  -H 'Authorization: Bearer YOUR_JWT_TOKEN' \\"
  echo "  -H 'Accept: application/json'"
fi

echo ""
echo ""

# Test 4: Without courseId (should use default/guest course)
echo "Test 4: Without courseId Parameter"
echo "----------------------------------------"
curl -X GET "${BASE_URL}/skillama/user-profile/access-control" \
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
  -v \
  | jq '.' 2>/dev/null || cat

echo ""
echo "=========================================="
echo "Tests Complete"
echo "=========================================="

