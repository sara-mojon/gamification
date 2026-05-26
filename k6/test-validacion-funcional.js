// k6/test-validacion-funcional.js

import http from 'k6/http';
import { check, group } from 'k6';
import encoding from 'k6/encoding';

// SOLO 1 USUARIO, 1 ITERACIÓN (Es un test de validación, no de carga)
export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    // Exigimos que el 100% de las comprobaciones sean correctas
    checks: ['rate==1.0'], 
  },
};

const BASE_URL = 'https://api.saramg.org/api';
const TOKEN_URL = 'https://auth.saramg.org/realms/masorange-realm/protocol/openid-connect/token';

export function setup() {
  const loginRes = http.post(TOKEN_URL, {
    grant_type: 'password', client_id: 'gamification-client',
    username: 'test_load@sara.local', password: 'password'
  });

  if (loginRes.status !== 200) throw new Error("Abortando: Login falló en test funcional");
  const token = loginRes.json('access_token');
  const payload = JSON.parse(encoding.b64decode(token.split('.')[1], 'rawurl', 's'));
  return { authToken: token, userId: payload.sub, username: 'test_load@sara.local' };
}

export default function (data) {
  const params = {
    headers: { 'Content-Type': 'application/json', 'Authorization': `Bearer ${data.authToken}` },
  };

  console.log("=== INICIANDO BATERÍA DE VALIDACIÓN QA ===");

  // ==========================================
  // 1. MICROSERVICIO: GAMIFICATION
  // ==========================================
  group('Gamification Service', function () {
    // --- Happy Paths ---
    const resConfig = http.get(`${BASE_URL}/gamification/points/config`, params);
    check(resConfig, { 'Configuración devuelve 200 OK': (r) => r.status === 200 });

    const resAward = http.post(`${BASE_URL}/gamification/award?userId=${data.userId}&rank=8`, null, params);
    check(resAward, { 'Puntos asignados devuelve 200 OK': (r) => r.status === 200 });

    // --- Negative Paths ---
    const resBadUser = http.post(`${BASE_URL}/gamification/award?userId=ID-FALSO-999&rank=1`, null, params);
    check(resBadUser, { 'Usuario falso interceptado (404/400)': (r) => r.status === 404 || r.status === 400 });
  });

  // ==========================================
  // 2. MICROSERVICIO: USER
  // ==========================================
  group('User Service', function () {
    // --- Happy Paths ---
    const resUserMe = http.get(`${BASE_URL}/users/me`, params);
    check(resUserMe, { 'Obtener perfil /me devuelve 200 OK': (r) => r.status === 200 });

    const resSync = http.post(`${BASE_URL}/users/sync`, JSON.stringify({}), params);
    check(resSync, { 'Sync usuario devuelve 200/201 OK': (r) => r.status === 200 || r.status === 201 });

    const resRanking = http.get(`${BASE_URL}/users/ranking/${data.username}`, params);
    check(resRanking, { 'Ranking de usuario devuelve 200 OK': (r) => r.status === 200 });

    // --- Negative Paths ---
    const resBadRanking = http.get(`${BASE_URL}/users/ranking/usuario-fantasma-123`, params);
    check(resBadRanking, { 'Ranking de usuario fantasma interceptado (404)': (r) => r.status === 404 });
  });

  // ==========================================
  // 3. MICROSERVICIO: CHALLENGES
  // ==========================================
  group('Challenges Service', function () {
    // --- Happy Paths ---
    const resListChallenges = http.get(`${BASE_URL}/challenges?page=0&size=10`, params);
    check(resListChallenges, { 'Listar retos devuelve 200 OK': (r) => r.status === 200 });

    const resHistory = http.get(`${BASE_URL}/challenges/me/history?page=0&size=6`, params);
    check(resHistory, { 'Historial usuario devuelve 200 OK': (r) => r.status === 200 });

    const resChallengeDetail = http.get(`${BASE_URL}/challenges/3`, params);
    check(resChallengeDetail, { 'Detalle de reto devuelve 200 OK': (r) => r.status === 200 });

    const resSubmit = http.post(`${BASE_URL}/challenges/3/submit`, JSON.stringify({
      sourceCode: "print('QA Test')", language: "python"
    }), params);
    check(resSubmit, { 'Submit de código devuelve 200/201 OK': (r) => r.status === 200 || r.status === 201 });

    // --- Negative Paths ---
    const resBadChallenge = http.get(`${BASE_URL}/challenges/9999999`, params);
    check(resBadChallenge, { 'Reto falso interceptado (404)': (r) => r.status === 404 });

    const resBadSubmit = http.post(`${BASE_URL}/challenges/3/submit`, JSON.stringify({
      sourceCode: "", // Vacío intencionadamente
      language: ""    // Vacío intencionadamente
    }), params);
    check(resBadSubmit, { 'Submit con datos vacíos interceptado (400)': (r) => r.status === 400 });

    const resBadPagination = http.get(`${BASE_URL}/challenges?page=letras&size=10`, params);
    check(resBadPagination, { 'Paginación con formato inválido interceptada (400)': (r) => r.status === 400 });
  });

  // ==========================================
  // 4. PRUEBAS DE SEGURIDAD Y FORMATO (GLOBALES)
  // ==========================================
  group('Global Validation & Security', function () {
    
    // 1. Método HTTP no soportado (Hacemos un GET a una ruta que es POST)
    const resBadMethod = http.get(`${BASE_URL}/gamification/award?userId=${data.userId}&rank=8`, params);
    check(resBadMethod, { 'Método HTTP inválido interceptado (405)': (r) => r.status === 405 });

    // 2. Content-Type no soportado (Enviamos Texto a una ruta que espera estrictamente JSON)
    const badContentParams = {
        headers: { 
            'Content-Type': 'text/plain',
            'Authorization': `Bearer ${data.authToken}`
        }
    };
    const resBadContentType = http.post(`${BASE_URL}/challenges/3/submit`, "esto no es un json", badContentParams);
    check(resBadContentType, { 'Content-Type inválido interceptado (415)': (r) => r.status === 415 });

    // 3. Token Inválido/Ausente (Llamada sin cabecera de Autorización)
    const noAuthParams = { headers: { 'Content-Type': 'application/json' } };
    const resNoAuth = http.get(`${BASE_URL}/users/me`, noAuthParams);
    check(resNoAuth, { 'Llamada sin token interceptada (401)': (r) => r.status === 401 });
  });

  console.log("=== BATERÍA DE VALIDACIÓN FINALIZADA ===");
}