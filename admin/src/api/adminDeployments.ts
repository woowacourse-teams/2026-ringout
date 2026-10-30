import { apiResult } from './apiClient';
import type { DeploymentEnvironment } from './deploymentEnvironment';
export type Deployment = {
  id: number; deploymentId: string; environment: string; image: string; imageId: string | null;
  previousImage: string | null; previousImageId: string | null; status: 'SUCCEEDED' | 'FAILED';
  startedAt: string; finishedAt: string; actionType: string;
  actionsRunId: number | null;
  flywayVersion: string | null;
  imageDigest: string | null; previousImageDigest: string | null; rollbackSourceId: number | null;
};
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number; totalPages: number };
export type DeploymentPage = Page<Deployment> & { latestSuccessfulDeploymentId: number | null };
export type DeploymentDetail = { deployment: Deployment; events: string };
const base = '/deployments';
export const apiEnvironment = (environment: DeploymentEnvironment) => environment === 'production' ? 'production' : 'dev';
export const getDeployments = (environment: DeploymentEnvironment, page: number, signal?: AbortSignal) =>
  apiResult<DeploymentPage>(`${base}?${new URLSearchParams({ environment: apiEnvironment(environment), page: String(page), size: '20' })}`, signal);
export const getDeployment = (id: number, signal?: AbortSignal) => apiResult<DeploymentDetail>(`${base}/${id}`, signal);
