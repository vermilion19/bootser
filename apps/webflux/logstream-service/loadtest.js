import http from 'k6/http';
import { check } from 'k6';

export const options = {
  // 가상 사용자(Virtual Users) 100명이 동시에
  vus: 100,
  // 30초 동안 공격
  duration: '30s',
};

export default function () {
  const url = 'http://localhost:8080/logs';
  const payload = 'Dummy Log Data for Windows Load Test';
  
  const params = {
    headers: {
      'Content-Type': 'text/plain',
    },
  };

  http.post(url, payload, params);
}