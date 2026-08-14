-- Справочные данные (только латиница)
INSERT INTO task_type_ref (id, name, description) VALUES
(2, 'DIAGNOSTICS', 'Diagnostics'),
(4, 'SOLUTION', 'Solution'),
(6, 'CLIENT_CONFIRMATION', 'Client Confirmation'),
(7, 'VERIFICATION', 'Verification');

INSERT INTO unit_role_ref (id, name, description) VALUES
(1, '1LTP', 'First Line Support'),
(2, '2LTP', 'Second Line Support'),
(3, 'MRF', 'Macro Regional Branch');

-- Правила
INSERT INTO cache_rule (external_id, process_name, process_version, close_code, target_unit_role, display_name, is_active) VALUES
(14525, 'ttm_rule2', 1, 'REQUIRES_CHECK', 'MRF', 'Requires MRF Check', true),
(14526, 'ttm_rule2', 1, 'ESCALATE_2LTP', '2LTP', 'Escalate to 2LTP', true),
(14527, 'ttm_rule2', 1, 'RESOLVED', '1LTP', 'Problem Resolved', true),
(14528, 'ttm_rule2', 1, 'GEP_REQUEST', '1LTP', 'GEP Request', true),
(14529, 'ttm_rule2', 1, 'NO_ANSWER', '1LTP', 'No Answer from Client', true);

-- Связи правил с типами заданий (все для Диагностики)
INSERT INTO rule_source_task_type_link (rule_id, task_type_id) 
SELECT r.id, 2 FROM cache_rule r WHERE r.external_id IN (14525, 14526, 14527, 14528, 14529);

-- Связи правил с ролями (все для 1LTP)
INSERT INTO rule_source_unit_role_link (rule_id, unit_role_id) 
SELECT r.id, 1 FROM cache_rule r WHERE r.external_id IN (14525, 14526, 14527, 14528, 14529);

-- Контекстные условия
INSERT INTO rule_context_conditions (rule_id, condition_key, condition_value) VALUES
((SELECT id FROM cache_rule WHERE external_id = 14525), 'macro_segment', 'B2B'),
((SELECT id FROM cache_rule WHERE external_id = 14525), 'service_type', 'VPN L2/L3'),
((SELECT id FROM cache_rule WHERE external_id = 14525), 'segment', '3K'),
((SELECT id FROM cache_rule WHERE external_id = 14526), 'macro_segment', 'B2G'),
((SELECT id FROM cache_rule WHERE external_id = 14528), 'service_type', 'Internet');

-- Целевые типы заданий для правил
INSERT INTO rule_target (rule_id, target_task_type) VALUES
((SELECT id FROM cache_rule WHERE external_id = 14525), 4),  -- Solution MRF
((SELECT id FROM cache_rule WHERE external_id = 14526), 4),  -- Solution 2LTP
((SELECT id FROM cache_rule WHERE external_id = 14527), 6),  -- Client Confirmation
((SELECT id FROM cache_rule WHERE external_id = 14528), 7),  -- Verification
((SELECT id FROM cache_rule WHERE external_id = 14529), 2);  -- Diagnostics again