# Love Task API - Complete Documentation for Frontend

## Base URL
```
Production: https://your-domain.com
Development: http://localhost:8081
```

## Authentication
All endpoints require JWT Bearer token:
```
Authorization: Bearer <your_jwt_token>
```

---

## 📋 API Endpoints

### 1. Create Love Task (Tạo nhiệm vụ tình yêu)

**Endpoint:**
```
POST /api/v1/love-tasks
POST /love-tasks
```

**Request Body:**
```json
{
  "title": "Mua hoa tặng mẹ",
  "description": "Nhớ mua hoa hồng đỏ nhé!",
  "assigneeId": "user456"
}
```

**Response Success (201 Created):**
```json
{
  "message": "Love task created successfully",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "description": "Nhớ mua hoa hồng đỏ nhé!",
    "status": "PENDING",
    "sender": {
      "userId": "user123",
      "fullName": "Nguyễn Văn A",
      "avatarUrl": "https://example.com/avatar.jpg"
    },
    "assignee": {
      "userId": "user456",
      "fullName": "Nguyễn Thị B",
      "avatarUrl": "https://example.com/avatar2.jpg"
    },
    "sharedPostId": null,
    "createdAt": "2026-03-10T10:00:00",
    "completedAt": null,
    "canShare": false,
    "canComplete": false,
    "reminderMessage": null
  }
}
```

---

### 2. Get Task Detail (Xem chi tiết task) - AC024.1, AC024.2

**Endpoint:**
```
GET /api/v1/love-tasks/{taskId}
GET /love-tasks/{taskId}
```

**Response Success (200 OK) - Task PENDING & User is Assignee:**
```json
{
  "message": "Task details retrieved",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "description": "Nhớ mua hoa hồng đỏ nhé!",
    "status": "PENDING",
    "sender": {
      "userId": "user123",
      "fullName": "Nguyễn Văn A",
      "avatarUrl": "https://example.com/avatar.jpg"
    },
    "assignee": {
      "userId": "user456",
      "fullName": "Nguyễn Thị B",
      "avatarUrl": "https://example.com/avatar2.jpg"
    },
    "sharedPostId": null,
    "createdAt": "2026-03-10T10:00:00",
    "completedAt": null,
    "canShare": true,
    "canComplete": false,
    "reminderMessage": "Share this task to the family space before completing it!"
  }
}
```

**Response - Task SHARED & User is Assignee (AC024.10):**
```json
{
  "message": "Task details retrieved",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "status": "SHARED",
    "sharedPostId": 25,
    "canShare": false,
    "canComplete": true,
    "reminderMessage": null
  }
}
```

**Response - User is NOT Assignee (AC024.11):**
```json
{
  "message": "Task details retrieved",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "status": "PENDING",
    "canShare": false,
    "canComplete": false,
    "reminderMessage": null
  }
}
```

---

### 3. Get Prefilled Post Content (Lấy nội dung post có sẵn) - AC024.4

**Endpoint:**
```
GET /api/v1/love-tasks/{taskId}/prefilled-content
GET /love-tasks/{taskId}/prefilled-content
```

**Response Success (200 OK):**
```json
{
  "message": "Prefilled content retrieved",
  "data": {
    "content": "💕 I just completed a love task from Nguyễn Văn A: Mua hoa tặng mẹ",
    "senderName": "Nguyễn Văn A",
    "taskTitle": "Mua hoa tặng mẹ"
  }
}
```

---

### 4. Share to Family Space (Chia sẻ lên Family Space) - AC024.6, AC024.7, AC024.8

**Endpoint:**
```
POST /api/v1/love-tasks/{taskId}/share
POST /love-tasks/{taskId}/share
```

**Request Body:**
```json
{
  "postContent": "💕 I just completed a love task from Nguyễn Văn A: Mua hoa tặng mẹ. Đã mua xong rồi nè!",
  "imageUrl": "https://example.com/flower.jpg"
}
```

**Response Success (200 OK):**
```json
{
  "message": "Task shared to family space successfully",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "status": "SHARED",
    "sharedPostId": 25,
    "canShare": false,
    "canComplete": true,
    "reminderMessage": null
  }
}
```

---

### 5. Complete Love Task (Hoàn thành task)

**Endpoint:**
```
POST /api/v1/love-tasks/{taskId}/complete
POST /love-tasks/{taskId}/complete
```

**Response Success (200 OK):**
```json
{
  "message": "Love task completed successfully",
  "data": {
    "taskId": 1,
    "title": "Mua hoa tặng mẹ",
    "status": "COMPLETED",
    "sharedPostId": 25,
    "completedAt": "2026-03-10T15:30:00",
    "canShare": false,
    "canComplete": false,
    "reminderMessage": null
  }
}
```

