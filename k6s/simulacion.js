import http from 'k6/http';
import { check, sleep, group } from 'k6';
import encoding from 'k6/encoding';

export const options = {
  stages: [
    { duration: '10s', target: 100 }, // SPIKE
    { duration: '5m', target: 100 },  
    { duration: '30s', target: 0 },   
  ],
  thresholds: {
    http_req_failed: ['rate<0.50'],   
    http_req_duration: ['p(95)<2000'], 
  },
};

const BASE_URL = 'https://api.saramg.org/api';
const TOKEN_URL = 'https://auth.saramg.org/realms/masorange-realm/protocol/openid-connect/token';

// --- SETUP ---
export function setup() {
  console.log('--- Iniciando Setup ---');
  
  const loginUsername = 'test_load@sara.local';
  
  const loginRes = http.post(TOKEN_URL, {
    grant_type: 'password',
    client_id: 'gamification-client',
    username: loginUsername,
    password: 'password'
  });

  if (!loginRes || !loginRes.body) {
    throw new Error(`[CRÍTICO] El servidor de Auth no responde. ¿Bloqueo de Cloudflare o red caída? Status: ${loginRes.status}`);
  }

  if (loginRes.status !== 200) {
    throw new Error(`[CRÍTICO] Fallo en auth: ${loginRes.status} - ${loginRes.body}`);
  }
  
  const token = loginRes.json('access_token');
  
  const payloadEncoded = token.split('.')[1];
  const payloadDecoded = encoding.b64decode(payloadEncoded, 'rawurl', 's');
  const payload = JSON.parse(payloadDecoded);
  const realUserId = payload.sub; 
  
  console.log(`--- Setup completado. ID: ${realUserId} | User: ${loginUsername} ---`);
  
  return { 
    authToken: token, 
    userId: realUserId,
    username: loginUsername
  };
}

export default function (data) {
  const params = {
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${data.authToken}`
    },
  };

  const accion = Math.random();

  // --- ESCENARIO D (10%): ERRORES PROVOCADOS ---
  if (accion < 0.10) {
    group('Pruebas de Error Provocado', function () {
      
      const resAwardError = http.post(`${BASE_URL}/gamification/award?userId=usuario-fantasma-999&rank=1`, null, params);
      
      console.log(`STATUS REAL Gamification: ${resAwardError.status} | BODY REAL: ${resAwardError.body}`);

      check(resAwardError, {
        'error: 404 o 400 esperado': (r) => r.status === 404 || r.status === 400,
      });

      const resChallengeError = http.get(`${BASE_URL}/challenges/999999`, params);
      check(resChallengeError, {
        'error: reto no encontrado (404)': (r) => r.status === 404,
      });

      sleep(1);
    });
  } 
  // --- ESCENARIO A (40%): Navegación ---
  else if (accion < 0.50) {
    group('Navegación', function () {
      http.get(`${BASE_URL}/challenges?page=0&size=10`, params);
      sleep(3);
    });
  } 
  // --- ESCENARIO B (30%): Envío de Código ---
  else if (accion < 0.80) {
    group('Envío de Código', function () {
      const res = http.post(`${BASE_URL}/challenges/3/submit`, JSON.stringify({
        sourceCode: "print('Test de resiliencia')",
        language: "python"
      }), params);
      
      if (res.status === 200 || res.status === 201) {
        http.post(`${BASE_URL}/gamification/award?userId=${data.userId}&rank=1`, null, params);
      }
      sleep(10);
    });
  }
  // --- ESCENARIO C (20%): Ranking ---
  else {
    group('Consulta de Ranking', function () {
      http.get(`${BASE_URL}/users/ranking/${data.username}`, params);
      sleep(5);
    });
  }
}