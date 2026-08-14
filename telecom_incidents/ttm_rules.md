# 📚 Полное руководство: Параметры Rule в TTM

## 1. Что такое Rule и зачем он нужен?

**Rule (Правило)** — это механизм, который:
- Определяет, какие варианты действий увидит пользователь при закрытии задания
- Управляет маршрутизацией бизнес-процесса
- Обеспечивает персонализацию интерфейса на основе контекста тикета

### Основная концепция

```
[Задание пользователя] 
    ↓
[Пользователь открывает задание]
    ↓
[Система: определяет контекст тикета]
    ↓
[Система: ищет подходящие Rules в БД]
    ↓
[Пользователь видит: выпадающий список с вариантами]
    ↓
[Пользователь выбирает Rule]
    ↓
[Токен идет по стрелке с этим RuleID]
    ↓
[Создается следующее задание]
```

## 2. Архитектура и хранение Rules

### Структура базы данных

```sql
-- Основная таблица правил
CREATE TABLE cache_rule (
    id INT PRIMARY KEY,                    -- Внутренний ID (генерируется)
    external_id INT,                       -- RuleID, который мы видим в Camunda
    process_name VARCHAR(100),             -- Имя процесса (ttm_rule2)
    process_version INT,                   -- Версия процесса
    close_code VARCHAR(50),                -- Шифр закрытия
    target_unit_role VARCHAR(50),          -- Куда направить
    display_name VARCHAR(255)              -- Что видит пользователь
);

-- Связь: какое правило относится к какому типу задания
CREATE TABLE rule_source_task_type_link (
    rule_id INT,                           -- Внутренний ID правила
    task_type_id INT                       -- Тип задания (2 - Диагностика)
);

-- Связь: какое правило доступно для какой роли
CREATE TABLE rule_source_unit_role_link (
    rule_id INT,
    unit_role_id INT                       -- ID роли (1 - 1LTP, 2 - 2LTP)
);

-- Контекстные условия
CREATE TABLE rule_context_conditions (
    rule_id INT,
    condition_key VARCHAR(50),             -- макросегмент, услуга, сегмент
    condition_value VARCHAR(50)            -- B2B, VPN L2/L3, 3K
);
```

### Типы заданий (Task Types)

| ID | Название | Описание |
|----|----------|----------|
| 2 | Диагностика | Первичная проверка проблемы |
| 3 | Ожидание | Ожидание внешних событий |
| 4 | Решение | Принятие решения по проблеме |
| 6 | Подтверждение клиентом | Проверка устранения проблемы |
| 7 | Проверка | Дополнительная проверка |

### Роли подразделений (Unit Roles)

| ID | Название | Описание |
|----|----------|----------|
| 1 | 1LTP | Первая линия технической поддержки |
| 2 | 2LTP | Вторая линия технической поддержки |
| 3 | МРФ | Макрорегиональный филиал |
| 4 | ГРПЦ | Группа регионального проектирования |

## 3. Структура тикета (Incidient)

```sql
CREATE TABLE incident (
    id INT PRIMARY KEY,
    number VARCHAR(20),                    -- Номер инцидента
    client_name VARCHAR(255),              -- Клиент
    service_type VARCHAR(50),              -- Тип услуги
    problem_type VARCHAR(50),              -- Тип проблемы
    priority INT,                          -- Приоритет
    status VARCHAR(50),                    -- Статус
    macro_segment VARCHAR(10),             -- Макросегмент (B2B, B2G, B2C)
    segment VARCHAR(10),                   -- Сегмент (3K, CMT, KB9V)
    mfu_client VARCHAR(100),               -- МФУ Клиента
    address TEXT,                          -- Адрес
    cms_order_number VARCHAR(20),          -- Номер заказа CMS
    created_date TIMESTAMP
);

CREATE TABLE tasks (
    id INT PRIMARY KEY,
    incident_id INT,                       -- Ссылка на инцидент
    task_type INT,                         -- Тип задания (2,4,6,7)
    unit_role VARCHAR(50),                 -- Роль исполнителя
    status VARCHAR(50),                    -- Статус задания
    created_date TIMESTAMP,
    started_date TIMESTAMP,
    closed_date TIMESTAMP,
    result VARCHAR(255)                    -- Результат выполнения
);
```

## 4. Практический процесс: От контекста до выпадающего списка

### Шаг 1: Пользователь открывает задание

