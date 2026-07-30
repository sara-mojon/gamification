import os
from deepeval.metrics import GEval
from deepeval.test_case import LLMTestCase, LLMTestCaseParams

# API key de OpenIA
os.environ["OPENAI_API_KEY"] = "sk-mitoken"

# 1. Alineación y Correctitud Lógica
correctness_metric = GEval(
    name="Task Completion & Correctness",
    criteria="Evalúa si los casos de prueba generados evalúan correctamente el algoritmo del enunciado. Penaliza severamente si los resultados esperados (outputs) de los asserts no coinciden matemáticamente con las entradas o contradicen los ejemplos del enunciado.",
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
)

# 2. Cobertura de Casos Límite
edge_case_metric = GEval(
    name="Edge Case Robustness",
    criteria="Evalúa la calidad y variedad de las pruebas. Puntúa positivamente si incluye casos límite (ej. strings vacíos, números negativos, operaciones extremas) además de los 'happy paths'. Penaliza si solo copia los ejemplos básicos del enunciado sin añadir valor.",
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
)

# 3. Ejecutabilidad y Validez Sintáctica
syntax_metric = GEval(
    name="Executable & Syntactically Valid",
    criteria="Revisa el código generado como si fueras un compilador. Penaliza si hay errores de sintaxis evidentes, aserciones mal construidas, o errores de tipado que impedirían que el script se ejecute en su lenguaje objetivo.",
    evaluation_params=[LLMTestCaseParams.ACTUAL_OUTPUT],
)

# 4. Adherencia a la Estructura y Formato
format_metric = GEval(
    name="Boilerplate & Format Adherence",
    criteria="Comprueba si el código incluye toda la infraestructura requerida: las funciones de testing, los bloques try/except, y la impresión final de los resultados en formato JSON. Penaliza severamente si el código se corta de forma abrupta o no imprime el JSON al final.",
    evaluation_params=[LLMTestCaseParams.ACTUAL_OUTPUT],
)

# 5. Independencia del Entorno (Evitar Alucinaciones)
hallucination_metric = GEval(
    name="Environment Independence & Hallucination Avoidance",
    criteria="Verifica si el código asume la existencia de variables globales, arrays o funciones que no están definidas en el enunciado. Penaliza la invención (alucinación) de datos. Premia el uso de variables locales ficticias (mocks) construidas correctamente para la prueba.",
    evaluation_params=[LLMTestCaseParams.INPUT, LLMTestCaseParams.ACTUAL_OUTPUT],
)

# --- DATOS DEL TEST ---
enunciado_reto = """
All of the animals are having a feast! Each animal is bringing one dish. There is just one rule: the dish must start and end with the same letters as the animal's name. For example, the great blue heron is bringing garlic naan and the chickadee is bringing chocolate cake.

Write a function `feast` that takes the animal's name and dish as arguments and returns true or false to indicate whether the beast is allowed to bring the dish to the feast.

Assume that `beast` and `dish` are always lowercase strings, and that each has at least two letters. `beast` and `dish` may contain hyphens and spaces, but these will not appear at the beginning or end of the string. They will not contain numerals.
"""

tests_generados_por_ollama = """
import java.util.*;

public class Main {
    static int total = 0;
    static int passed = 0;
    static int failed = 0;
    static List<String> results = new ArrayList<>();

    static void assertEquals(Object expected, Object actual, String message) {
        if (expected instanceof Double && actual instanceof Double) {
            if (Math.abs((Double)expected - (Double)actual) <= 1e-9) return;
        }
        if ((expected == null && actual == null) || (expected != null && expected.equals(actual))) {
            return;
        }
        throw new AssertionError(message + " | Expected: " + expected + ", but got: " + actual);
    }

    static void runTest(String testName, Runnable assertionFn) {
        total++;
        try {
            assertionFn.run();
            passed++;
            results.add("{\"name\": \"" + testName + "\", \"status\": \"OK\"}");
        } catch (AssertionError e) {
            failed++;
            results.add("{\"name\": \"" + testName + "\", \"status\": \"FAIL\", \"error\": \"" + e.getMessage().replace("\"", "'") + "\"}");
        } catch (Exception e) {
            failed++;
            results.add("{\"name\": \"" + testName + "\", \"status\": \"FAIL\", \"error\": \"Exception\"}");
        }
    }

    public static void main(String[] args) {
        // --- GENERATE 4-6 TESTS HERE ---
        runTest("Test 1", () -> assertEquals(true, solution("great blue heron", "garlic naan"), "Test 1 Failed"));
        runTest("Test 2", () -> assertEquals(true, solution("chickadee", "chocolate cake"), "Test 2 Failed"));
        runTest("Test 3", () -> assertEquals(false, solution("brown bear", "bear claw"), "Test 3 Failed"));
        runTest("Test 4", () -> assertEquals(true, solution("panda", "apple pie"), "Test 4 Failed"));
        runTest("Test 5", () -> assertEquals(false, solution("fox", "chicken wings"), "Test 5 Failed"));
        runTest("Test 6", () -> assertEquals(true, solution("lion", "noodle soup"), "Test 6 Failed"));

        System.out.print("||JSON_RESULT||{\"total\": " + total + ", \"passed\": " + passed + ", \"failed\": " + failed + ", \"results\": [");
        for (int i = 0; i < results.size(); i++) {
            System.out.print(results.get(i));
            if (i < results.size() - 1) System.out.print(", ");
        }
        System.out.println("]}");
        if (failed > 0) System.exit(1);
    }
}
"""

test_case = LLMTestCase(
    input=enunciado_reto,
    actual_output=tests_generados_por_ollama
)

# Lista de todas nuestras métricas
metrics = [
    correctness_metric,
    edge_case_metric,
    syntax_metric,
    format_metric,
    hallucination_metric
]

print("Evaluando pruebas generadas por el LLM local en Java...\n")

# Evaluar e imprimir resultados de cada métrica
for metric in metrics:
    metric.measure(test_case)
    print(f"--- {metric.name.upper()} ---")
    print(f"Nota: {metric.score}") # Puntuación de 0.0 a 1.0
    print(f"Razón: {metric.reason}\n")