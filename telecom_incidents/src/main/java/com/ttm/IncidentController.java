package com.ttm;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class IncidentController {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RuleService ruleService;

    // ============ ИНЦИДЕНТЫ ============

    @PostMapping("/incidents")
    public Map<String, Object> createIncident(@RequestBody Map<String, String> body) {
        String number = generateIncidentNumber();
        
        String sql = """
            INSERT INTO incident (number, client_name, service_type, problem_type, 
                                  macro_segment, segment, address, status, created_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)
        """;
        
        String macroSegment = body.getOrDefault("macroSegment", "B2B");
        String segment = body.getOrDefault("segment", "3K");
        
        jdbcTemplate.update(sql,
            number,
            body.get("clientName"),
            body.get("serviceType"),
            body.get("problemType"),
            macroSegment,
            segment,
            body.get("address")
        );
        
        String selectSql = "SELECT * FROM incident WHERE number = ?";
        Map<String, Object> incident = jdbcTemplate.queryForMap(selectSql, number);
        
        Long incidentId = (Long) incident.get("id");
        createDiagnosticsTask(incidentId, "1LTP");
        
        return Map.of(
            "number", number,
            "id", incidentId,
            "status", "IN_PROGRESS",
            "message", "Инцидент создан, назначена диагностика"
        );
    }

    @GetMapping("/incidents")
    public List<Map<String, Object>> getIncidents() {
        return jdbcTemplate.queryForList("SELECT * FROM incident ORDER BY created_date DESC");
    }

    @GetMapping("/incidents/{id}")
    public Map<String, Object> getIncidentById(@PathVariable Long id) {
        return jdbcTemplate.queryForMap("SELECT * FROM incident WHERE id = ?", id);
    }

    // ============ ЗАДАНИЯ ============

    @GetMapping("/tasks")
    public List<Map<String, Object>> getTasks() {
        return jdbcTemplate.queryForList("SELECT * FROM tasks ORDER BY created_date DESC");
    }

    @GetMapping("/tasks/{taskId}")
    public Map<String, Object> getTaskById(@PathVariable String taskId) {
        return jdbcTemplate.queryForMap("SELECT * FROM tasks WHERE id = ?", taskId);
    }

    @GetMapping("/tasks/incident/{incidentId}")
    public List<Map<String, Object>> getTasksByIncident(@PathVariable Long incidentId) {
        return jdbcTemplate.queryForList(
            "SELECT * FROM tasks WHERE incident_id = ? ORDER BY created_date", incidentId
        );
    }

    @GetMapping("/tasks/{taskId}/rules")
    public Map<String, Object> getAvailableRules(@PathVariable String taskId) {
        String contextSql = """
            SELECT 
                t.task_type,
                t.unit_role,
                i.macro_segment,
                i.service_type,
                i.segment
            FROM tasks t 
            JOIN incident i ON t.incident_id = i.id 
            WHERE t.id = ?
        """;
        
        Map<String, Object> context = jdbcTemplate.queryForMap(contextSql, taskId);
        
        List<Map<String, Object>> rules = ruleService.findMatchingRules(
            (Integer) context.get("task_type"),
            (String) context.get("unit_role"),
            (String) context.get("macro_segment"),
            (String) context.get("service_type"),
            (String) context.get("segment")
        );
        
        return Map.of(
            "taskId", taskId,
            "availableRules", rules,
            "count", rules.size()
        );
    }

    @PostMapping("/tasks/{taskId}/complete")
    public Map<String, String> completeTask(
            @PathVariable String taskId,
            @RequestBody Map<String, Object> request) {
        
        Integer ruleId = (Integer) request.get("ruleId");
        String comment = (String) request.get("comment");
        
        String updateSql = "UPDATE tasks SET status = 'COMPLETED', result = ?, closed_date = CURRENT_TIMESTAMP WHERE id = ?";
        jdbcTemplate.update(updateSql, String.valueOf(ruleId), taskId);
        
        String taskSql = """
            SELECT t.incident_id, t.task_type, i.service_type, i.macro_segment
            FROM tasks t JOIN incident i ON t.incident_id = i.id
            WHERE t.id = ?
        """;
        Map<String, Object> taskData = jdbcTemplate.queryForMap(taskSql, taskId);
        
        String nextTaskType = determineNextTaskType(ruleId);
        if (nextTaskType != null) {
            String nextUnitRole = getTargetUnitRole(ruleId);
            Long incidentId = (Long) taskData.get("incident_id");
            createTask(incidentId, nextTaskType, nextUnitRole, taskId);
        }
        
        if (shouldCloseIncident(ruleId)) {
            String closeSql = "UPDATE incident SET status = 'CLOSED' WHERE id = ?";
            jdbcTemplate.update(closeSql, taskData.get("incident_id"));
        }
        
        return Map.of(
            "taskId", taskId,
            "status", "COMPLETED",
            "message", "Задание выполнено"
        );
    }

    // ============ ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ============

    private String generateIncidentNumber() {
        LocalDateTime now = LocalDateTime.now();
        String date = now.format(DateTimeFormatter.ofPattern("yyMMdd"));
        String time = now.format(DateTimeFormatter.ofPattern("HHmmss"));
        return date + time + (int)(Math.random() * 100);
    }

    private void createDiagnosticsTask(Long incidentId, String unitRole) {
        String taskId = generateTaskId();
        String sql = """
            INSERT INTO tasks (id, incident_id, task_type, unit_role, status, created_date)
            VALUES (?, ?, 2, ?, 'IN_PROGRESS', CURRENT_TIMESTAMP)
        """;
        jdbcTemplate.update(sql, taskId, incidentId, unitRole);
    }

    private String generateTaskId() {
        return "TASK-" + System.currentTimeMillis() + "-" + (int)(Math.random() * 1000);
    }

    private void createTask(Long incidentId, String taskTypeId, String unitRole, String parentTaskId) {
        String taskId = generateTaskId();
        String sql = """
            INSERT INTO tasks (id, incident_id, task_type, unit_role, status, parent_task_id, created_date)
            VALUES (?, ?, ?, ?, 'CREATED', ?, CURRENT_TIMESTAMP)
        """;
        jdbcTemplate.update(sql, taskId, incidentId, Integer.parseInt(taskTypeId), unitRole, parentTaskId);
    }

    private String determineNextTaskType(Integer ruleId) {
        String sql = "SELECT target_task_type FROM rule_target WHERE rule_id = (SELECT id FROM cache_rule WHERE external_id = ?)";
        try {
            return jdbcTemplate.queryForObject(sql, String.class, ruleId);
        } catch (Exception e) {
            return null;
        }
    }

    private String getTargetUnitRole(Integer ruleId) {
        String sql = "SELECT target_unit_role FROM cache_rule WHERE external_id = ?";
        return jdbcTemplate.queryForObject(sql, String.class, ruleId);
    }

    private boolean shouldCloseIncident(Integer ruleId) {
        return ruleId.equals(14527);
    }
}