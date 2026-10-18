const API_BASE = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080/api';

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers: { 'Content-Type': 'application/json', ...(init.headers || {}) },
  });
  const body = await response.json().catch(() => ({}));
  if (!response.ok) throw new Error(body.message || `Request failed (${response.status})`);
  return body as T;
}

export type Repository = { id: number; name: string; localPath: string; projectType: string; createdAt: string | null };
export type ScanStatus = 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'FAILED' | 'CANCELLED';
export type Scan = { id: number; repositoryId: number; repositoryName: string; status: ScanStatus; summary: string | null; runBuild: boolean; runLint: boolean; startedAt: string | null; completedAt: string | null; createdAt: string | null; diagnosticsCount: number };
export type Diagnostic = { id: number; scanId: number; errorType: string; severity: string; filePath: string | null; lineNumber: number | null; message: string; evidence: string | null };
export type Repair = { id: number; scanId: number; diagnosticId: number | null; status: string; explanation: string; patchJson: string; modelName: string; validationSummary: string | null; createdAt: string | null };
export type PatchFile = { path: string; content: string; reason: string };
export type PatchPayload = { baseCommit: string; files: PatchFile[]; cautions?: string };
export type Health = { backend: boolean; database: boolean; ollamaReachable: boolean; model: string; message: string };
export type Validation = { id: number; repairId: number; commandName: string; status: string; exitCode: number | null; outputSummary: string; executedAt: string | null };

export const api = {
  health: () => request<Health>('/health'),
  model: () => request<{ reachable: boolean; configuredModel: string; message?: string }>('/settings/model'),
  repositories: () => request<Repository[]>('/repositories'),
  registerRepository: (path: string) => request<Repository>('/repositories', { method: 'POST', body: JSON.stringify({ path }) }),
  scans: () => request<Scan[]>('/scans'),
  scansForRepository: (id: number) => request<Scan[]>(`/scans/repository/${id}`),
  scan: (id: number) => request<Scan>(`/scans/${id}`),
  diagnostics: (id: number) => request<Diagnostic[]>(`/scans/${id}/diagnostics`),
  startScan: (repositoryId: number, runBuild: boolean, runLint: boolean) => request<Scan>('/scans', { method: 'POST', body: JSON.stringify({ repositoryId, runBuild, runLint }) }),
  explain: (id: number) => request<{ explanation: string; model: string }>(`/diagnostics/${id}/explain`, { method: 'POST', body: '{}' }),
  generateRepair: (diagnosticId: number) => request<Repair>('/repairs', { method: 'POST', body: JSON.stringify({ diagnosticId }) }),
  repair: (id: number) => request<Repair>(`/repairs/${id}`),
  repairsForScan: (id: number) => request<Repair[]>(`/scans/${id}/repairs`),
  validateRepair: (id: number) => request<Repair>(`/repairs/${id}/validate`, { method: 'POST', body: '{}' }),
  applyRepair: (id: number) => request<Repair>(`/repairs/${id}/apply`, { method: 'POST', body: '{}' }),
  validations: (id: number) => request<Validation[]>(`/repairs/${id}/validations`),
};
