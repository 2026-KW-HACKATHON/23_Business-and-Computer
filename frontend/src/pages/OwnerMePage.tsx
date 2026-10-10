import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import {
  Button,
  CategoryBadge,
  Dialog,
  LoadNotice,
  MenuList,
  ProfilePhoto,
  StoreInfo,
  SubScreen,
  SummaryCard,
} from "../components";
import { landingPath, logOut } from "../features/auth";
import {
  OWNER_PATHS,
  ownerMeChanges,
  resolveConcern,
  saveOwnerMe,
  useOpenJobs,
  useOwnerConcern,
  useOwnerMe,
} from "../features/owner";
import type { ActivityTab, OwnerMe } from "../features/owner";
import { PROFILE_PHOTO_ACCEPT, TermsSheet, checkProfilePhoto } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import "./OwnerMePage.css";

const ACTIVITY_TABS: ActivityTab[] = ["sent", "proposals", "inProgress", "done"];

const PHOTO_CHECK_TEXT = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

/**
 * 피그마 「내 정보 · 설정 (사장님)」. 가게 정보 · 요약 · 내 활동 · 설정 · 로그아웃.
 * 가게 정보와 요약 4칸은 GET /owners/me (ADR 0040). 불러오는 중이거나 실패하면 가게 정보 자리에
 * 안내, 요약은 「-」. 사진을 고르면 바로 올리고(PROFILE) PUT /owners/me 로 저장한다.
 * 요약 아래 「우리 가게 고민」에서 지금 고민을 보고 고치거나 「해결됐어요」로 내린다 (ADR 0070).
 */
function OwnerMePage() {
  const navigate = useNavigate();
  const back = useBack(OWNER_PATHS.home);
  const { load, reload } = useOwnerMe();
  // 보낸 의뢰는 내 활동 › 보낸 의뢰 탭과 같게 모집 중인 의뢰만 센다 (GET /owners/me 는 끝난 의뢰까지 센다)
  const { load: openLoad } = useOpenJobs();
  const me = load.status === "loaded" ? load.data : undefined;
  const [termsOpen, setTermsOpen] = useState(false);

  const openActivity = (tab: ActivityTab) => navigate(OWNER_PATHS.activity(tab));

  // 저장된 토큰은 바로 지우고 서버의 refresh 쿠키 폐기(POST /logout)는 기다리지 않는다
  const logout = () => {
    void logOut();
    navigate("/login", { replace: true });
  };

  const counts = [
    openLoad.status === "loaded" ? openLoad.data.length : null,
    me ? me.receivedProposalCount : null,
    me ? me.inProgressJobCount : null,
    me ? me.completedJobCount : null,
  ];

  return (
    <SubScreen title="내 정보" onBack={back}>
      <section className="owner-me__profile">
        {me ? (
          <StoreHead me={me} />
        ) : (
          <LoadNotice
            layout="block"
            status={load.status === "loading" ? "loading" : "error"}
            loadingText="가게 정보를 불러오는 중이에요"
            errorText="가게 정보를 불러오지 못했어요"
            onRetry={reload}
          />
        )}
        <SummaryCard
          items={[
            { label: "보낸 의뢰", count: counts[0] },
            { label: "받은 제안", count: counts[1] },
            { label: "진행 중", count: counts[2] },
            { label: "완료", count: counts[3] },
          ]}
          onSelect={(i) => openActivity(ACTIVITY_TABS[i])}
        />
      </section>

      <ConcernSection />

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
          <MenuList items={[{ label: "약관 및 정책", onClick: () => setTermsOpen(true) }]} />
        </div>
        <MenuList items={[{ label: "로그아웃", danger: true, onClick: logout }]} />
      </section>

      <TermsSheet open={termsOpen} onClose={() => setTermsOpen(false)} tone="owner" />
    </SubScreen>
  );
}

/**
 * 「우리 가게 고민」. 고민이 있으면 분야 · 한 줄 · 설명과 「고치기」 · 「해결됐어요」(한 번 더 묻는다),
 * 없으면 「올려 둔 고민이 없어요」와 「고민 올리기」. 해결하면 다시 불러와 빈 상태로 바뀐다.
 */
