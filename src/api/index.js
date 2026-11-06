import { api as realApi } from './client';
import { api as mockApi } from './mock';

/**
 * 统一 API 出口：
 * - 默认走真实后端（agent-server，Vite 代理 /api -> :8080）
 * - `npm run mock`（VITE_API_MOCK=true）时切换为前端内存 mock
 */
const useMock = import.meta.env.VITE_API_MOCK === 'true';

export const api = useMock ? mockApi : realApi;
export const API_MODE = useMock ? 'mock' : 'live';
