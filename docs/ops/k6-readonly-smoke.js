import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  scenarios: {
    readonly_smoke: {
      executor: 'constant-vus',
      vus: Number(__ENV.VUS || 5),
      duration: __ENV.DURATION || '2m',
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const baseUrl = (__ENV.BASE_URL || 'http://127.0.0.1:8080').replace(/\/$/, '');
const token = __ENV.ACCESS_TOKEN;

export default function () {
  const health = http.get(`${baseUrl}/actuator/health/liveness`);
  check(health, { 'liveness is healthy': (response) => response.status === 200 });

  if (token) {
    const params = { headers: { Authorization: `Bearer ${token}` } };
    const applications = http.get(`${baseUrl}/api/applications?page=1&size=20`, params);
    check(applications, { 'applications is successful': (response) => response.status === 200 });
    const stats = http.get(`${baseUrl}/api/stats/overview`, params);
    check(stats, { 'stats is successful': (response) => response.status === 200 });
  }
  sleep(1);
}
