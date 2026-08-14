package com.ttm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class RuleService {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public List<Map<String, Object>> findMatchingRules(
            Integer taskType,
            String unitRole,
            String macroSegment,
            String serviceType,
            String segment) {

        Integer unitRoleId = getUnitRoleId(unitRole);

        String sql = """
            SELECT 
                r.external_id as ruleId,
                r.display_name as displayName,
                r.close_code as closeCode,
                r.target_unit_role as targetUnitRole,
                GROUP_CONCAT(CONCAT(c.condition_key, '=', c.condition_value) SEPARATOR ' AND ') as conditions
            FROM cache_rule r
            JOIN rule_source_task_type_link stt ON r.id = stt.rule_id
            JOIN rule_source_unit_role_link sur ON r.id = sur.rule_id
            LEFT JOIN rule_context_conditions c ON r.id = c.rule_id
            WHERE 
                stt.task_type_id = ?
                AND sur.unit_role_id = ?
                AND r.process_name = 'ttm_rule2'
                AND r.process_version = 1
                AND r.is_active = true
            GROUP BY r.id
            HAVING 
                COUNT(c.condition_key) = 0
                OR (
                    SUM(
                        CASE 
                            WHEN c.condition_key = 'macro_segment' 
                                AND c.condition_value = ? THEN 1
                            WHEN c.condition_key = 'service_type' 
                                AND c.condition_value = ? THEN 1
                            WHEN c.condition_key = 'segment' 
                                AND c.condition_value = ? THEN 1
                            ELSE 0
                        END
                    ) = COUNT(c.condition_key)
                )
            ORDER BY r.external_id
        """;

        return jdbcTemplate.queryForList(sql,
            taskType,
            unitRoleId,
            macroSegment != null ? macroSegment : "",
            serviceType != null ? serviceType : "",
            segment != null ? segment : ""
        );
    }

    private Integer getUnitRoleId(String roleName) {
        String sql = "SELECT id FROM unit_role_ref WHERE name = ?";
        try {
            return jdbcTemplate.queryForObject(sql, Integer.class, roleName);
        } catch (Exception e) {
            return 1; // default: 1LTP
        }
    }
}