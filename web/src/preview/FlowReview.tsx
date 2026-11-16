import { FLOW_SCENARIOS } from "./flowScenarios";
import "./flow-review.css";

export function FlowReview() {
  return (
    <main className="flow-review">
      <p className="eyebrow">RINGOUT · STEP 03</p>
      <h1>숲 화면 확인하기</h1>
      <p className="review-intro">
        앱에 들어갈 화면을 상태별로 확인하세요.
        <br />이 목록은 개발용이며 앱 화면에는 포함되지 않습니다.
      </p>
      <div className="review-scenarios">
        {FLOW_SCENARIOS.map((scenario) => (
          <a key={scenario.id} href={`?view=forest&scenario=${scenario.id}`}>
            <strong>{scenario.title}</strong>
            <span>{scenario.detail}</span>
          </a>
        ))}
      </div>
      <p className="review-note">
        샘플 데이터만 사용합니다. 새로고침하면 초기 상태로 돌아갑니다.
        <br />
        실제 Room 저장·앱 홈 이동은 4단계에서 연결합니다.
      </p>
      <a href="?view=preview">2단계 3D 모델 미리보기 →</a>
    </main>
  );
}
