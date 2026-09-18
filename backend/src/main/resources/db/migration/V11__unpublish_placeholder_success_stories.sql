-- =============================================================================
-- V11: Stop publishing placeholder success-story videos.
-- The six stories seeded in V7 carried over the demo YouTube ID (dQw4w9WgXcQ) that the
-- original static stories.html used as a stand-in. No real videos exist for them, so
-- showing them publicly would present an unrelated video as a genuine student story.
-- Rows are PRESERVED (not deleted): the admin can upload the real video for each and
-- publish it again from Admin -> Success Stories. Nothing is invented here.
-- =============================================================================

UPDATE success_stories
SET published   = FALSE,
    description = CASE
                      WHEN description IS NULL OR description = ''
                      THEN 'Placeholder entry — no real video has been added yet. Upload the actual video and publish to show it on the website.'
                      ELSE description
                  END,
    updated_at  = CURRENT_TIMESTAMP
WHERE video_path IS NULL
  AND video_url LIKE '%dQw4w9WgXcQ%';
