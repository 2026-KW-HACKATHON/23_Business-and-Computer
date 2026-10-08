import { useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Chip, LoadNotice, ProfilePhoto, SubScreen, TextField } from "../components";
import { landingPath } from "../features/auth";
import {
  OWNER_PATHS,
  businessInfoText,
  ownerStoreForm,
  saveOwnerMe,
  storeAddressOf,
  storeCategoryId,
  useOwnerMe,
  useStoreCategories,
} from "../features/owner";
import type { OwnerMe, OwnerStoreForm } from "../features/owner";
import { PROFILE_PHOTO_ACCEPT, checkProfilePhoto } from "../features/signup";
import type { BusinessCategory } from "../features/signup";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { STORE_CATEGORIES } from "../types/storeCategory";
import "./OwnerStoreEditPage.css";

const PHOTO_CHECK_TEXT = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

/**
 * 피그마 「가게 정보 수정」. 지금 값은 GET /owners/me, 업종 칩과 서버 id 는 GET /business-categories,
 * 「저장하기」는 PUT /owners/me (ADR 0040). 업종은 가입 1/3과 같은 칩 11개 중 하나.
 * 사업자 정보(대표자 · 사업자번호)는 인증된 값이라 잠겨 있다.
 */
function OwnerStoreEditPage() {
  const back = useBack(OWNER_PATHS.me);
  const { load: meLoad, reload: reloadMe } = useOwnerMe();
  const { load: categoriesLoad, reload: reloadCategories } = useStoreCategories();

  if (meLoad.status === "loaded" && categoriesLoad.status === "loaded") {
    return <StoreForm me={meLoad.data} categories={categoriesLoad.data} onBack={back} />;
  }
  const failed = meLoad.status === "error" || categoriesLoad.status === "error";
  return (
    <SubScreen title="가게 정보 수정" onBack={back}>
      <LoadNotice
        status={failed ? "error" : "loading"}
        loadingText="가게 정보를 불러오는 중이에요"
        errorText="가게 정보를 불러오지 못했어요"
        onRetry={() => {
          if (meLoad.status === "error") reloadMe();
          if (categoriesLoad.status === "error") reloadCategories();
        }}
      />
    </SubScreen>
  );
}

/**
 * 불러온 값으로 채운 입력 칸. 새 사진은 「저장하기」를 누를 때 올린다.
 * 보내는 동안 「저장하는 중...」, 성공하면 내 정보로 돌아간다.
 */
function StoreForm({
  me,
  categories,
  onBack,
}: {
  me: OwnerMe;
  categories: BusinessCategory[];
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const [form, setForm] = useState<OwnerStoreForm>(() => ownerStoreForm(me, categories));
  const [photo, setPhoto] = useState<File>();
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [photoError, setPhotoError] = useState<string>();
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string>();
  // 빠른 두 번 누름에도 한 번만 보낸다
  const inFlight = useRef(false);

  const update = (patch: Partial<OwnerStoreForm>) => setForm((f) => ({ ...f, ...patch }));
  const categoryId = form.category && storeCategoryId(categories, form.category);
  const filled = form.storeName.trim() !== "" && form.address.trim() !== "" && categoryId !== undefined;

  const pickPhoto = (file: File) => {
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      setPhotoError(PHOTO_CHECK_TEXT[check]);
      return;
    }
    setPhotoError(undefined);
    setPhoto(file);
  };

  const save = async () => {
    if (inFlight.current || categoryId === undefined) return;
    inFlight.current = true;
    setSaving(true);
    setSaveError(undefined);
    const result = await saveOwnerMe(
      {
        storeName: form.storeName,
        categoryId,
        storeAddress: storeAddressOf(form),
        description: form.intro,
        profileImageUrl: me.profileImageUrl ?? "",
      },
      photo,
    );
    inFlight.current = false;
    setSaving(false);
    switch (result.status) {
      case "saved":
        return onBack();
      case "unauthorized":
        return navigate("/login", { replace: true });
      case "forbidden":
        window.alert("사장님만 가게 정보를 바꿀 수 있어요");
        return navigate(landingPath(), { replace: true });
      case "photoFailed":
        return setSaveError("사진을 올리지 못했어요. 다시 시도해 주세요");
      case "invalidInput":
        return setSaveError("입력한 내용을 다시 확인해 주세요");
      default:
        return setSaveError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title="가게 정보 수정"
      onBack={onBack}
      footer={
        <>
          {saveError && (
            <p className="owner-store__send-error" role="alert">
              {saveError}
            </p>
          )}
          <Button fullWidth disabled={!filled || saving} onClick={() => void save()}>
            {saving ? "저장하는 중..." : "저장하기"}
          </Button>
        </>
      }
    >
      <div className="owner-store">
        <div className="owner-store__photo">
          <ProfilePhoto
            size="medium"
            src={photoUrl || me.profileImageUrl || undefined}
            accept={PROFILE_PHOTO_ACCEPT}
            onSelect={pickPhoto}
          />
          <span>가게 사진 바꾸기</span>
          {photoError && (
            <p className="owner-store__photo-error" role="alert">
              {photoError}
            </p>
          )}
        </div>

        <label className="owner-store__field">
          <span className="owner-store__label">상호명</span>
          <TextField
            value={form.storeName}
            maxLength={255}
            onChange={(e) => update({ storeName: e.target.value })}
          />
        </label>

        <div className="owner-store__field">
          <span className="owner-store__label">업종</span>
          <div className="owner-store__chips" role="group" aria-label="업종">
            {STORE_CATEGORIES.map((category) => (
              <Chip
                key={category}
                label={category}
                selected={form.category === category}
                onClick={() => update({ category })}
              />
            ))}
          </div>
        </div>

        <div className="owner-store__field">
          <span className="owner-store__label">가게 주소</span>
          <TextField
            aria-label="가게 주소"
            value={form.address}
            onChange={(e) => update({ address: e.target.value })}
          />
          <TextField
            aria-label="상세 주소"
            placeholder="상세 주소"
            value={form.addressDetail}
            onChange={(e) => update({ addressDetail: e.target.value })}
          />
        </div>

        <label className="owner-store__field">
          <span className="owner-store__label">
            가게 소개 <small>선택</small>
          </span>
          <textarea
            className="owner-store__textarea"
            value={form.intro}
            onChange={(e) => update({ intro: e.target.value })}
          />
        </label>

        <div className="owner-store__field">
          <span className="owner-store__label">
            사업자 정보 <small>인증된 정보라 바꿀 수 없어요</small>
          </span>
          <div className="owner-store__locked">
            <span>{businessInfoText(me)}</span>
            <span aria-hidden="true">🔒</span>
          </div>
          <p className="owner-store__note">
            대표자나 사업자번호가 바뀌었으면 골목인턴 운영진에게 알려 주세요. 다시 인증한 뒤에 바꿀 수
            있어요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default OwnerStoreEditPage;