```javascript
// Пользователь кликает на задание в интерфейсе
GET /api/tasks/138640-01-03
```

### Шаг 2: Система получает контекст задания

```sql
-- Динамический запрос №1: Получаем контекст задания
SELECT 
    t.id as task_id,
    t.task_type,          -- например, 2 (Диагностика)
    t.unit_role,          -- например, '1LTP'
    t.incident_id,
    i.macro_segment,      -- например, 'B2B'
    i.service_type,       -- например, 'VPN L2/L3'
    i.segment,            -- например, '3K'
    i.problem_type,       -- например, 'Клиентская'
    i.priority,           -- например, 7
    i.mfu_client         -- например, 'Центральный филиал'
FROM tasks t
JOIN incident i ON t.incident_id = i.id
WHERE t.id = '138640-01-03';
```

**Результат (JSON):**
```json
{
    "task_id": "138640-01-03",
    "task_type": 2,
    "unit_role": "1LTP",
    "incident_id": 138640,
    "macro_segment": "B2B",
    "service_type": "VPN L2/L3",
    "segment": "3K",
    "problem_type": "Клиентская",
    "priority": 7,
    "mfu_client": "Центральный филиал"
}
```

### Шаг 3: Система ищет подходящие правила

```sql
-- Динамический запрос №2: Ищем правила, подходящие под контекст
SELECT 
    r.external_id as rule_id,
    r.close_code,
    r.target_unit_role,
    r.display_name,
    GROUP_CONCAT(
        CONCAT(c.condition_key, '=', c.condition_value) 
        SEPARATOR ' AND '
    ) as conditions
FROM cache_rule r
JOIN rule_source_task_type_link stt ON r.id = stt.rule_id
JOIN rule_source_unit_role_link sur ON r.id = sur.rule_id
LEFT JOIN rule_context_conditions c ON r.id = c.rule_id
WHERE 
    -- ⬇️ ДИНАМИЧЕСКИЕ ПАРАМЕТРЫ ИЗ JSON ⬇️
    stt.task_type_id = 2                    -- Из JSON: task_type
    AND sur.unit_role_id = 1               -- Из JSON: unit_role (1LTP)
    AND r.process_name = 'ttm_rule2'       -- Текущий процесс
    AND r.process_version = 307            -- Текущая версия
GROUP BY r.id
HAVING 
    -- Проверяем условия
    (
        -- Правила без условий - подходят всем
        COUNT(c.condition_key) = 0
    )
    OR
    (
        -- Правила с условиями проверяются на совпадение
        SUM(
            CASE 
                -- ⬇️ УСЛОВИЯ ИЗ JSON ⬇️
                WHEN c.condition_key = 'macro_segment' 
                    AND c.condition_value = 'B2B' THEN 1
                WHEN c.condition_key = 'service_type' 
                    AND c.condition_value = 'VPN L2/L3' THEN 1
                WHEN c.condition_key = 'segment' 
                    AND c.condition_value = '3K' THEN 1
                ELSE 0
            END
        ) = COUNT(c.condition_key)
    );
```

### Шаг 4: Результат - доступные правила

```json
{
    "taskId": "138640-01-03",
    "availableRules": [
        {
            "ruleId": 14525,
            "displayName": "Требуется проверка МРФ",
            "closeCode": "REQUIRES_CHECK",
            "targetUnitRole": "МРФ"
        },
        {
            "ruleId": 14527,
            "displayName": "Проблема устранена",
            "closeCode": "RESOLVED",
            "targetUnitRole": "1LTP"
        },
        {
            "ruleId": 14530,
            "displayName": "Недозвон до клиента",
            "closeCode": "NO_ANSWER",
            "targetUnitRole": "1LTP"
        }
    ],
    "sla": {
        "remaining": "10 ч. 47 мин.",
        "deadline": "05.02.2021 10:47"
    }
}
```

### Шаг 5: Что видит пользователь

```
┌─────────────────────────────────────────────────┐
│  Задание: Диагностика                           │
│  Инцидент: 138640                              │
│  Клиент: ЗНАМЕНСКИЙ ДЕТСКИЙ САД РОМАШКА        │
├─────────────────────────────────────────────────┤
│  Результат: [Выберите действие ▼]              │
│             ├─ Требуется проверка МРФ          │ ← Rule 14525
│             ├─ Проблема устранена              │ ← Rule 14527
│             └─ Недозвон до клиента             │ ← Rule 14530
├─────────────────────────────────────────────────┤
│  [Принять в работу]                            │
└─────────────────────────────────────────────────┘
```

