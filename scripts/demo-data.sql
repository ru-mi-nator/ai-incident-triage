BEGIN;

DO $$
BEGIN
    IF (
        SELECT COUNT(*)
        FROM users
        WHERE username IN ('support1', 'support2', 'developer1', 'developer2')
    ) <> 4 THEN
        RAISE EXCEPTION 'Required demo users are missing. Expected support1, support2, developer1 and developer2.';
    END IF;
END $$;

WITH demo_users AS (
    SELECT
        MAX(id) FILTER (WHERE username = 'support1') AS support1_id,
        MAX(id) FILTER (WHERE username = 'support2') AS support2_id,
        MAX(id) FILTER (WHERE username = 'developer1') AS developer1_id,
        MAX(id) FILTER (WHERE username = 'developer2') AS developer2_id
    FROM users
    WHERE username IN ('support1', 'support2', 'developer1', 'developer2')
),
demo_incidents (
    title,
    description,
    application_name,
    environment,
    error_logs,
    status,
    created_by_username,
    assigned_developer_username,
    final_category,
    final_priority,
    actual_root_cause,
    actual_resolution,
    created_at,
    updated_at,
    assigned_at,
    resolved_at
) AS (
    VALUES
    (
        'Payment API returning intermittent 500 errors',
        'Checkout attempts intermittently failed during authorization capture. Retries succeeded for some requests, but support observed increased payment error volume during peak demo traffic.',
        'PAYMENT_SERVICE',
        'PROD',
        '2026-06-21T09:12:44Z WARN payment-api request_id=demo-pay-417 capture attempt retried after storage timeout' || E'\n' ||
        '2026-06-21T09:13:02Z ERROR payment-api request_id=demo-pay-418 returned HTTP 500 while reading payment ledger state',
        'RESOLVED',
        'support1',
        'developer1',
        'DATABASE',
        'CRITICAL',
        'A connection pool saturation condition caused payment ledger reads to exceed the API timeout during a traffic spike.',
        'Raised the payment database pool limit, added a shorter retry backoff, and confirmed successful capture responses under replayed demo load.',
        CURRENT_TIMESTAMP - INTERVAL '34 days 10 hours',
        CURRENT_TIMESTAMP - INTERVAL '32 days 1 hour',
        CURRENT_TIMESTAMP - INTERVAL '34 days 8 hours',
        CURRENT_TIMESTAMP - INTERVAL '32 days 1 hour'
    ),
    (
        'Authentication tokens expiring unexpectedly',
        'Active users were redirected to sign in earlier than expected after refreshing pages. Session renewal still worked for newly issued sessions.',
        'AUTH_SERVICE',
        'PROD',
        '2026-06-22T15:24:10Z WARN auth-session request_id=demo-auth-116 session rejected because expiry claim was older than refresh window' || E'\n' ||
        '2026-06-22T15:24:11Z INFO auth-session request_id=demo-auth-116 cleared session and requested reauthentication',
        'IN_PROGRESS',
        'support2',
        'developer2',
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '2 days 4 hours',
        CURRENT_TIMESTAMP - INTERVAL '2 days 1 hour',
        CURRENT_TIMESTAMP - INTERVAL '2 days 1 hour',
        NULL
    ),
    (
        'Order status not updating after payment',
        'Orders in user acceptance testing remained in pending status after payment confirmation events were received by the order workflow.',
        'ORDER_SERVICE',
        'UAT',
        '2026-06-20T10:09:33Z WARN order-workflow request_id=demo-order-225 payment event acknowledged but status transition not applied',
        'OPEN',
        'support1',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '4 days 6 hours',
        CURRENT_TIMESTAMP - INTERVAL '4 days 5 hours',
        NULL,
        NULL
    ),
    (
        'Notification emails delayed by twenty minutes',
        'Order confirmation emails were queued successfully but delivered later than the expected service window during production smoke testing.',
        'NOTIFICATION_SERVICE',
        'PROD',
        '2026-06-19T11:33:02Z WARN notification-worker request_id=demo-notify-032 email queue lag observed at 1200 seconds',
        'OPEN',
        'support2',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '6 days 9 hours',
        CURRENT_TIMESTAMP - INTERVAL '6 days 8 hours',
        NULL,
        NULL
    ),
    (
        'Reporting dashboard query timing out',
        'The operations dashboard loaded summary tiles but timed out while rendering the incident trend widget for the current reporting window.',
        'REPORTING_SERVICE',
        'PROD',
        '2026-06-18T08:45:51Z ERROR reporting-api request_id=demo-report-808 query cancelled after statement timeout while loading trend widget',
        'RESOLVED',
        'support1',
        'developer2',
        'PERFORMANCE',
        'HIGH',
        'The dashboard query scanned the full reporting table because a date predicate did not use the available created_at index.',
        'Reworked the report filter to preserve index usage and verified the dashboard loads within the target response time.',
        CURRENT_TIMESTAMP - INTERVAL '8 days 7 hours',
        CURRENT_TIMESTAMP - INTERVAL '7 days 2 hours',
        CURRENT_TIMESTAMP - INTERVAL '8 days 6 hours',
        CURRENT_TIMESTAMP - INTERVAL '7 days 2 hours'
    ),
    (
        'Duplicate payment callback processing',
        'Quality assurance observed duplicate callback handling when the same gateway event was replayed by the integration test harness.',
        'PAYMENT_SERVICE',
        'QA',
        '2026-06-16T14:18:29Z WARN payment-callback request_id=demo-pay-552 duplicate event candidate accepted for processing',
        'IN_PROGRESS',
        'support2',
        'developer1',
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '10 days 3 hours',
        CURRENT_TIMESTAMP - INTERVAL '9 days 22 hours',
        CURRENT_TIMESTAMP - INTERVAL '10 days 1 hour',
        NULL
    ),
    (
        'Invalid refresh token accepted',
        'A QA regression scenario found that a malformed refresh credential was accepted when the client retried immediately after logout.',
        'AUTH_SERVICE',
        'QA',
        '2026-06-14T07:54:04Z ERROR auth-refresh request_id=demo-auth-741 malformed refresh credential passed validation branch',
        'RESOLVED',
        'support1',
        'developer1',
        'AUTHENTICATION',
        'CRITICAL',
        'A feature flag in QA routed refresh validation through an outdated compatibility branch after logout retries.',
        'Disabled the compatibility branch in QA, added validation coverage for malformed refresh credentials, and verified logout retry behavior.',
        CURRENT_TIMESTAMP - INTERVAL '12 days 8 hours',
        CURRENT_TIMESTAMP - INTERVAL '11 days 4 hours',
        CURRENT_TIMESTAMP - INTERVAL '12 days 6 hours',
        CURRENT_TIMESTAMP - INTERVAL '11 days 4 hours'
    ),
    (
        'Order creation returning validation errors',
        'Development builds rejected valid cart submissions when optional delivery instructions were omitted from the request body.',
        'ORDER_SERVICE',
        'DEV',
        '2026-06-12T12:26:43Z WARN order-api request_id=demo-order-319 validation failed for optional deliveryInstructions field',
        'OPEN',
        'support2',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '14 days 5 hours',
        CURRENT_TIMESTAMP - INTERVAL '14 days 4 hours',
        NULL,
        NULL
    ),
    (
        'SMS notifications not delivered',
        'UAT users did not receive SMS status alerts even though notification preferences were enabled and message records were created.',
        'NOTIFICATION_SERVICE',
        'UAT',
        '2026-06-10T16:41:13Z WARN sms-dispatcher request_id=demo-sms-084 delivery adapter returned retryable provider status',
        'IN_PROGRESS',
        'support1',
        'developer2',
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '16 days 2 hours',
        CURRENT_TIMESTAMP - INTERVAL '15 days 20 hours',
        CURRENT_TIMESTAMP - INTERVAL '16 days 1 hour',
        NULL
    ),
    (
        'Monthly report contains duplicate records',
        'The monthly QA report showed repeated incident rows in the export file while the dashboard totals remained correct.',
        'REPORTING_SERVICE',
        'QA',
        '2026-06-08T09:07:36Z WARN report-export request_id=demo-report-281 duplicate row emitted for aggregated incident summary',
        'RESOLVED',
        'support2',
        'developer1',
        'DATABASE',
        'MEDIUM',
        'The export mapper appended grouped rows twice when pagination ended on an exact batch boundary.',
        'Adjusted the export mapper boundary condition and verified duplicate-free monthly reports for small and full-page batches.',
        CURRENT_TIMESTAMP - INTERVAL '18 days 6 hours',
        CURRENT_TIMESTAMP - INTERVAL '17 days 3 hours',
        CURRENT_TIMESTAMP - INTERVAL '18 days 5 hours',
        CURRENT_TIMESTAMP - INTERVAL '17 days 3 hours'
    ),
    (
        'Payment gateway connection timeout',
        'UAT payment attempts intermittently timed out while establishing a connection to the gateway simulator during checkout testing.',
        'PAYMENT_SERVICE',
        'UAT',
        '2026-06-06T13:55:27Z WARN gateway-client request_id=demo-pay-903 connection attempt exceeded configured timeout',
        'OPEN',
        'support1',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '21 days 3 hours',
        CURRENT_TIMESTAMP - INTERVAL '21 days 2 hours',
        NULL,
        NULL
    ),
    (
        'User login latency above threshold',
        'Production sign-in requests completed successfully but exceeded the dashboard threshold during the morning usage window.',
        'AUTH_SERVICE',
        'PROD',
        '2026-06-04T10:21:18Z WARN auth-login request_id=demo-auth-510 login completed in 3480 ms above threshold',
        'IN_PROGRESS',
        'support2',
        'developer1',
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '24 days 4 hours',
        CURRENT_TIMESTAMP - INTERVAL '23 days 22 hours',
        CURRENT_TIMESTAMP - INTERVAL '24 days 3 hours',
        NULL
    ),
    (
        'Order inventory reservation failed',
        'Production order creation succeeded but inventory reservation failed for a subset of carts during a catalog refresh window.',
        'ORDER_SERVICE',
        'PROD',
        '2026-06-02T06:48:20Z ERROR inventory-adapter request_id=demo-order-672 reservation rejected after catalog refresh event',
        'RESOLVED',
        'support1',
        'developer2',
        'INTEGRATION',
        'CRITICAL',
        'Inventory reservation read a stale catalog snapshot while a refresh transaction was still finalizing.',
        'Serialized catalog refresh completion before reservation reads and verified order creation with refreshed inventory snapshots.',
        CURRENT_TIMESTAMP - INTERVAL '29 days 9 hours',
        CURRENT_TIMESTAMP - INTERVAL '28 days 6 hours',
        CURRENT_TIMESTAMP - INTERVAL '29 days 8 hours',
        CURRENT_TIMESTAMP - INTERVAL '28 days 6 hours'
    ),
    (
        'Scheduled report email not generated',
        'The development scheduler completed without errors, but the expected report email artifact was not created for the configured demo schedule.',
        'REPORTING_SERVICE',
        'DEV',
        '2026-05-30T05:31:49Z WARN report-scheduler request_id=demo-report-044 scheduled run completed with zero email jobs generated',
        'OPEN',
        'support2',
        NULL,
        NULL,
        NULL,
        NULL,
        NULL,
        CURRENT_TIMESTAMP - INTERVAL '31 days 7 hours',
        CURRENT_TIMESTAMP - INTERVAL '31 days 6 hours',
        NULL,
        NULL
    )
)
INSERT INTO incidents (
    title,
    description,
    application_name,
    environment,
    error_logs,
    status,
    created_by_id,
    assigned_developer_id,
    final_category,
    final_priority,
    actual_root_cause,
    actual_resolution,
    created_at,
    updated_at,
    assigned_at,
    resolved_at
)
SELECT
    demo_incidents.title,
    demo_incidents.description,
    demo_incidents.application_name,
    demo_incidents.environment,
    demo_incidents.error_logs,
    demo_incidents.status,
    CASE demo_incidents.created_by_username
        WHEN 'support1' THEN demo_users.support1_id
        WHEN 'support2' THEN demo_users.support2_id
    END,
    CASE demo_incidents.assigned_developer_username
        WHEN 'developer1' THEN demo_users.developer1_id
        WHEN 'developer2' THEN demo_users.developer2_id
        ELSE NULL
    END,
    demo_incidents.final_category,
    demo_incidents.final_priority,
    demo_incidents.actual_root_cause,
    demo_incidents.actual_resolution,
    demo_incidents.created_at,
    demo_incidents.updated_at,
    demo_incidents.assigned_at,
    demo_incidents.resolved_at
