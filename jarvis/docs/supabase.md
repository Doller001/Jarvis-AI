# Jarvis AI — Supabase Database Integration & Setup Guide

This guide provides step-by-step instructions on setting up **Supabase (PostgreSQL)** with the Jarvis backend to store conversation history, user preferences, and action logs.

---

## 📌 1. Supabase Overview

[Supabase](https://supabase.com) is an open-source Firebase alternative powered by enterprise-grade **PostgreSQL**. Jarvis connects natively to Supabase via `DATABASE_URL` (using `psycopg2`) to store persistent conversation memory that survives backend restarts and multi-instance cloud deployments.

---

## 🚀 2. Step-by-Step Supabase Project Setup

### Step 1: Create a Supabase Account & Project
1. Go to [https://supabase.com](https://supabase.com) and click **Start your project** (Sign up with GitHub or Email).
2. On your Supabase Dashboard, click **New Project**.
3. Fill in the project details:
   - **Name**: `jarvis-db`
   - **Database Password**: Choose a strong password (e.g. `MySecurePassword123!`) and **save it safely**.
   - **Region**: Choose the region closest to your server or Render service (e.g., `Singapore`, `US East`, `EU West`).
   - **Plan**: Select **Free tier**.
4. Click **Create new project** and wait ~2 minutes for provision.

---

## 🔑 3. Getting Your Database URL & API Keys

### Option A: Database Connection String (`DATABASE_URL`)

1. In your Supabase Dashboard, click the **Settings ⚙️** icon at the bottom of the left sidebar.
2. Select **Database** from the settings menu.
3. Scroll down to the **Connection string** section.
4. Click on the **URI** tab.

You will see two connection options:

#### 1. Connection Pooler (Recommended for Render / Cloud Apps)
```ini
DATABASE_URL=postgresql://postgres.[YOUR-PROJECT-REF]:[YOUR-PASSWORD]@aws-0-[REGION].pooler.supabase.com:6543/postgres
```
*Note: Replace `[YOUR-PASSWORD]` with your actual database password.*

#### 2. Direct Connection String
```ini
DATABASE_URL=postgresql://postgres.[YOUR-PROJECT-REF]:[YOUR-PASSWORD]@db.[YOUR-PROJECT-REF].supabase.co:5432/postgres
```

---

### Option B: API Credentials (`SUPABASE_URL` & Keys)

1. Navigate to **Settings ⚙️** → **API**.
2. Copy the following credentials:
   - **Project URL**: `https://[YOUR-PROJECT-REF].supabase.co`
   - **anon / public key**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`
   - **service_role / secret key**: `eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...`

---

## 🛢️ 4. Database Schema Initialization

Jarvis automatically creates the required tables on backend startup. However, you can also run this SQL script manually in the Supabase **SQL Editor**:

1. Click **SQL Editor** in the left sidebar.
2. Click **New query** and paste the following SQL script:

```sql
-- Create conversations table
CREATE TABLE IF NOT EXISTS conversations (
    id SERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    timestamp DOUBLE PRECISION NOT NULL
);

-- Create user preferences table
CREATE TABLE IF NOT EXISTS user_preferences (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    updated_at DOUBLE PRECISION NOT NULL
);

-- Create index for fast session retrieval
CREATE INDEX IF NOT EXISTS idx_conversations_session_id ON conversations(session_id);
```

3. Click **Run** (or `Ctrl + Enter`).

---

## ⚙️ 5. Setting Up Environment Variables

Add the `DATABASE_URL` to your backend environment file (`.env`):

### `.env` File Example
```ini
PORT=8000
DATABASE_URL=postgresql://postgres.xxxx:MySecurePassword123!@aws-0-singapore.pooler.supabase.com:6543/postgres
GROQ_API_KEY=gsk_your_groq_key
OPENROUTER_API_KEY=sk-or-v1-your_key
GEMINI_API_KEY=AIzaSy_your_key
ALLOWED_ORIGINS=*
```

### Render Deployment Configuration
1. Go to your Render Dashboard → Service → **Environment**.
2. Add key `DATABASE_URL` with value `postgresql://postgres.xxxx:Password@aws-0-singapore.pooler.supabase.com:6543/postgres`.
3. Save changes — Render will redeploy automatically!

---

## 📱 6. How the Android APK Connects to Supabase

1. The **Android Application** connects via WebSocket/HTTP to the Jarvis backend:
   `https://jarvis-backend.onrender.com/ws`
2. When the user speaks (e.g. *"Jarvis, volume badhao"* or *"Jarvis, tell me about relativity"*), the backend `JarvisBrain` processes the intent.
3. The backend `MemoryManager` records user and assistant messages into **Supabase PostgreSQL** via `PersistentStore`.
4. When the user asks *"Jarvis, what did I ask earlier?"*, Jarvis queries Supabase to restore exact conversation history!