## 5. Примеры работы Rules в разных контекстах

### Пример A: Тикет B2B, VPN L2/L3, Сегмент 3K

**Контекст:**
```json
{
    "task_type": 2,
    "unit_role": "1LTP",
    "macro_segment": "B2B",
    "service_type": "VPN L2/L3",
    "segment": "3K"
}
```

**Правила в БД:**

| RuleID | Display Name | Conditions | Подходит? |
|--------|--------------|------------|-----------|
| 14525 | Требуется проверка МРФ | macro_segment=B2B AND service_type=VPN L2/L3 AND segment=3K | ✅ ДА |
| 14526 | Передать на 2LTP | macro_segment=B2G | ❌ НЕТ (B2B != B2G) |
| 14527 | Проблема устранена | (нет условий) | ✅ ДА |
| 14528 | Запрос в ГЭП | service_type=Интернет | ❌ НЕТ (VPN != Интернет) |

**Пользователь увидит:** 2 варианта (14525, 14527)

---

### Пример B: Тикет B2G, Интернет, Сегмент CMT

**Контекст:**
```json
{
    "task_type": 2,
    "unit_role": "1LTP",
    "macro_segment": "B2G",
    "service_type": "Интернет",
    "segment": "CMT"
}
```

**Правила в БД:**

| RuleID | Display Name | Conditions | Подходит? |
|--------|--------------|------------|-----------|
| 14525 | Требуется проверка МРФ | macro_segment=B2B AND service_type=VPN L2/L3 AND segment=3K | ❌ НЕТ |
| 14526 | Передать на 2LTP | macro_segment=B2G | ✅ ДА |
| 14527 | Проблема устранена | (нет условий) | ✅ ДА |
| 14528 | Запрос в ГЭП | service_type=Интернет | ✅ ДА |

**Пользователь увидит:** 3 варианта (14526, 14527, 14528)

---

### Пример C: Тикет B2B, Интернет, Сегмент 3K

**Контекст:**
```json
{
    "task_type": 2,
    "unit_role": "1LTP",
    "macro_segment": "B2B",
    "service_type": "Интернет",
    "segment": "3K"
}
```

**Правила в БД:**

| RuleID | Display Name | Conditions | Подходит? |
|--------|--------------|------------|-----------|
| 14525 | Требуется проверка МРФ | macro_segment=B2B AND service_type=VPN L2/L3 AND segment=3K | ❌ НЕТ (VPN != Интернет) |
| 14526 | Передать на 2LTP | macro_segment=B2G | ❌ НЕТ (B2B != B2G) |
| 14527 | Проблема устранена | (нет условий) | ✅ ДА |
| 14528 | Запрос в ГЭП | service_type=Интернет | ✅ ДА |

**Пользователь увидит:** 2 варианта (14527, 14528)

## 6. Как создаются Rules в Camunda Modeler

### Шаг 1: Открыть схему процесса

В Camunda Modeler открыть BPMN-диаграмму (например, `ttm_rule2`).

### Шаг 2: Добавить стрелку из шлюза

1. Нажать на шлюз (Gateway)
2. Перетащить стрелку к следующему заданию
3. Нажать на стрелку → открыть панель свойств

### Шаг 3: Настроить Rule

В панели свойств (вкладка **Extensions**) добавить:

```xml
<camunda:properties>
    <camunda:property name="ruleId" value="14525" />
</camunda:properties>
```

### Шаг 4: Сохранить и деплоить

1. Сохранить диаграмму
2. Задеплоить в Camunda

### Шаг 5: Добавить параметры в БД

```sql
-- 1. Создать правило
INSERT INTO cache_rule (
    external_id, process_name, process_version, 
    close_code, target_unit_role, display_name
) VALUES (
    14525, 'ttm_rule2', 307,
    'REQUIRES_CHECK', 'МРФ', 'Требуется проверка МРФ'
);

-- 2. Связать с типом задания (Диагностика)
INSERT INTO rule_source_task_type_link (rule_id, task_type_id)
VALUES (LAST_INSERT_ID(), 2);

-- 3. Связать с ролью (1LTP)
INSERT INTO rule_source_unit_role_link (rule_id, unit_role_id)
VALUES (LAST_INSERT_ID(), 1);

-- 4. Добавить контекстные условия
INSERT INTO rule_context_conditions (rule_id, condition_key, condition_value)
VALUES 
    (LAST_INSERT_ID(), 'macro_segment', 'B2B'),
    (LAST_INSERT_ID(), 'service_type', 'VPN L2/L3'),
    (LAST_INSERT_ID(), 'segment', '3K');
```

