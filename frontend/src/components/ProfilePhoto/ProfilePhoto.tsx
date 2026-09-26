import { useId } from "react";
import AppImage from "../AppImage/AppImage";
import "./ProfilePhoto.css";

interface ProfilePhotoProps {
  /** 고른 사진의 미리보기 주소. 없으면 회색 원 */
  src?: string;
  onSelect: (file: File) => void;
}

function ProfilePhoto({ src, onSelect }: ProfilePhotoProps) {
  const inputId = useId();

  return (
    <div className="profile-photo">
      {src ? (
        <img className="profile-photo__image" src={src} alt="내 프로필 사진" />
      ) : (
        <div className="profile-photo__empty" />
      )}
      <label htmlFor={inputId} className="profile-photo__add" aria-label="프로필 사진 추가">
        <AppImage name="iconPlus13" />
      </label>
      <input
        id={inputId}
        className="profile-photo__input"
        type="file"
        accept="image/*"
        onChange={(e) => {
          const file = e.target.files?.[0];
          if (file) onSelect(file);
          e.target.value = "";
        }}
      />
    </div>
  );
}

export default ProfilePhoto;
