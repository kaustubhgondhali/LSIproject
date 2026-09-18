-- =============================================================================
-- V9: Blog and Category Management with strict website separation
-- Supports Share Market (ACADEMY) and Mutual Fund (MUTUAL_FUND) blogs,
-- status workflow (DRAFT / PUBLISHED / UNPUBLISHED), categories, and SEO metadata.
-- =============================================================================

CREATE TABLE blog_categories (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id       BIGINT        NOT NULL,
    name          VARCHAR(100)  NOT NULL,
    slug          VARCHAR(120)  NOT NULL,
    description   VARCHAR(255),
    display_order INT           NOT NULL DEFAULT 0,
    active        BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP(6)  NOT NULL,
    updated_at    TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_blog_categories_site_slug UNIQUE (site_id, slug),
    CONSTRAINT fk_blog_categories_site      FOREIGN KEY (site_id) REFERENCES sites (id)
);

CREATE INDEX idx_blog_categories_site_active ON blog_categories (site_id, active, display_order);

CREATE TABLE blogs (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    site_id             BIGINT        NOT NULL,
    category_id         BIGINT,
    title               VARCHAR(255)  NOT NULL,
    slug                VARCHAR(280)  NOT NULL,
    short_description   VARCHAR(1000) NOT NULL,
    content             LONGTEXT      NOT NULL,
    featured_image_path VARCHAR(255),
    author              VARCHAR(150)  NOT NULL DEFAULT 'Lord Sai Team',
    tags                VARCHAR(500),
    status              VARCHAR(20)   NOT NULL DEFAULT 'DRAFT',   -- DRAFT | PUBLISHED | UNPUBLISHED
    seo_title           VARCHAR(255),
    seo_description     VARCHAR(500),
    seo_keywords        VARCHAR(500),
    published_at        TIMESTAMP(6)  NULL,
    created_by_user_id  BIGINT,
    updated_by_user_id  BIGINT,
    created_at          TIMESTAMP(6)  NOT NULL,
    updated_at          TIMESTAMP(6)  NOT NULL,
    CONSTRAINT uk_blogs_site_slug         UNIQUE (site_id, slug),
    CONSTRAINT fk_blogs_site              FOREIGN KEY (site_id)            REFERENCES sites (id),
    CONSTRAINT fk_blogs_category          FOREIGN KEY (category_id)        REFERENCES blog_categories (id) ON DELETE SET NULL,
    CONSTRAINT fk_blogs_created_by        FOREIGN KEY (created_by_user_id) REFERENCES users (id),
    CONSTRAINT fk_blogs_updated_by        FOREIGN KEY (updated_by_user_id) REFERENCES users (id)
);

CREATE INDEX idx_blogs_site_status    ON blogs (site_id, status, published_at);
CREATE INDEX idx_blogs_category       ON blogs (category_id);
CREATE INDEX idx_blogs_created_at     ON blogs (created_at);

-- -----------------------------------------------------------------------------
-- Seed Initial Categories
-- -----------------------------------------------------------------------------
-- 1. Share Market (ACADEMY)
INSERT INTO blog_categories (site_id, name, slug, description, display_order, active, created_at, updated_at)
SELECT s.id, 'Stock Market Basics', 'stock-market-basics', 'Foundational stock market concepts and exchange fundamentals', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY'
UNION ALL
SELECT s.id, 'Technical Analysis', 'technical-analysis', 'Candlestick patterns, price action, and chart indicators', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY'
UNION ALL
SELECT s.id, 'Trading', 'trading', 'Intraday and swing trading strategies for active participants', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY'
UNION ALL
SELECT s.id, 'Risk Management', 'risk-management', 'Position sizing, stop-loss discipline, and capital preservation math', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY'
UNION ALL
SELECT s.id, 'Investment', 'investment', 'Long-term equity allocation and wealth compounding principles', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY';