---

### 6. Get My Tasks (Lấy danh sách task của tôi)

**Endpoint:**
```
GET /api/v1/love-tasks/my-tasks
GET /love-tasks/my-tasks
```

**Response Success (200 OK):**
```json
{
  "message": "My tasks retrieved",
  "data": [
    {
      "taskId": 1,
      "title": "Mua hoa tặng mẹ",
      "status": "PENDING",
      "canShare": true,
      "canComplete": false,
      "reminderMessage": "Share this task to the family space before completing it!"
    },
    {
      "taskId": 2,
      "title": "Nấu cơm tối",
      "status": "SHARED",
      "canShare": false,
      "canComplete": true
    }
  ]
}
```

---

## 🎯 Acceptance Criteria Implementation

### AC024.1 - Show "Share To Family Space" Button
```typescript
// Check canShare flag
if (task.status === 'PENDING' && task.canShare) {
  // Show "Share To Family Space" button
}
```

### AC024.2 - Show Reminder Message
```typescript
// Display reminder if exists
if (task.reminderMessage) {
  // Show: "Share this task to the family space before completing it!"
}
```

### AC024.3 - Open Create Post Form
```typescript
// When user taps "Share To Family Space"
const handleShareClick = async () => {
  // 1. Get prefilled content
  const prefilled = await getPrefilledContent(taskId);
  
  // 2. Open Create Post form with prefilled content
  openCreatePostForm({
    initialContent: prefilled.content
  });
};
```

### AC024.4 - Prefilled Content
```typescript
// Content format
"💕 I just completed a love task from [Sender Name]: [Love Task Title]"
```

### AC024.5 - User Can Edit
```typescript
// User can edit postContent before submitting
<textarea value={postContent} onChange={handleEdit} />
```

### AC024.6, AC024.7, AC024.8 - Submit Post
```typescript
const handleSubmitPost = async () => {
  try {
    // Share to family space
    await shareToFamilySpace(taskId, {
      postContent: editedContent,
      imageUrl: uploadedImage
    });
    
    // Show success notification
    showToast('Task shared successfully!');
    
    // Task status updated to SHARED
    // Refresh task detail
  } catch (error) {
    showToast('Failed to share task');
  }
};
```

### AC024.9 - Button Disappears After Share
```typescript
// After share, canShare becomes false
if (!task.canShare) {
  // Hide "Share To Family Space" button
}
```

### AC024.10 - Show "Complete Love Task" Button
```typescript
if (task.status === 'SHARED' && task.canComplete) {
  // Show "Complete Love Task" button
}
```

### AC024.11 - Not Assignee
```typescript
if (!task.canShare && !task.canComplete) {
  // Don't show any action buttons
}
```

### AC024.12 - Task Already Shared/Completed
```typescript
if (task.status === 'SHARED' || task.status === 'COMPLETED') {
  // Don't show "Share" button
}
```

### AC024.13 - Cancel Form
```typescript
const handleCancelForm = () => {
  // Close form
  closeCreatePostForm();
  
  // Task status remains PENDING
  // No API call needed
};
```

---

## 💻 Frontend Integration Code

### React/TypeScript Example

```typescript
// types.ts
interface UserInfo {
  userId: string;
  fullName: string;
  avatarUrl: string;
}

interface LoveTask {
  taskId: number;
  title: string;
  description: string;
  status: 'PENDING' | 'SHARED' | 'COMPLETED';
  sender: UserInfo;
  assignee: UserInfo;
  sharedPostId: number | null;
  createdAt: string;
  completedAt: string | null;
  canShare: boolean;
  canComplete: boolean;
  reminderMessage: string | null;
}

interface PrefilledContent {
  content: string;
  senderName: string;
  taskTitle: string;
}

// api.ts
const API_BASE_URL = 'http://localhost:8081';

const getAuthHeaders = () => ({
  'Authorization': `Bearer ${localStorage.getItem('token')}`,
  'Content-Type': 'application/json'
});

export const getTaskDetail = async (taskId: number): Promise<LoveTask> => {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/love-tasks/${taskId}`,
    { headers: getAuthHeaders() }
  );
  const data = await response.json();
  return data.data;
};

export const getPrefilledContent = async (taskId: number): Promise<PrefilledContent> => {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/love-tasks/${taskId}/prefilled-content`,
    { headers: getAuthHeaders() }
  );
  const data = await response.json();
  return data.data;
};

export const shareToFamilySpace = async (
  taskId: number,
  postContent: string,
  imageUrl?: string
): Promise<LoveTask> => {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/love-tasks/${taskId}/share`,
    {
      method: 'POST',
      headers: getAuthHeaders(),
      body: JSON.stringify({ postContent, imageUrl })
    }
  );
  const data = await response.json();
  return data.data;
};

