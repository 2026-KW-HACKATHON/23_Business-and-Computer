import { useMemo, useState } from "react";
import { useNavigate } from "react-router-dom";
import { MenuList, ProfilePhoto, StoreInfo, SubScreen, SummaryCard } from "../components";
import { clearTokens } from "../features/auth";
import {
  OWNER_PATHS,
  setOwnerStorePhoto,
  useOwnerProfile,
  useOwnerStorePhoto,
  useOpenJobs,
  useReceivedProposals,
} from "../features/owner";
import type { ActivityTab } from "../features/owner";
import { TermsSheet } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import "./OwnerMePage.css";

const ACTIVITY_TABS: ActivityTab[] = ["sent", "proposals", "inProgress", "done"];

/**
 * 피그마 「내 정보 · 설정 (사장님)」. 가게 정보 · 요약 · 내 활동 · 설정 · 로그아웃.
 * 받은 제안 개수는 GET /me/received-proposals (ADR 0025). 불러오는 중이거나 실패하면 「-」.
 */
function OwnerMePage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const profile = useOwnerProfile();
  // 사진 업로드는 백엔드 연동 전까지 미리보기만 한다 (가게 정보 수정과 같은 사진)
  const photo = useOwnerStorePhoto();
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [termsOpen, setTermsOpen] = useState(false);
  const { counts } = profile;
  const { load: proposalsLoad } = useReceivedProposals();
  const { load: openLoad } = useOpenJobs();

  const openActivity = (tab: ActivityTab) => navigate(OWNER_PATHS.activity(tab));

  const logout = () => {
    clearTokens();
    navigate("/login", { replace: true });
  };

  return (
    <SubScreen title="내 정보" onBack={back}>
      <section className="owner-me__profile">
        <div className="owner-me__head">
          <ProfilePhoto size="small" src={photoUrl} onSelect={setOwnerStorePhoto} />
          <StoreInfo
            storeName={profile.storeName}
            ownerName={profile.ownerName}
            address={profile.address}
          />
        </div>
        <div className="owner-me__badges">
          {profile.businessVerified && (
            <span className="owner-me__verified">
              <span className="owner-me__verified-check" aria-hidden="true">
                <svg viewBox="0 0 10 10" fill="none">
                  <path
                    d="m2 5.2 2 2L8 3"
                    stroke="currentColor"
                    strokeWidth="1.6"
                    strokeLinecap="round"
                    strokeLinejoin="round"
                  />
                </svg>
              </span>
              사업자 인증 완료
            </span>
          )}
          <button
            type="button"
            className="owner-me__edit"
            onClick={() => navigate(OWNER_PATHS.store)}
          >
            가게 정보 수정
          </button>
        </div>
        <SummaryCard
          items={[
            { label: "보낸 의뢰", count: openLoad.status === "loaded" ? openLoad.data.length : "-" },
            {
              label: "받은 제안",
              count: proposalsLoad.status === "loaded" ? proposalsLoad.proposals.length : "-",
            },
            { label: "진행 중", count: counts.inProgress },
            { label: "완료", count: counts.done },
          ]}
          onSelect={(i) => openActivity(ACTIVITY_TABS[i])}
        />
      </section>

      <section className="owner-me__section">
        <h2 className="owner-me__section-title">내 활동</h2>
        <MenuList
          items={[
            { label: "보낸 의뢰", onClick: () => openActivity("sent") },
            { label: "받은 제안", onClick: () => openActivity("proposals") },
            { label: "진행 중", onClick: () => openActivity("inProgress") },
            { label: "완료 및 결제 내역", onClick: () => openActivity("done") },
          ]}
        />
      </section>

      <section className="owner-me__section owner-me__section--settings">
        <div className="owner-me__group">
          <h2 className="owner-me__section-title">설정</h2>
          <MenuList
            items={[
              { label: "알림 설정" },
              { label: "약관 및 정책", onClick: () => setTermsOpen(true) },
            ]}
          />
        </div>
        <MenuList items={[{ label: "로그아웃", danger: true, onClick: logout }]} />
      </section>

      <TermsSheet open={termsOpen} onClose={() => setTermsOpen(false)} tone="owner" />
    </SubScreen>
  );
}

export default OwnerMePage;