-- 2. Mutual Fund (MUTUAL_FUND)
INSERT INTO blog_categories (site_id, name, slug, description, display_order, active, created_at, updated_at)
SELECT s.id, 'Mutual Funds', 'mutual-funds', 'Portfolio construction, fund categories, and asset allocation strategies', 1, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND'
UNION ALL
SELECT s.id, 'SIP', 'sip', 'Systematic Investment Plans and rupee-cost averaging compounding math', 2, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND'
UNION ALL
SELECT s.id, 'Personal Finance', 'personal-finance', 'Emergency funds, budgeting, and disciplined wealth building', 3, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND'
UNION ALL
SELECT s.id, 'Investment Planning', 'investment-planning', 'Goal-based financial planning for retirement, children, and security', 4, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND'
UNION ALL
SELECT s.id, 'Financial Education', 'financial-education', 'Investor awareness, SEBI guidelines, and long-term security guidance', 5, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND';

-- -----------------------------------------------------------------------------
-- Seed Initial Reference Articles (Published) from existing blog.html
-- -----------------------------------------------------------------------------
-- Academy Article 1
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'stock-market-basics' LIMIT 1),
       'Stock Market: Where Should a Beginner Start?',
       'stock-market-where-should-a-beginner-start',
       'Entering the capital market without understanding structure and terminology is the fastest way to lose capital. This foundational guide covers Demat account setup, exchange functions (NSE/BSE), order types, and the essential shift from speculative tips to structured learning.',
       '<p>Entering the capital market without understanding structure and terminology is the fastest way to lose capital. A structured approach begins with understanding how exchanges, depositories, and brokers interact to protect investor interests.</p><h2>1. Demat vs Trading Account</h2><p>Your trading account acts as the vehicle through which buy and sell orders are routed to the exchange (NSE/BSE), while your Demat account acts as the digital vault where your purchased securities reside with depositories (CDSL/NSDL).</p><h2>2. The Danger of Speculative Tips</h2><p>Relying on unsolicited WhatsApp or Telegram tips leads to disastrous drawdowns. Successful market participants build a repeatable framework based on price action and strict risk management.</p><h2>3. Essential First Steps</h2><ul><li>Complete KYC with a reputable SEBI-registered broker.</li><li>Learn index composition (NIFTY 50 and SENSEX) before picking individual stocks.</li><li>Never risk capital earmarked for short-term living expenses.</li></ul>',
       'img/service-1.jpg',
       'Mentor Vaibhav Pawar',
       'Basics, Demat, Beginners',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY';

-- Academy Article 2
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'risk-management' LIMIT 1),
       'What Is Risk Management in Trading?',
       'what-is-risk-management-in-trading',
       '"First protect your capital. Then think about returns." Learn position sizing mathematics, stop-loss execution, and maximum drawdown control.',
       '<p>"First protect your capital. Then think about returns." Without an ironclad risk management framework, even a trading system with a 70% win rate can lead to catastrophic ruin.</p><h2>1. The 1% Risk Rule</h2><p>Professional traders never risk more than 1% to 2% of their total trading capital on any single setup. This ensures that a normal streak of consecutive losses cannot wipe out your account.</p><h2>2. Mathematical Position Sizing</h2><p>Position sizing is determined by dividing your maximum rupee risk by the distance between entry price and stop-loss price. Never adjust your stop-loss after entering a trade.</p><h2>3. Emotional Neutrality</h2><p>Pre-defining your exit before order placement removes emotional hesitation and preserves psychological capital.</p>',
       'img/service-3.jpg',
       'Mentor Vaibhav Pawar',
       'Risk, Position Sizing, Stop Loss',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY';

-- Academy Article 3
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'trading' LIMIT 1),
       '5 Common Mistakes New Traders Make',
       '5-common-mistakes-new-traders-make',
       'Why revenge trading, skipping stop-losses, and following unsolicited social media tips destroy retail trading accounts and how to avoid them.',
       '<p>Many retail participants leave the market within six months due to avoidable psychological and tactical mistakes.</p><h2>1. Revenge Trading</h2><p>Attempting to immediately win back a loss by taking larger, undisciplined positions is the quickest route to an account blowout.</p><h2>2. Averaging Losing Trades</h2><p>Adding more capital to a falling stock hoping for a bounce compounds risk exponentially.</p><h2>3. Over-Leveraging in Derivatives</h2><p>Trading options without understanding Delta and Theta decay results in rapid capital erosion.</p><h2>4. Lacking a Trade Journal</h2><p>Without documenting your trade rationale, mistakes are repeated indefinitely.</p>',
       'img/service-4.jpg',
       'Lord Sai Academy Mentors',
       'Psychology, Mistakes, Discipline',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'ACADEMY';

