import { Construction } from 'lucide-react';

export default function DevelopmentPreview({ title }: { title: string }) {
  return (
    <section className="development-preview-shell" aria-label={title}>
      <p className="development-notice" role="status">
        <Construction size={17} />
        <span>{title} 화면은 개발 중입니다.</span>
      </p>
    </section>
  );
}
