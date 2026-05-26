import os
from deepeval.metrics import GEval
from deepeval.test_case import LLMTestCase, LLMTestCaseParams

# API key de OpenIA
os.environ["OPENAI_API_KEY"] = "sk-ApiKeyDePrueba"

# Rúbrica ajustada para ignorar el boilerplate de Judge0
test_generation_metric = GEval(
    name="Calidad de Generación de Tests",
    criteria="Evalúa los casos de prueba generados. IGNORA el código de infraestructura (import json, run_test, try/except). Céntrate EXCLUSIVAMENTE en la lógica de las llamadas a la función (los asserts o strictEqual). Penaliza severamente si el resultado esperado (True/False) no coincide con las reglas matemáticas del enunciado. Premia los casos límite.",
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
)

enunciado_reto = """
# Description:
 
 There is an arithmetic expression that missing some operators. Fill in operator `+-*/`. Correct the arithmetic expression.
 
 Argument: a string `exp`, like this: `"1 () 2 = 3"`
 
 Result: a string, like this: `"1 (+) 2 = 3"`
 
 Note: You can assume that all the inputs are valid, and it has at least one correct answer. Your results should be one of them. 
 
 You should not add extra brackets. Of course, these number should not be changed too.
 
 All the numbers in the arithmetic expression are integers (positive of negative), Float numbers are not provided. (But you may need to operate float numbers during the calculation process)
 
 This kata not too easy and may contains bugs, please help me test it. ;-)
 
# Some Examples

```

correct("1 () 2 = 3") === "1 (+) 2 = 3"

correct("2 () 2 = 4") === "2 (+) 2 = 4" 
                       or "2 (*) 2 = 4"(both valid)

correct("1 () 2 () 3 = 7") === "1 (+) 2 (*) 3 = 7"

correct("5 () 5 () 5 () 5 = 625") === "5 (*) 5 (*) 5 (*) 5 = 625"

correct("5 () 5 + 5 () 5 = 50") === "5 (*) 5 + 5 (*) 5 = 50"

correct("5 () 5 + 5 () 5 = 35") === "5 (+) 5 + 5 (*) 5 = 50" 
                                 or "5 (*) 5 + 5 (+) 5 = 35"

```
"""

tests_generados_por_ollama = """
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <math.h>

int total = 0;
int passed = 0;
int failed = 0;
char results[4096] = ""; 

void run_test(const char* test_name, int condition, const char* error_msg) {
    total++;
    char temp[512];
    if (condition) {
        passed++;
        snprintf(temp, sizeof(temp), "{\"name\": \"%s\", \"status\": \"OK\"}", test_name);
    } else {
        failed++;
        snprintf(temp, sizeof(temp), "{\"name\": \"%s\", \"status\": \"FAIL\", \"error\": \"%s\"}", test_name, error_msg);
    }
    if (total > 1) strcat(results, ", ");
    strcat(results, temp);
}

int main() {
    run_test("Test Case 1", strcmp(solution("1 () 2 = 3"), "1 (+) 2 = 3") == 0, "Expected '1 (+) 2 = 3'");
    run_test("Test Case 2", strcmp(solution("2 () 2 = 4"), "2 (*) 2 = 4") == 0 || strcmp(solution("2 () 2 = 4"), "2 (+) 2 = 4") == 0, "Expected '2 (*) 2 = 4' or '2 (+) 2 = 4'");
    run_test("Test Case 3", strcmp(solution("1 () 2 () 3 = 7"), "1 (+) 2 (*) 3 = 7") == 0, "Expected '1 (+) 2 (*) 3 = 7'");
    run_test("Test Case 4", strcmp(solution("5 () 5 () 5 () 5 = 625"), "5 (*) 5 (*) 5 (*) 5 = 625") == 0, "Expected '5 (*) 5 (*) 5 (*) 5 = 625'");
    run_test("Test Case 5", strcmp(solution("5 () 5 + 5 () 5 = 50"), "5 (*) 5 + 5 (*) 5 = 50") == 0 || strcmp(solution("5 () 5 + 5 () 5 = 50"), "5 (+) 5 + 5 (*) 5 = 50") == 0, "Expected '5 (*) 5 + 5 (*) 5 = 50' or '5 (+) 5 + 5 (*) 5 = 50'");
    run_test("Test Case 6", strcmp(solution("5 () 5 + 5 () 5 = 35"), "5 (+) 5 + 5 (*) 5 = 50") == 0 || strcmp(solution("5 () 5 + 5 () 5 = 35"), "5 (*) 5 + 5 (+) 5 = 35") == 0, "Expected '5 (+) 5 + 5 (*) 5 = 50' or '5 (*) 5 + 5 (+) 5 = 35'");

    printf("||JSON_RESULT||{\"total\": %d, \"passed\": %d, \"failed\": %d, \"results\": [%s]}\n", total, passed, failed, results);
    return failed > 0 ? 1 : 0;
}
"""

test_case = LLMTestCase(
    input=enunciado_reto,
    actual_output=tests_generados_por_ollama
)

print("Evaluando tests de C...")
test_generation_metric.measure(test_case)

print("\n--- RESULTADOS DE C ---")
print(f"Nota (0 a 1): {test_generation_metric.score}")
print(f"Razón del juez: {test_generation_metric.reason}")