function ConcernSection() {
  const navigate = useNavigate();
  const { load, reload } = useOwnerConcern();
  const [confirming, setConfirming] = useState(false);
  const [resolving, setResolving] = useState(false);
  const concern = load.status === "loaded" ? load.concern : null;

  const resolve = async () => {
    if (resolving) return;
    setResolving(true);
    const result = await resolveConcern();
    setResolving(false);
    setConfirming(false);
    if (result.status === "resolved") {
      reload();
    } else if (result.status === "unauthorized") {
      navigate("/login", { replace: true });
    } else if (result.status === "forbidden") {
      window.alert("사장님만 가게 고민을 내릴 수 있어요");
      navigate(landingPath(), { replace: true });
    } else {
      window.alert("고민을 내리지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <section className="owner-me__section">
      <h2 className="owner-me__section-title">우리 가게 고민</h2>
      {load.status !== "loaded" ? (
        <LoadNotice
          status={load.status}
          loadingText="가게 고민을 불러오는 중이에요"
          errorText="가게 고민을 불러오지 못했어요"
          onRetry={reload}
        />
      ) : concern ? (
        <>
          <div className="owner-me__concern">
            {concern.categoryName && <CategoryBadge field={concern.categoryName} />}
            <p className="owner-me__concern-title">{concern.title}</p>
            {concern.description && <p className="owner-me__concern-body">{concern.description}</p>}
          </div>
          <div className="owner-me__concern-actions">
            <Button variant="secondary" onClick={() => navigate(OWNER_PATHS.concern)}>
              고치기
            </Button>
            <Button onClick={() => setConfirming(true)}>해결됐어요</Button>
          </div>
        </>
      ) : (
        <>
          <p className="owner-me__concern-empty">올려 둔 고민이 없어요. 고민을 올리면 학생들이 먼저 제안해 줘요</p>
          <Button variant="secondary" onClick={() => navigate(OWNER_PATHS.concern)}>
            고민 올리기
          </Button>
        </>
      )}

      <Dialog
        open={confirming}
        title="고민이 해결됐나요?"
        description={"고민을 내리면 학생 가게 목록에서 사라져요.\n새 고민은 언제든 다시 올릴 수 있어요."}
        onClose={() => setConfirming(false)}
        actions={
          <div className="owner-me__dialog-actions">
            <Button variant="secondary" onClick={() => setConfirming(false)}>
              아직이에요
            </Button>
            <Button loading={resolving} loadingLabel="내리는 중" onClick={() => void resolve()}>
              해결됐어요
            </Button>
          </div>
        }
      />
    </section>
  );
}

/**
 * 가게 사진 · 상호명 · 사장님 이름 · 주소, 「사업자 인증 완료」 · 「가게 정보 수정」.
 * 고른 사진은 올리는 동안 미리 보여 주고, 실패하면 알림 뒤 원래 사진으로 돌아간다.
 */
function StoreHead({ me }: { me: OwnerMe }) {
  const navigate = useNavigate();
  const [savedPhotoUrl, setSavedPhotoUrl] = useState(me.profileImageUrl ?? "");
  const [pendingPhoto, setPendingPhoto] = useState<File>();
  const pendingFiles = useMemo(() => (pendingPhoto ? [pendingPhoto] : []), [pendingPhoto]);
  const [pendingUrl] = useObjectUrls(pendingFiles);
  // 올리는 동안 다시 골라도 한 번만 보낸다
  const saving = useRef(false);

  const changePhoto = async (file: File) => {
    if (saving.current) return;
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      window.alert(PHOTO_CHECK_TEXT[check]);
      return;
    }
    saving.current = true;
    setPendingPhoto(file);
    const result = await saveOwnerMe({ ...ownerMeChanges(me), profileImageUrl: savedPhotoUrl }, file);
    saving.current = false;
    setPendingPhoto(undefined);
    if (result.status === "saved") {
      setSavedPhotoUrl(result.profileImageUrl);
    } else if (result.status === "unauthorized") {
      navigate("/login", { replace: true });
    } else if (result.status === "forbidden") {
      window.alert("사장님만 가게 사진을 바꿀 수 있어요");
      navigate(landingPath(), { replace: true });
    } else if (result.status === "photoFailed") {
      window.alert("사진을 올리지 못했어요. 다시 시도해 주세요");
    } else {
      window.alert("사진을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <>
      <div className="owner-me__head">
        <ProfilePhoto
          size="small"
          src={pendingUrl || savedPhotoUrl || undefined}
          accept={PROFILE_PHOTO_ACCEPT}
          onSelect={(file) => void changePhoto(file)}
        />
        <StoreInfo storeName={me.storeName} ownerName={me.name} address={me.storeAddress ?? ""} />
      </div>
      <div className="owner-me__badges">
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
        <button type="button" className="owner-me__edit" onClick={() => navigate(OWNER_PATHS.store)}>
          가게 정보 수정
        </button>
      </div>
    </>
  );
}

export default OwnerMePage;
