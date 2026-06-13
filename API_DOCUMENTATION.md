# API Documentation - Protest App Backend

Once the application is running, the default server base URL is:
`http://localhost:8080`

All endpoints are stateless and require the HTTP Header `Authorization: Bearer <JWT_TOKEN>` for secured paths.

---

## 1. Authentication & Onboarding

### Request Mobile OTP
- **Endpoint**: `POST /api/v1/auth/otp/request`
- **Request Payload**:
```json
{
  "mobileNumber": "+919876543210"
}
```

### Verify OTP & Get JWT
- **Endpoint**: `POST /api/v1/auth/otp/verify`
- **Request Payload**:
```json
{
  "mobileNumber": "+919876543210",
  "otpCode": "123456"
}
```
- **Response**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "role": "ROLE_CITIZEN",
  "isNewUser": true
}
```

### Link Voter ID (Required to raise issues)
- **Endpoint**: `POST /api/v1/auth/voter-id/link`
- **Request Payload**:
```json
{
  "voterId": "ABC1234567"
}
```

---

## 2. Issues Feed & Workflow (Citizens & Approvers)

### Raise a Local Issue (Citizen Only)
- **Endpoint**: `POST /api/v1/issues`
- **Request Payload**:
```json
{
  "title": "Broken Water Pipe in Ward 12",
  "description": "The main water pipe is broken and leaking since yesterday.",
  "photoUrls": ["https://assets.example.com/images/leak1.jpg"],
  "latitude": 28.6139,
  "longitude": 77.2090
}
```

### Like/Unlike an Issue
- **Endpoint**: `POST /api/v1/issues/{issueId}/like`

### Send Message / Notify Creator
- **Endpoint**: `POST /api/v1/issues/{issueId}/message`
- **Request Payload**:
```json
{
  "content": "Please upload a clearer picture of the location."
}
```

### Resolve Issue (Creator or Approver)
- **Endpoint**: `POST /api/v1/issues/{issueId}/resolve`

### Close and Validate Issue (Creator Citizen Only)
- **Endpoint**: `POST /api/v1/issues/{issueId}/close`

### Fetch Localized Trending Feed (Hacker News Time Decay Algorithm)
- **Endpoint**: `GET /api/v1/issues/feed`
- **Params**: `latitude=28.6139&longitude=77.2090&skip=0&limit=20`

### Report Issue/Post
- **Endpoint**: `POST /api/v1/issues/{issueId}/report`
- **Request Payload**:
```json
{
  "reason": "Inappropriate profile picture"
}
```

---

## 3. Politician Dashboard Actions (Approvers)

### Verify/Checklist Issue
- **Endpoint**: `POST /api/v1/issues/approver/{issueId}/checklist`
- **Request Payload**:
```json
{
  "checkName": "CONTENT_APPROPRIATE",
  "checked": true
}
```

### Approve Issue (Populates Approver Name & Designation)
- **Endpoint**: `POST /api/v1/issues/approver/{issueId}/approve`

### Post Ephemeral Program News (Expiry TTL index configured)
- **Endpoint**: `POST /api/v1/news/approver`
- **Request Payload**:
```json
{
  "content": "Inaugurating the new public park tomorrow at 10 AM.",
  "photoUrls": ["https://assets.example.com/park.jpg"],
  "programDate": "2026-05-26T10:00:00Z",
  "expirationDate": "2026-05-27T10:00:00Z",
  "locationCode": "WARD-01"
}
```

### Get News Bulletin for Location
- **Endpoint**: `GET /api/v1/news/bulletin`
- **Params**: `locationCode=WARD-01`

---

## 4. Surveys & Lobbying Analytics

### Create a Survey (Corporate / Media / Politician)
- **Endpoint**: `POST /api/v1/surveys`
- **Request Payload**:
```json
{
  "title": "Public Transit Survey",
  "description": "Gauge sentiment on the proposed metro route extension.",
  "targetBoundaryCode": "WARD-01",
  "expirationDate": "2026-06-25T17:00:00Z",
  "questions": [
    {
      "questionText": "Do you support the metro extension route?",
      "options": ["Strongly Support", "Neutral", "Oppose"]
    }
  ]
}
```

### Submit Response (Double voting restricted)
- **Endpoint**: `POST /api/v1/surveys/{surveyId}/respond`
- **Request Payload**:
```json
{
  "answers": {
    "question_uuid_here": ["Strongly Support"]
  }
}
```

### Get Aggregated Lobbying Sentiment Report (Anonymized Data Export)
- **Endpoint**: `GET /api/v1/surveys/{surveyId}/lobbying-report`
- **Response**:
```json
{
  "surveyId": "6512fd45b23d9...",
  "surveyTitle": "Public Transit Survey",
  "targetBoundaryCode": "WARD-01",
  "totalResponses": 1250,
  "aggregatedAnswers": {
    "Do you support the metro extension route?": {
      "Strongly Support": 980,
      "Neutral": 120,
      "Oppose": 150
    }
  }
}
```

---

## 5. Administrative Overrides

### Approve Survey to Go Live
- **Endpoint**: `POST /api/v1/admin/surveys/{surveyId}/approve`

### Toggle active Election Mode
- **Endpoint**: `POST /api/v1/admin/config/election-mode`
- **Request Payload**:
```json
{
  "active": true
}
```

### Shadowban user/politician
- **Endpoint**: `POST /api/v1/admin/users/{userId}/shadowban`
- **Params**: `shadowbanned=true`

### Hide Issue Visibility
- **Endpoint**: `POST /api/v1/admin/issues/{id}/toggle-hide`
- **Params**: `hidden=true`
