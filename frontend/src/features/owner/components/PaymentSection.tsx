import { useState } from "react";
import { Checkbox, NumberedSteps, TextButton } from "../../../components";
import { formatWon } from "../../../lib/money";
import { PAYMENT_METHODS } from "../lib/payment";
import type { PaymentMethod } from "../types";
import RefundGuideSheet from "./RefundGuideSheet";
import "./PaymentSection.css";

interface PaymentSectionProps {
  amount: number;
  method: PaymentMethod;
  onMethodChange: (method: PaymentMethod) => void;
  agreed: boolean;
  onAgreeChange: (agreed: boolean) => void;
  /** 「안전결제는 이렇게 진행돼요」와 취소 기준 상자 (안전결제 화면) */
  showGuide?: boolean;
}

/** 결제 금액 · 결제 수단 · (진행 순서 · 취소 기준) · 약관 동의. 안전결제와 제안 수락이 같이 쓴다 */
function PaymentSection({
  amount,
  method,
  onMethodChange,
  agreed,
  onAgreeChange,
  showGuide = false,
}: PaymentSectionProps) {
  const [guideOpen, setGuideOpen] = useState(false);

  return (
    <>
      <section className="pay-section">
        <h3 className="pay-section__title">결제 금액</h3>
        <dl className="pay-amount">
          <div className="pay-amount__row">
            <dt>작업비</dt>
            <dd>{formatWon(amount)}</dd>
          </div>
          <div className="pay-amount__row">
            <dt>
              수수료 <small>(정식 서비스 론칭 시 적용)</small>
            </dt>
            <dd>0원</dd>
          </div>
          <div className="pay-amount__row pay-amount__row--total">
            <dt>결제할 금액</dt>
            <dd>{formatWon(amount)}</dd>
          </div>
        </dl>
      </section>

      <section className="pay-section">
        <h3 className="pay-section__title">결제 수단</h3>
        <div className="pay-methods" role="radiogroup" aria-label="결제 수단">
          {PAYMENT_METHODS.map(({ value, label }) => (
            <label
              key={value}
              className={`pay-methods__item${method === value ? " pay-methods__item--selected" : ""}`}
            >
              <input
                type="radio"
                name="payment-method"
                className="pay-methods__input"
                checked={method === value}
                onChange={() => onMethodChange(value)}
              />
              <span className="pay-methods__radio" aria-hidden="true" />
              {label}
            </label>
          ))}
        </div>
      </section>

      {showGuide && (
        <>
          <section className="pay-section">
            <h3 className="pay-section__title">안전결제는 이렇게 진행돼요</h3>
            <NumberedSteps
              steps={[
                {
                  title: (
                    <>
                      지금 결제한 돈은 <b>가꿈</b>이 맡아 둬요
                    </>
                  ),
                  description: "학생에게 바로 가지 않아요",
                },
                {
                  title: "결과물을 받고 「완료」를 누르면 학생에게 보내요",
                  description: "초안을 받은 뒤 정한 횟수만큼 고쳐 달라고 할 수 있어요",
                },
                { title: "결과물을 받고 7일 동안 답이 없으면 자동으로 완료돼요" },
              ]}
            />
          </section>

          <div className="pay-refund">
            <strong>취소하면 이렇게 돌려받아요</strong>
            <dl>
              <div className="pay-refund__row">
                <dt>작업 시작 전</dt>
                <dd>전액 환불</dd>
              </div>
              <div className="pay-refund__row">
                <dt>작업 중 (결과물 받기 전)</dt>
                <dd>작업비의 20%를 뺀 금액 환불</dd>
              </div>
              <div className="pay-refund__row">
                <dt>결과물을 받은 뒤</dt>
                <dd>취소할 수 없어요</dd>
              </div>
            </dl>
            <TextButton className="pay-refund__more" onClick={() => setGuideOpen(true)}>
              자세히 보기
            </TextButton>
          </div>
        </>
      )}

      <Checkbox
        checked={agreed}
        onChange={onAgreeChange}
        label="책임 약관과 취소·환불 기준에 동의해요 (필수)"
      />

      <RefundGuideSheet open={guideOpen} amount={amount} onClose={() => setGuideOpen(false)} />
    </>
  );
}

export default PaymentSection;
