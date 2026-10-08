import { useEffect, useMemo, useRef, useState } from "react";
import type { ChangeEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, Button, ProfilePhoto, StepIndicator, StoreInfo, TextField } from "../components";
import { landingPath } from "../features/auth";
import {
  PROFILE_PHOTO_ACCEPT,
  checkProfilePhoto,
  findBusinessCategoryId,
  registerOwnerSignup,
  uploadSignupPhoto,
  uploadStorePhoto,
  useOwnerSignup,
} from "../features/signup";
import type { PhotoUploadResult } from "../features/signup";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { markSignupGuide } from "../lib/signupGuide";
import "./SignupPage.css";
import "./OwnerSignupProfilePage.css";

const MAX_STORE_PHOTOS = 5;

type PhotoError = "type" | "size" | null;
type SubmitError =
  | "photo"
  | "category"
  | "businessNumberTaken"
  | "invalidInput"
  | "dataConflict"
  | "retry"
  | null;

const PHOTO_ERROR_TEXT: Record<Exclude<PhotoError, null>, string> = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

const SUBMIT_ERROR_TEXT: Record<Exclude<SubmitError, null>, string> = {
  photo: "사진을 올리지 못했어요. 다시 시도해 주세요",
  category: "업종 정보를 불러오지 못했어요. 잠시 후 다시 시도해 주세요",
  businessNumberTaken: "이미 다른 계정에서 가입한 사업자등록번호예요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  dataConflict: "일시적인 문제가 생겼어요. 다시 시도해도 안 되면 문의해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

/**
 * 피그마 「회원가입 - 프로필 입력(사장님) 3/3」.
 * 「회원가입 완료」는 고른 업종의 서버 id 를 찾고, 사진을 올린 뒤 POST /auth/owner 로 저장한다.
 * 요청 중·실패는 피그마에 없어 버튼 문구와 아래 안내 문구로만 보여준다 (학생 가입과 같은 규칙, ADR 0019).
 */
function OwnerSignupProfilePage() {
  const navigate = useNavigate();
  const { draft, update } = useOwnerSignup();
  const profileFiles = useMemo(
    () => (draft.profilePhoto ? [draft.profilePhoto] : []),
    [draft.profilePhoto],
  );
  const [profileUrl] = useObjectUrls(profileFiles);
  const storePhotoUrls = useObjectUrls(draft.storePhotos);
  const [photoError, setPhotoError] = useState<PhotoError>(null);
  // 요청 중 여부와 오류는 이 화면에만 둔다 (학생 3/3 과 같은 방식)
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitError>(null);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);
  // state 가 다시 그려지기 전에 버튼이 두 번 눌려도 가입 요청은 한 번만 보낸다
  const inFlight = useRef(false);
  // 다시 시도할 때 같은 사진을 또 올리지 않도록 사진마다 올린 주소를 기억한다
  const uploaded = useRef(new Map<File, string>());

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  // 인증을 마치지 않고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (draft.business.check !== "verified" || draft.category === null) {
    return <Navigate to="/signup/role" replace />;
  }
  const category = draft.category;

  const handleProfilePhoto = (file: File) => {
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      setPhotoError(check);
      return;
    }
    setPhotoError(null);
    if (submitError === "photo") setSubmitError(null);
    update({ profilePhoto: file });
  };

  const handleAddPhotos = (e: ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    // 형식·크기가 맞지 않는 사진은 빼고, 왜 빠졌는지 알려준다
    const checks = files.map(checkProfilePhoto);
    const rejected = checks.find((check) => check !== "ok");
    setPhotoError(rejected ?? null);
    const accepted = files.filter((_, i) => checks[i] === "ok");
    if (accepted.length === 0) return;
    if (submitError === "photo") setSubmitError(null);
    update({ storePhotos: [...draft.storePhotos, ...accepted].slice(0, MAX_STORE_PHOTOS) });
  };

  const removeStorePhoto = (index: number) => {
    update({ storePhotos: draft.storePhotos.filter((_, i) => i !== index) });
  };

  // 업종 id → 사진 → 가입 저장 순서로 보낸다. 중간에 실패하면 거기서 멈춘다
  const submit = async () => {
    const id = ++requestId.current;
    const stale = () => id !== requestId.current;
    const stop = (error: Exclude<SubmitError, null>) => {
      setSubmitting(false);
      setSubmitError(error);
    };
    setSubmitting(true);
    setSubmitError(null);

    const lookup = await findBusinessCategoryId(category);
    if (stale()) return;
    if (lookup.status === "unauthorized") {
      navigate("/login", { replace: true });
      return;
    }
    if (lookup.status !== "found") {
      stop("category");
      return;
    }

    // 사진 한 장을 올리고 주소를 돌려준다. 이미 올린 사진은 다시 올리지 않는다. 실패하면 null
    const uploadUrl = async (
      file: File,
      upload: (file: File) => Promise<PhotoUploadResult>,
    ): Promise<string | null> => {
      const saved = uploaded.current.get(file);
      if (saved) return saved;
      const result = await upload(file);
      if (stale()) return null;
      if (result.status === "unauthorized") {
        navigate("/login", { replace: true });
        return null;
      }
      if (result.status === "failed") {
        stop("photo");
        return null;
      }
      uploaded.current.set(file, result.imageUrl);
      return result.imageUrl;
    };

    let profileImageUrl = "";
    if (draft.profilePhoto) {
      const url = await uploadUrl(draft.profilePhoto, uploadSignupPhoto);
      if (url === null) return;
      profileImageUrl = url;
    }
    const storeImageUrls: string[] = [];
    for (const photo of draft.storePhotos) {
      const url = await uploadUrl(photo, uploadStorePhoto);
      if (url === null) return;
      storeImageUrls.push(url);
    }

    // 성공하면 새 토큰은 응답을 버려도 이미 저장되어 있다
    const result = await registerOwnerSignup(draft, lookup.id, { profileImageUrl, storeImageUrls });
    if (stale()) return;
    setSubmitting(false);

    switch (result) {
      case "registered":
        // 뒤로 가기로 돌아오지 않게 교체한다
        update({ completed: true });
        // 가입 후 첫 홈에서 한 번 안내한다 (ADR 0053)
        markSignupGuide("owner");
        navigate("/signup/owner/done", { replace: true });
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "alreadyRegistered":
        window.alert("이미 가입을 마친 계정이에요");
        navigate(landingPath(), { replace: true });
        break;
      case "categoryInvalid":
        setSubmitError("category");
        break;
      case "businessNumberTaken":
      case "invalidInput":
      case "dataConflict":
        setSubmitError(result);
        break;
      default:
        setSubmitError("retry");
    }
  };

  const handleComplete = async () => {
    if (inFlight.current) return;
    inFlight.current = true;
    try {
      await submit();
    } finally {
      inFlight.current = false;
    }
  };

  const photoInput = (
    <input
      type="file"
      accept={PROFILE_PHOTO_ACCEPT}
      multiple
      disabled={submitting}
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
          <ProfilePhoto
            src={profileUrl}
            accept={PROFILE_PHOTO_ACCEPT}
            onSelect={(file) => {
              if (!submitting) handleProfilePhoto(file);
            }}
          />
          <StoreInfo storeName={draft.storeName} ownerName={draft.name} address={draft.storeAddress} />
        </div>

        <TextField
          className="owner-signup-profile__description"
          placeholder="매장 소개"
          aria-label="매장 소개"
          value={draft.description}
          readOnly={submitting}
          onChange={(e) => update({ description: e.target.value })}
        />

        <h2 className="owner-signup-profile__photos-title">
          매장 대표사진<span className="owner-signup-profile__optional">(선택)</span>
        </h2>
        <p className="owner-signup-profile__photos-note">*최대 5장까지 선택할 수 있어요</p>
        {photoError && (
          <p className="owner-signup-profile__error" role="alert">
            {PHOTO_ERROR_TEXT[photoError]}
          </p>
        )}

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
                  disabled={submitting}
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
        {submitError && (
          <p className="owner-signup-profile__error owner-signup-profile__submit-error" role="alert">
            {SUBMIT_ERROR_TEXT[submitError]}
          </p>
        )}
        <Button
          loading={submitting}
          loadingLabel="가입 중"
          fullWidth
          disabled={submitting}
          onClick={() => void handleComplete()}
        >
          회원가입 완료
        </Button>
      </footer>
    </div>
  );
}

export default OwnerSignupProfilePage;
