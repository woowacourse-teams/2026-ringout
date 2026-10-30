import type { DeploymentDetail } from '../../api/adminDeployments';
export function serviceState(detail: DeploymentDetail) {
  const { deployment: item, events } = detail;
  const lines = events.split('\n');
  const event = (name: string) => lines.filter(line => new RegExp(`(?:^|\\s)event=${name}(?:\\s|$)`).test(line)).at(-1);
  const port = (line?: string) => line?.match(/(?:^|\s)(?:previous_)?port=(8081|8082)(?:\s|$)/)?.[1] ?? null;
  if (item.status === 'SUCCEEDED') return { image: item.image, imageDigest: item.imageDigest, imageId: item.imageId, port: port(event('TRAFFIC_SWITCHED') ?? event('DEPLOY_SUCCEEDED')), retained: false, uncertain: false };
  // A failed restore or a failure after switching traffic cannot prove which version serves traffic.
  if (event('ROLLBACK_FAILED') || event('TRAFFIC_SWITCHED')) return { image: null, imageDigest: null, imageId: null, port: null, retained: false, uncertain: true };
  const previous = event('ROLLBACK_SUCCEEDED') ?? event('PREVIOUS_SERVER');
  const known = Boolean(item.previousImage && previous);
  return { image: known ? item.previousImage : null, imageDigest: known ? item.previousImageDigest : null, imageId: known ? item.previousImageId : null, port: port(previous), retained: known, uncertain: !known };
}
