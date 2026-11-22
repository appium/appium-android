/** Compares dotted version strings numerically, missing trailing segments count as `0` */
export function compareVersions(a, b) {
  const [pa, pb] = [a, b].map((v) => v.split('.').map(Number));
  const len = Math.max(pa.length, pb.length);
  for (let i = 0; i < len; i++) {
    const diff = (pa[i] ?? 0) - (pb[i] ?? 0);
    if (diff !== 0) {
      return diff;
    }
  }
  return 0;
}
