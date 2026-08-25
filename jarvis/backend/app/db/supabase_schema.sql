-- ==============================================================================
-- JARVIS ASSISTANT - SUPABASE POSTGRESQL SCHEMA
-- ==============================================================================

-- 1. Conversations Table
CREATE TABLE IF NOT EXISTS public.conversations (
    id BIGSERIAL PRIMARY KEY,
    session_id VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL,
    content TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    timestamp DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_conversations_session_timestamp 
ON public.conversations(session_id, timestamp DESC);

-- 2. User Preferences Table
CREATE TABLE IF NOT EXISTS public.user_preferences (
    key VARCHAR(255) PRIMARY KEY,
    value TEXT NOT NULL,
    metadata JSONB DEFAULT '{}'::jsonb,
    updated_at DOUBLE PRECISION NOT NULL,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

-- 3. Long-term Memories Table
CREATE TABLE IF NOT EXISTS public.memories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    session_id VARCHAR(255) NOT NULL,
    memory_type VARCHAR(100) DEFAULT 'episodic',
    content TEXT NOT NULL,
    importance_score FLOAT DEFAULT 1.0,
    metadata JSONB DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_memories_session 
ON public.memories(session_id, created_at DESC);

-- Enable Row Level Security (RLS)
ALTER TABLE public.conversations ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.user_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.memories ENABLE ROW LEVEL SECURITY;

-- Allow public service / anon access policies
CREATE POLICY "Allow all access to conversations" 
ON public.conversations FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow all access to user_preferences" 
ON public.user_preferences FOR ALL USING (true) WITH CHECK (true);

CREATE POLICY "Allow all access to memories" 
ON public.memories FOR ALL USING (true) WITH CHECK (true);
