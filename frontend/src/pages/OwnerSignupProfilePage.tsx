import { useMemo } from "react";
import type { ChangeEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, Button, ProfilePhoto, StepIndicator, StoreInfo, TextField } from "../components";
import { useOwnerSignup } from "../features/signup";
import { useObjectUrls } from "../hooks/useObjectUrls";
import "./SignupPage.css";
import "./OwnerSignupProfilePage.css";

const MAX_STORE_PHOTOS = 5;

/** 피그마 「회원가입 - 프로필 입력(사장님) 3/3」 */
function OwnerSignupProfilePage() {
  const navigate = useNavigate();
  const { draft, update } = useOwnerSignup();
  const profileFiles = useMemo(
    () => (draft.profilePhoto ? [draft.profilePhoto] : []),
    [draft.profilePhoto],
  );
  const [profileUrl] = useObjectUrls(profileFiles);
  const storePhotoUrls = useObjectUrls(draft.storePhotos);

  // 인증을 마치지 않고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (draft.business.check !== "verified") return <Navigate to="/signup/role" replace />;

  const handleAddPhotos = (e: ChangeEvent<HTMLInputElement>) => {
    const files = e.target.files;
    if (files) {
      const next = [...draft.storePhotos, ...Array.from(files)].slice(0, MAX_STORE_PHOTOS);
      update({ storePhotos: next });
    }
    e.target.value = "";
  };

  const removeStorePhoto = (index: number) => {
    update({ storePhotos: draft.storePhotos.filter((_, i) => i !== index) });
  };

  // 백엔드 연동 전: 가입 저장 없이 완료 화면으로 간다. 뒤로 가기로 돌아오지 않게 교체한다.
  const handleComplete = () => {
    update({ completed: true });
    navigate("/signup/owner/done", { replace: true });
  };

  const photoInput = (
    <input
      type="file"
      accept="image/*"
      multiple
      className="owner-signup-profile__input"
      onChange={handleAddPhotos}
    />
  );

  return (
    <div className="signup">
      <AppBar
        title="프로필 입력"
        onBack={() => navigate("/signup/owner/2")}
        muted
        bottom={<StepIndicator total={3} current={3} tone="owner" />}
      />

      <main className="signup__body">
        <div className="owner-signup-profile__head">
          <ProfilePhoto src={profileUrl} onSelect={(profilePhoto) => update({ profilePhoto })} />
          <StoreInfo storeName={draft.storeName} ownerName={draft.name} address={draft.storeAddress} />
        </div>

        <TextField
          className="owner-signup-profile__description"
          placeholder="매장 소개"
          aria-label="매장 소개"
          value={draft.description}
          onChange={(e) => update({ description: e.target.value })}
        />

        <h2 className="owner-signup-profile__photos-title">
          매장 대표사진<span className="owner-signup-profile__optional">(선택)</span>
        </h2>
        <p className="owner-signup-profile__photos-note">*최대 5장까지 선택할 수 있어요</p>

        {draft.storePhotos.length === 0 ? (
          <label className="owner-signup-profile__upload">
            <span className="owner-signup-profile__plus" aria-hidden="true">
              +
            </span>
            이미지를 업로드 해주세요!
            {photoInput}
          </label>
        ) : (
          <ul className="owner-signup-profile__grid">
            {storePhotoUrls.map((url, i) => (
              <li key={url} className="owner-signup-profile__thumb">
                <img src={url} alt={`매장 사진 ${i + 1}`} />
                <button
                  type="button"
                  className="owner-signup-profile__remove"
                  onClick={() => removeStorePhoto(i)}
                  aria-label={`매장 사진 ${i + 1} 빼기`}
                >
                  ✕
                </button>
              </li>
            ))}
            {draft.storePhotos.length < MAX_STORE_PHOTOS && (
              <li>
                <label className="owner-signup-profile__add" aria-label="매장 사진 더 추가">
                  <span className="owner-signup-profile__plus owner-signup-profile__plus--small" aria-hidden="true">
                    +
                  </span>
                  {photoInput}
                </label>
              </li>
            )}
          </ul>
        )}
      </main>

      <footer className="signup__footer">
        <Button fullWidth onClick={handleComplete}>
          회원가입 완료
        </Button>
      </footer>
    </div>
  );
}

export default OwnerSignupProfilePage;