-- Mutual Fund Article 1
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'sip' LIMIT 1),
       'SIP vs Lump Sum: What''s the Difference?',
       'sip-vs-lump-sum-whats-the-difference',
       'Understand how Rupee Cost Averaging protects monthly investors during corrections, compared to the timing requirements of lump-sum allocations.',
       '<p>Systematic Investment Planning (SIP) and lump-sum investments are two distinct ways to build wealth in mutual funds, each suited to specific market conditions and financial profiles.</p><h2>1. Rupee Cost Averaging</h2><p>SIP automatically buys more mutual fund units when markets correct and fewer units when markets rise, eliminating the stress of timing market peaks and troughs.</p><h2>2. When Lump Sum Makes Sense</h2><p>Lump-sum deployment is advantageous when you have surplus idle funds and valuation indicators show reasonable or attractive market pricing.</p><h2>3. Long-Term Power of Compounding</h2><p>Consistent monthly contributions compounded over 10 to 20 years create generational wealth through disciplined reinvestment of dividends and capital appreciation.</p>',
       'img/service-2.jpg',
       'Vaibhav Pawar (AMFI MFD)',
       'SIP, Compounding, Rupee Cost Averaging',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND';

-- Mutual Fund Article 2
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'personal-finance' LIMIT 1),
       'How Does Systematic Withdrawal Plan (SWP) Work?',
       'how-does-systematic-withdrawal-plan-swp-work',
       'A detailed breakdown of tax-efficient monthly income generation for retirees. Discover how keeping capital invested while withdrawing fixed sums extends corpus longevity.',
       '<p>A Systematic Withdrawal Plan (SWP) is a smart financial mechanism that allows investors to redeem a predetermined sum from their mutual fund corpus at regular intervals.</p><h2>1. Consistent Cashflow for Retirement</h2><p>Instead of locking all retirement savings into fixed-deposit structures that lose value to inflation, an SWP maintains exposure to growth assets while distributing predictable monthly income.</p><h2>2. Tax Efficiency Over Fixed Deposits</h2><p>Only the capital gains component of each withdrawal is subject to tax, making SWP significantly more tax-efficient than interest payouts from traditional banking deposits.</p><h2>3. Preserving the Capital Base</h2><p>When withdrawal rates are calibrated below historical portfolio returns, your remaining principal continues to compound and grow over time.</p>',
       'img/service-3.jpg',
       'Vaibhav Pawar (AMFI MFD)',
       'SWP, Retirement, Regular Income',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND';

-- Mutual Fund Article 3
INSERT INTO blogs (site_id, category_id, title, slug, short_description, content, featured_image_path, author, tags, status, published_at, created_at, updated_at)
SELECT s.id,
       (SELECT c.id FROM blog_categories c WHERE c.site_id = s.id AND c.slug = 'mutual-funds' LIMIT 1),
       'What Is Mutual Fund Diversification?',
       'what-is-mutual-fund-diversification',
       'Why holding 15 similar large-cap funds is not true diversification. Learn how to blend equity, debt, and hybrid assets across different market capitalizations.',
       '<p>True diversification is not about collecting numerous schemes; it is about combining uncorrelated asset classes to optimize risk-adjusted returns.</p><h2>1. The Illusion of Over-Diversification</h2><p>Holding multiple schemes that all invest in the same top 30 index constituents results in redundant expense ratios without downside protection.</p><h2>2. Multi-Asset Blending</h2><p>A resilient portfolio combines large-cap stability, mid/small-cap growth, international diversification, and debt securities to withstand diverse macroeconomic cycles.</p><h2>3. Periodic Rebalancing</h2><p>Rebalancing annually restores target asset weights and systematically locks in gains from outperforming sectors.</p>',
       'img/service-4.jpg',
       'Vaibhav Pawar (AMFI MFD)',
       'Diversification, Asset Allocation, Mutual Funds',
       'PUBLISHED',
       CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM sites s WHERE s.site_code = 'MUTUAL_FUND';

