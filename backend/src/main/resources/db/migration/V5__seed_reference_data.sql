-- =============================================================================
-- V5: Reference data — the two managed sites and the existing flagship course.
-- Course/module names mirror what courses.html already shows publicly.
-- No user accounts are seeded here; the first admin is bootstrapped from
-- environment variables on first startup (see .env.example).
-- =============================================================================

INSERT INTO sites (site_code, site_name, active, created_at, updated_at) VALUES
    ('ACADEMY',     'Lord Sai Share Market Academy',  TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('MUTUAL_FUND', 'Lord Sai Investment (Mutual Fund Distribution)', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO courses (course_code, course_name, short_description, description, price, discounted_price,
                     duration, thumbnail_path, status, display_order, created_at, updated_at) VALUES
    ('SMET-MASTER',
     'Share Market Education & Training',
     'A comprehensive master curriculum covering market basics, technical analysis, intraday, swing, options, risk management, and live market observation.',
     'One complete structured course instead of random tips. Beginner friendly, practical chart analysis, capital-protection focus, multi-style versatility (intraday, swing, options, investing), live market observation sessions, and access to the Student Portal with module handouts, class recordings, trade journal and doubt desk.',
     14999.00,
     9999.00,
     'Full Master Course',
     'img/courses/share-market-master.jpg',
     'ACTIVE',
     1,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO course_modules (course_id, module_name, description, display_order, active, created_at, updated_at)
SELECT c.id, m.module_name, m.description, m.display_order, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM courses c
JOIN (
    SELECT 'Stock Market Basics' AS module_name,
           'How the market works: exchanges, indices, order types, market mechanics and participants.' AS description, 1 AS display_order
    UNION ALL SELECT 'Technical Analysis & Price Action',
           'Candlestick anatomy, buyer-seller psychology, support/resistance, trends and chart patterns.', 2
    UNION ALL SELECT 'Intraday Trading Strategies',
           'Time-bound setups, volume, momentum and disciplined intraday execution.', 3
    UNION ALL SELECT 'Swing Trading (Working Professionals)',
           'Multi-day positional approaches suited to people who cannot watch the screen all day.', 4
    UNION ALL SELECT 'Options Trading & Derivatives',
           'Calls, puts, strike selection (ITM/ATM/OTM), Delta, Theta decay and Open Interest.', 5
    UNION ALL SELECT 'Investment & Wealth Compounding',
           'Long-term investing, SIPs and the mathematics of compounding.', 6
    UNION ALL SELECT 'Capital Protection & Risk Rules',
           'Position sizing, stop-loss discipline and rules that keep you in the game.', 7
    UNION ALL SELECT 'Trading Psychology & Mindset',
           'Fear, greed, overtrading and building a repeatable process.', 8
    UNION ALL SELECT 'Live Market Observation',
           'Guided live-market sessions applying everything learned in real time.', 9
) m
WHERE c.course_code = 'SMET-MASTER';

INSERT INTO student_id_sequence (year_value, last_number) VALUES (2026, 0);
