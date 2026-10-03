import AppImage from "../AppImage/AppImage";
import "./ProfilePhoto.css";

interface ProfilePhotoProps {
  /** 고른 사진의 미리보기 주소. 없으면 회색 원 */
  src?: string;
  onSelect: (file: File) => void;
  /** large = 132px (가입), medium = 88px (가게 정보 수정), small = 72px (내 정보) */
  size?: "large" | "medium" | "small";
}

/**
 * 내 프로필 사진 (사장님·학생 공통). 동그라미 어디를 눌러도 사진을 고르고,
 * 오른쪽 아래 +는 아직 사진이 없을 때만 보인다.
 */
function ProfilePhoto({ src, onSelect, size = "large" }: ProfilePhotoProps) {
  return (
    <label className={`profile-photo profile-photo--${size}`} aria-label={src ? "프로필 사진 바꾸기" : "프로필 사진 추가"}>
      {src ? (
        <img className="profile-photo__image" src={src} alt="" />
      ) : (
        <>
          <span className="profile-photo__empty" />
          <span className="profile-photo__add" aria-hidden="true">
            <AppImage name="iconPlus13" />
          </span>
        </>
      )}
      <input
        className="profile-photo__input"
        type="file"
        accept="image/*"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onSelect(file);
          e.target.value = "";
        }}
      />
    </label>
  );
}

export default ProfilePhoto;
