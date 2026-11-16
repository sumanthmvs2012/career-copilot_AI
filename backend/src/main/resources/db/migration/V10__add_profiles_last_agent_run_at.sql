-- When the hourly draft agent last finished a run for this profile. NULL = never run (first run looks back 24 hours).
ALTER TABLE profiles ADD COLUMN last_agent_run_at TIMESTAMP WITH TIME ZONE;
