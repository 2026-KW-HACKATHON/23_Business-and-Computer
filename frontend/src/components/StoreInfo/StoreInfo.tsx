import { ownerTitle } from "../../lib/korean";
import AppImage from "../AppImage/AppImage";
import "./StoreInfo.css";

interface StoreInfoProps {
  storeName: string;
  /** 「사장님」은 붙여서 보여 준다 (이미 「사장님」으로 끝나면 그대로) */
  ownerName: string;
  /** 줄바꿈(\n)은 그대로 보인다 */
  address: string;
}

/** 사장님 프로필 머리의 가게 정보. 상호명 / 사장님 이름 / 주소 */
function StoreInfo({ storeName, ownerName, address }: StoreInfoProps) {
  return (
    <div className="store-info">
      <strong className="store-info__name">{storeName}</strong>
      <span className="store-info__owner">{ownerTitle(ownerName)}</span>
      <span className="store-info__address">
        <AppImage name="iconLocation12" className="store-info__pin" />
        {address}
      </span>
    </div>
  );
}

export default StoreInfo;