export const completeTask = async (taskId: number): Promise<LoveTask> => {
  const response = await fetch(
    `${API_BASE_URL}/api/v1/love-tasks/${taskId}/complete`,
    {
      method: 'POST',
      headers: getAuthHeaders()
    }
  );
  const data = await response.json();
  return data.data;
};

// TaskDetailPage.tsx
import React, { useState, useEffect } from 'react';

const TaskDetailPage: React.FC<{ taskId: number }> = ({ taskId }) => {
  const [task, setTask] = useState<LoveTask | null>(null);
  const [showPostForm, setShowPostForm] = useState(false);
  const [postContent, setPostContent] = useState('');

  useEffect(() => {
    loadTask();
  }, [taskId]);

  const loadTask = async () => {
    const data = await getTaskDetail(taskId);
    setTask(data);
  };

  const handleShareClick = async () => {
    // AC024.3 - Open Create Post Form
    const prefilled = await getPrefilledContent(taskId);
    setPostContent(prefilled.content);
    setShowPostForm(true);
  };

  const handleSubmitPost = async () => {
    try {
      // AC024.6, AC024.7, AC024.8
      await shareToFamilySpace(taskId, postContent);
      alert('Task shared successfully!');
      setShowPostForm(false);
      loadTask(); // Refresh
    } catch (error) {
      alert('Failed to share task');
    }
  };

  const handleCancelForm = () => {
    // AC024.13
    setShowPostForm(false);
  };

  const handleCompleteTask = async () => {
    try {
      await completeTask(taskId);
      alert('Task completed!');
      loadTask();
    } catch (error) {
      alert('Failed to complete task');
    }
  };

  if (!task) return <div>Loading...</div>;

  return (
    <div>
      <h1>{task.title}</h1>
      <p>{task.description}</p>
      <p>Status: {task.status}</p>

      {/* AC024.2 - Reminder Message */}
      {task.reminderMessage && (
        <div className="reminder">{task.reminderMessage}</div>
      )}

      {/* AC024.1 - Share Button (PENDING only) */}
      {task.canShare && (
        <button onClick={handleShareClick}>
          Share To Family Space
        </button>
      )}

      {/* AC024.10 - Complete Button (SHARED only) */}
      {task.canComplete && (
        <button onClick={handleCompleteTask}>
          Complete Love Task
        </button>
      )}

      {/* AC024.3, AC024.4, AC024.5 - Create Post Form */}
      {showPostForm && (
        <div className="post-form">
          <h2>Share to Family Space</h2>
          <textarea
            value={postContent}
            onChange={(e) => setPostContent(e.target.value)}
          />
          <button onClick={handleSubmitPost}>Post</button>
          <button onClick={handleCancelForm}>Cancel</button>
        </div>
      )}
    </div>
  );
};

export default TaskDetailPage;
```

---

## 📝 Task Status Flow

```
PENDING → SHARED → COMPLETED
```

| Status | Can Share | Can Complete | Show Reminder |
|--------|-----------|--------------|---------------|
| PENDING | ✅ (if assignee) | ❌ | ✅ |
| SHARED | ❌ | ✅ (if assignee) | ❌ |
| COMPLETED | ❌ | ❌ | ❌ |

---

## ⚠️ Error Handling

**403 Forbidden - Not Assignee:**
```json
{
  "message": "Only assignee can share the task"
}
```

**400 Bad Request - Already Shared:**
```json
{
  "message": "Task is already shared or completed"
}
```

**400 Bad Request - Not Shared Yet:**
```json
{
  "message": "Task must be shared before completing"
}
```

---

## 🗄️ Database Schema

```sql
CREATE TABLE love_tasks (
    task_id INT AUTO_INCREMENT PRIMARY KEY,
    family_id VARCHAR(255) NOT NULL,
    sender_id VARCHAR(255) NOT NULL,
    assignee_id VARCHAR(255) NOT NULL,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    status ENUM('PENDING', 'SHARED', 'COMPLETED') NOT NULL,
    shared_post_id INT,
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    completed_at DATETIME,
    FOREIGN KEY (family_id) REFERENCES families(id),
    FOREIGN KEY (sender_id) REFERENCES users(id),
    FOREIGN KEY (assignee_id) REFERENCES users(id),
    FOREIGN KEY (shared_post_id) REFERENCES posts(post_id)
);
```

---

## 📞 Support

Check Swagger UI for more details:
```
http://localhost:8081/swagger-ui/index.html
```
