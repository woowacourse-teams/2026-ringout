export type DeploymentEnvironment = 'production' | 'development';

export const deploymentEnvironments = {
  production: { label: '운영' },
  development: { label: '개발' },
} as const;
