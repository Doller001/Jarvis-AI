# Jarvis AI — MongoDB Atlas Integration & Setup Guide

This guide provides step-by-step instructions on setting up **MongoDB Atlas (Cloud NoSQL Database)** with the Jarvis backend to store conversation history, user preferences, and action logs.

---

## 📌 1. MongoDB Atlas Overview

[MongoDB Atlas](https://www.mongodb.com/cloud/atlas) is a multi-cloud developer data platform providing managed NoSQL document databases. Jarvis connects natively to MongoDB Atlas via `MONGODB_URI` using PyMongo (`mongodb+srv://` connection strings) to store persistent conversation memory.

Jarvis uses a **multi-tier fallback system**:
1. **Tier 1**: **MongoDB Atlas** (`MONGODB_URI`)
2. **Tier 2**: **Supabase PostgreSQL** (`DATABASE_URL`)
3. **Tier 3**: **Local SQLite** (`jarvis_memory.db`)

---

## 🚀 2. Step-by-Step MongoDB Atlas Setup

### Step 1: Create a MongoDB Atlas Account & Cluster
1. Go to [mongodb.com/cloud/atlas](https://www.mongodb.com/cloud/atlas) and sign up for a free account.
2. Create a new Organization and Project (e.g. `Jarvis-AI`).
3. Click **Deploy a Database** and choose the **M0 Free Tier**.
4. Select your preferred Cloud Provider (AWS, GCP, or Azure) and Region.
5. Click **Create Cluster**.

### Step 2: Database Access & Network Security
1. Navigate to **Security** → **Database Access**.
2. Click **Add New Database User**.
3. Set authentication method to **Password**, enter a username (e.g. `jarvis_user`) and a secure password.
4. Set User Privileges to **Read and write to any database**.
5. Click **Add User**.
6. Navigate to **Security** → **Network Access**.
7. Click **Add IP Address** and add `0.0.0.0/0` (Allow Access from Anywhere, suitable for Render / Cloud backend hosting).

---

## 🔑 3. Getting Your Connection String (`MONGODB_URI`)

1. In your MongoDB Atlas Dashboard, click **Database** → **Connect**.
2. Select **Drivers** (Node.js, Python, etc.).
3. Under **3. Add your connection string into your application code**, copy the URI string:
```ini
MONGODB_URI=mongodb+srv://jarvis_user:<password>@cluster0.xxxxx.mongodb.net/jarvis?retryWrites=true&w=majority
```
*Note: Replace `<password>` with your database user password and `jarvis` with your preferred database name.*

---

## ⚙️ 4. Setting Up Environment Variables

Add `MONGODB_URI` to your backend environment file (`.env` or Render environment variables):

### `.env` File Example
```ini
PORT=8000
MONGODB_URI=mongodb+srv://jarvis_user:MyPassword123@cluster0.xxxxx.mongodb.net/jarvis?retryWrites=true&w=majority
GROQ_API_KEY=gsk_your_groq_key
OPENROUTER_API_KEY=sk-or-v1-your_key
GEMINI_API_KEY=AIzaSy_your_key
ALLOWED_ORIGINS=*
```

### Render Deployment Configuration
1. Go to your Render Dashboard → Service → **Environment**.
2. Add key `MONGODB_URI` with value `mongodb+srv://...`.
3. Save changes — Render will redeploy automatically!

---

## 📱 5. Collections & Schema

Jarvis automatically initializes indices on startup:
- Collection: `conversations`
  - Document Schema: `{ "session_id": "...", "role": "user|assistant", "content": "...", "timestamp": 1740000000.0 }`
  - Index: `session_id ASC, timestamp DESC`
- Collection: `user_preferences`
  - Document Schema: `{ "key": "...", "value": "...", "updated_at": 1740000000.0 }`
  - Unique Index: `key`
