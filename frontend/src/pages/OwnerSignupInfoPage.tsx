import { useState } from "react";
import { useNavigate } from "react-router-dom";
import { AppBar, Button, Checkbox, Chip, StepIndicator, TextField } from "../components";
import { TermsSheet, isStoreInfoComplete, useOwnerSignup } from "../features/signup";
import { STORE_CATEGORIES } from "../types/storeCategory";
import "./OwnerSignupPage.css";
import "./OwnerSignupInfoPage.css";

/** 피그마 「회원가입 - 프로필 입력(사장님) 1/3」 */
function OwnerSignupInfoPage() {
  const navigate = useNavigate();
  const { draft, update } = useOwnerSignup();
  const [termsOpen, setTermsOpen] = useState(false);

  return (
    <div className="owner-signup">
      <AppBar
        title="가게 정보 입력"
        onBack={() => navigate("/signup/role")}
        muted
        bottom={<StepIndicator total={3} current={1} tone="owner" />}
      />

      <main className="owner-signup__body">
        <section className="owner-signup__section">
          <h2 className="owner-signup__section-title">사장님 정보</h2>
          <TextField
            placeholder="이름"
            aria-label="이름"
            autoComplete="name"
            value={draft.name}
            onChange={(e) => update({ name: e.target.value })}
          />
        </section>

        <section className="owner-signup__section">
          <h2 className="owner-signup__section-title">업종</h2>
          <div className="owner-signup-info__chips" role="group" aria-label="업종 (하나만 선택)">
            {STORE_CATEGORIES.map((category) => (
              <Chip
                key={category}
                label={category}
                selected={draft.category === category}
                onClick={() => update({ category })}
              />
            ))}
          </div>
        </section>

        <section className="owner-signup__section">
          <h2 className="owner-signup__section-title">가게 세부 정보</h2>
          <div className="owner-signup__fields">
            <TextField
              placeholder="가게 이름"
              aria-label="가게 이름"
              value={draft.storeName}
              onChange={(e) => update({ storeName: e.target.value })}
            />
            <TextField
              placeholder="매장 주소"
              aria-label="매장 주소"
              autoComplete="street-address"
              value={draft.storeAddress}
              onChange={(e) => update({ storeAddress: e.target.value })}
            />
          </div>
          <div className="owner-signup-info__terms">
            <Checkbox
              checked={draft.agreedToTerms}
              onChange={(agreedToTerms) => update({ agreedToTerms })}
              label="이용약관에 모두 동의해요"
            />
            <button
              type="button"
              className="owner-signup-info__terms-view"
              onClick={() => setTermsOpen(true)}
            >
              보기
            </button>
          </div>
        </section>
      </main>

      <footer className="owner-signup__footer">
        <Button fullWidth disabled={!isStoreInfoComplete(draft)} onClick={() => navigate("/signup/owner/2")}>
          다음
        </Button>
      </footer>

      <TermsSheet open={termsOpen} onClose={() => setTermsOpen(false)} tone="owner" />
    </div>
  );
}

export default OwnerSignupInfoPage;
