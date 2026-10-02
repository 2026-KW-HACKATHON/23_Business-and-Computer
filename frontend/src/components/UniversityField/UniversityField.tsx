import AppImage from "../AppImage/AppImage";
import "./UniversityField.css";

interface UniversityFieldProps {
  /** 지금은 광운대생만 가입할 수 있어 광운대학교로 잠겨 있다 */
  name?: string;
}

/** 회원가입 대학교 칸 (잠김). 학교 로고 + 학교 이름 + 자물쇠 */
function UniversityField({ name = "광운대학교" }: UniversityFieldProps) {
  return (
    <div className="university-field" aria-label={`대학교: ${name} (바꿀 수 없어요)`}>
      <AppImage name="logoKwangwoon" className="university-field__logo" />
      <span className="university-field__name">{name}</span>
      <AppImage name="iconLock16" />
    </div>
  );
}

export default UniversityField;
