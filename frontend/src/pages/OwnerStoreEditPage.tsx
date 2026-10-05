import { useMemo, useState } from "react";
import { Button, Chip, ProfilePhoto, SubScreen, TextField } from "../components";
import {
  OWNER_PATHS,
  saveOwnerStore,
  setOwnerStorePhoto,
  useOwnerStore,
  useOwnerStorePhoto,
} from "../features/owner";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { STORE_CATEGORIES } from "../types/storeCategory";
import "./OwnerStoreEditPage.css";

/**
 * 피그마 「가게 정보 수정」. 업종은 가입 1/3과 같은 칩 11개 중 하나.
 * 대표자 · 사업자번호는 인증된 값이라 잠겨 있다.
 */
function OwnerStoreEditPage() {
  const back = useBack(OWNER_PATHS.me);
  const store = useOwnerStore();
  const [form, setForm] = useState(store);
  const savedPhoto = useOwnerStorePhoto();
  const [photo, setPhoto] = useState(savedPhoto);
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);

  const update = (patch: Partial<typeof form>) => setForm((f) => ({ ...f, ...patch }));
  // 백엔드 연동 전: 내 정보에만 반영된다 (새로고침하면 처음으로)
  const save = () => {
    saveOwnerStore(form);
    if (photo !== savedPhoto) setOwnerStorePhoto(photo);
    back();
  };

  const filled =
    form.storeName.trim() !== "" && form.address.trim() !== "" && form.phone.trim() !== "";

  return (
    <SubScreen
      title="가게 정보 수정"
      onBack={back}
      footer={
        <Button fullWidth disabled={!filled} onClick={save}>
          저장하기
        </Button>
      }
    >
      <div className="owner-store">
        <div className="owner-store__photo">
          <ProfilePhoto size="medium" src={photoUrl} onSelect={setPhoto} />
          <span>가게 사진 바꾸기</span>
        </div>

        <label className="owner-store__field">
          <span className="owner-store__label">상호명</span>
          <TextField
            value={form.storeName}
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
            value={form.addressDetail}
            onChange={(e) => update({ addressDetail: e.target.value })}
          />
        </div>

        <label className="owner-store__field">
          <span className="owner-store__label">가게 전화번호</span>
          <TextField
            inputMode="tel"
            value={form.phone}
            onChange={(e) => update({ phone: e.target.value })}
          />
        </label>

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
            <span>
              대표자 {form.representative} · 사업자번호 {form.businessNumber}
            </span>
            <span aria-hidden="true">🔒</span>
          </div>
          <p className="owner-store__note">
            대표자나 사업자번호가 바뀌었으면 가꿈 운영진에게 알려 주세요. 다시 인증한 뒤에 바꿀 수
            있어요.
          </p>
        </div>
      </div>
    </SubScreen>
  );
}

export default OwnerStoreEditPage;
