-- The structured part of an insight: headline, highlights, patterns, suggestions and the measured facts behind them.
-- NULL for insights generated before this existed; those only have a summary.
ALTER TABLE ai_insights
    ADD COLUMN details JSON NULL;