FROM demo_incidents
CROSS JOIN demo_users
WHERE NOT EXISTS (
    SELECT 1
    FROM incidents
    WHERE incidents.title = demo_incidents.title
      AND incidents.application_name = demo_incidents.application_name
);

WITH ai_seed (
    title,
    application_name,
    suggested_category,
    suggested_priority,
    probable_root_cause,
    suggested_resolution,
    generated_at
) AS (
    VALUES
    (
        'Payment API returning intermittent 500 errors',
        'PAYMENT_SERVICE',
        'API',
        'HIGH',
        'Payment API failures are likely related to intermittent response handling while loading ledger state.',
        'Review API timeout behavior, validate connection pool headroom, and add retry protection around ledger reads.',
        CURRENT_TIMESTAMP - INTERVAL '33 days 15 hours'
    ),
    (
        'Authentication tokens expiring unexpectedly',
        'AUTH_SERVICE',
        'AUTHENTICATION',
        'HIGH',
        'Session expiry calculation may be using an inconsistent refresh window for active browser sessions.',
        'Compare issued and refreshed session timestamps, then align expiry validation with the configured session lifetime.',
        CURRENT_TIMESTAMP - INTERVAL '1 day 21 hours'
    ),
    (
        'Reporting dashboard query timing out',
        'REPORTING_SERVICE',
        'DATABASE',
        'MEDIUM',
        'Dashboard trend loading appears to exceed the query timeout because report aggregation is scanning too much historical data.',
        'Inspect the trend query plan, constrain the report window, and add or use indexes that match the dashboard filter.',
        CURRENT_TIMESTAMP - INTERVAL '7 days 14 hours'
    ),
    (
        'Duplicate payment callback processing',
        'PAYMENT_SERVICE',
        'INTEGRATION',
        'HIGH',
        'Callback replay handling may not be enforcing idempotency before the payment event enters processing.',
        'Add an idempotency check around callback event keys and verify replayed gateway events are acknowledged once.',
        CURRENT_TIMESTAMP - INTERVAL '9 days 12 hours'
    ),
    (
        'Invalid refresh token accepted',
        'AUTH_SERVICE',
        'CONFIGURATION',
        'HIGH',
        'Refresh validation likely allows an invalid credential through a fallback branch after logout retry handling.',
        'Audit refresh validation branches, disable legacy fallback behavior, and add regression tests for malformed refresh credentials.',
        CURRENT_TIMESTAMP - INTERVAL '11 days 18 hours'
    ),
    (
        'Order inventory reservation failed',
        'ORDER_SERVICE',
        'DATABASE',
        'HIGH',
        'Inventory reservation failures appear tied to a timing gap between order creation and catalog refresh completion.',
        'Trace reservation requests through the inventory adapter and ensure catalog refresh state is consistent before reservations run.',
        CURRENT_TIMESTAMP - INTERVAL '28 days 18 hours'
    )
)
INSERT INTO ai_analyses (
    incident_id,
    suggested_category,
    suggested_priority,
    probable_root_cause,
    suggested_resolution,
    model_name,
    generated_at
)
SELECT
    incidents.id,
    ai_seed.suggested_category,
    ai_seed.suggested_priority,
    ai_seed.probable_root_cause,
    ai_seed.suggested_resolution,
    'gemini-2.5-flash-lite',
    ai_seed.generated_at
FROM ai_seed
JOIN incidents
  ON incidents.title = ai_seed.title
 AND incidents.application_name = ai_seed.application_name
WHERE NOT EXISTS (
    SELECT 1
    FROM ai_analyses
    WHERE ai_analyses.incident_id = incidents.id
);

COMMIT;
