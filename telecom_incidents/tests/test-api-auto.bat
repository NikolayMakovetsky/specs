@echo off
chcp 65001 > nul

echo ============================================================
echo     🧪 TTM Lite - Тестирование API (автоматическое)
echo ============================================================
echo.

echo [1/3] Создание инцидента...
curl -X POST http://localhost:8081/api/incidents -H "Content-Type: application/json" -d "{\"clientName\":\"Тестовый клиент\",\"serviceType\":\"Интернет\",\"problemType\":\"Клиентская\",\"macroSegment\":\"B2B\",\"address\":\"г. Москва\"}"

echo.
echo.
echo [2/3] Список инцидентов...
curl http://localhost:8081/api/incidents

echo.
echo.
echo [3/3] Список заданий...
curl http://localhost:8081/api/tasks

echo.
echo.
echo ============================================================
echo     ✅ Тестирование завершено!
echo ============================================================
pause