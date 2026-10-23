export function providerName(provider: string) {
  return ({ GOOGLE: 'Google', APPLE: 'Apple', KAKAO: '카카오' } as Record<string, string>)[provider] ?? provider;
}