## 7. Диагностика проблем с Rules

### Проблема: Пустой список результатов

**Причина:** Контекст тикета не соответствует ни одному правилу

**Диагностика:**
1. Проверить контекст тикета
2. Проверить правила в БД
3. Использовать DevTools (F12) для просмотра ответа API

**Решение:**
```sql
-- Временно исправить для одного тикета
UPDATE tasks SET unit_role = '1LTP' WHERE id = '138640-01-03';

-- Или добавить недостающее правило
INSERT INTO rule_context_conditions (rule_id, condition_key, condition_value)
VALUES (14525, 'segment', '3K');
```

### Проблема: Красные стрелки в Camunda

**Причина:** RuleID в стрелке не существует в БД

**Решение:**
1. Найти версию, где правило существовало
2. Восстановить правило в БД
3. Обновить схему

## 8. Реализация в пет-проекте

### API Эндпоинт

```python
from flask import Flask, jsonify, request
from sqlalchemy import text

app = Flask(__name__)

@app.route('/api/tasks/<task_id>/rules', methods=['GET'])
def get_available_rules(task_id):
    # Шаг 1: Получить контекст
    context_query = """
    SELECT 
        t.task_type,
        t.unit_role,
        i.macro_segment,
        i.service_type,
        i.segment,
        i.problem_type
    FROM tasks t
    JOIN incident i ON t.incident_id = i.id
    WHERE t.id = :task_id
    """
    context = db.execute(text(context_query), {'task_id': task_id}).first()
    
    # Шаг 2: Найти правила
    rules_query = """
    SELECT 
        r.external_id as rule_id,
        r.close_code,
        r.target_unit_role,
        r.display_name
    FROM cache_rule r
    JOIN rule_source_task_type_link stt ON r.id = stt.rule_id
    JOIN rule_source_unit_role_link sur ON r.id = sur.rule_id
    LEFT JOIN rule_context_conditions c ON r.id = c.rule_id
    WHERE 
        stt.task_type_id = :task_type
        AND sur.unit_role_id = :unit_role
        AND r.process_name = 'ttm_rule2'
        AND r.process_version = 307
    GROUP BY r.id
    HAVING 
        COUNT(c.condition_key) = 0
        OR (
            SUM(
                CASE 
                    WHEN c.condition_key = 'macro_segment' 
                        AND c.condition_value = :macro_segment THEN 1
                    WHEN c.condition_key = 'service_type' 
                        AND c.condition_value = :service_type THEN 1
                    WHEN c.condition_key = 'segment' 
                        AND c.condition_value = :segment THEN 1
                    ELSE 0
                END
            ) = COUNT(c.condition_key)
        )
    """
    
    rules = db.execute(text(rules_query), {
        'task_type': context.task_type,
        'unit_role': context.unit_role,
        'macro_segment': context.macro_segment,
        'service_type': context.service_type,
        'segment': context.segment
    }).fetchall()
    
    return jsonify({
        'taskId': task_id,
        'availableRules': [dict(rule) for rule in rules]
    })
```

## 9. Ключевые выводы

1. **Rules - это мост между БД и интерфейсом пользователя**
2. **Контекст тикета определяет, какие Rules будут показаны**
3. **Фильтрация происходит на уровне SQL с динамическими параметрами**
4. **Пользователь видит только подходящие варианты действий**
5. **При выборе Rule определяется дальнейший путь процесса**

### Жизненный цикл Rules

```
[Создание в Camunda] 
    ↓
[Добавление в БД с параметрами]
    ↓
[Связывание с типом задания и ролью]
    ↓
[Добавление контекстных условий]
    ↓
[Система фильтрует правила по контексту]
    ↓
[Пользователь видит доступные варианты]
    ↓
[Выбор → переход к следующему заданию]
```

---

*Это полное руководство охватывает все аспекты работы с Rules: от архитектуры до практической реализации в пет-проекте.*