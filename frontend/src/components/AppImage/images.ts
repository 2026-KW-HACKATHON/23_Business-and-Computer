import logoGakkum from "../../assets/brand/logo-gakkum.svg";
import appIcon from "../../assets/brand/app-icon.png";
import tagline from "../../assets/brand/tagline.png";
import splashTagline from "../../assets/brand/splash-tagline.png";
import characterOwner from "../../assets/characters/owner.svg";
import characterStudent from "../../assets/characters/student.svg";
import characterBadgeOwner from "../../assets/characters/badge-owner.svg";
import characterBadgeStudent from "../../assets/characters/badge-student.svg";
import splashOwner from "../../assets/characters/splash-owner.webp";
import splashStudent from "../../assets/characters/splash-student.webp";
import onboarding1 from "../../assets/illustrations/onboarding-1.webp";
import onboarding2 from "../../assets/illustrations/onboarding-2.webp";
import onboarding3 from "../../assets/illustrations/onboarding-3.webp";
import doneOwner from "../../assets/illustrations/done-owner.webp";
import doneStudent from "../../assets/illustrations/done-student.webp";
import doneOwnerThumbsUp from "../../assets/illustrations/done-owner-thumbs-up.webp";
import doneStudentV from "../../assets/illustrations/done-student-v.webp";
import doneOwnerV from "../../assets/illustrations/done-owner-v.webp";
import paymentFailOwner from "../../assets/illustrations/payment-fail-owner.webp";
import warningOwner from "../../assets/illustrations/warning-owner.webp";
import warningStudent from "../../assets/illustrations/warning-student.webp";
import sorryOwner from "../../assets/illustrations/sorry-owner.webp";
import splashPaperProposal from "../../assets/illustrations/splash-paper-proposal.webp";
import splashPaperRequest from "../../assets/illustrations/splash-paper-request.webp";
import logoKakao from "../../assets/logos/kakao.svg";
import logoNotion from "../../assets/logos/notion.webp";
import logoKwangwoon from "../../assets/logos/kwangwoon.webp";
import roleOwner from "../../assets/icons/role-owner.webp";
import roleStudent from "../../assets/icons/role-student.webp";
import icon3dCraft from "../../assets/icons/3d-craft.webp";
import icon3dPhoto from "../../assets/icons/3d-photo.webp";
import icon3dElectronics from "../../assets/icons/3d-electronics.webp";
import icon3dBooks from "../../assets/icons/3d-books.webp";
import icon3dBank from "../../assets/icons/3d-bank.webp";
import icon3dHardware from "../../assets/icons/3d-hardware.webp";
import iconFieldAll from "../../assets/icons/field-all.webp";
import iconCardProposal from "../../assets/icons/card-proposal.webp";
import iconCardRequest from "../../assets/icons/card-request.webp";
import iconHeart from "../../assets/icons/heart.webp";
import iconLink from "../../assets/icons/link.webp";
import iconHeartEmpty from "../../assets/icons/heart-empty-16.svg";
import iconLightbulb from "../../assets/icons/lightbulb-24.svg";
import iconBell from "../../assets/icons/bell.svg";
import iconMy from "../../assets/icons/my.svg";
import iconTabHome from "../../assets/icons/tab-home.svg";
import iconTabSearch from "../../assets/icons/tab-search.svg";
import iconTabRequest from "../../assets/icons/tab-request.svg";
import iconTabChat from "../../assets/icons/tab-chat.svg";
import iconChevronRight14 from "../../assets/icons/chevron-right-14.svg";
import iconChevronRight20 from "../../assets/icons/chevron-right-20.svg";
import iconCheck11 from "../../assets/icons/check-11.svg";
import iconCheckCircle18 from "../../assets/icons/check-circle-18.svg";
import iconCheckSuccess14 from "../../assets/icons/check-success-14.svg";
import iconLock16 from "../../assets/icons/lock-16.svg";
import iconLocation12 from "../../assets/icons/location-12.svg";
import iconPlus13 from "../../assets/icons/plus-13.svg";

export interface ImageInfo {
  src: string;
  /** 피그마에서 보이는 크기(px). 래스터 파일은 선명도를 위해 2배로 저장했다. */
  width: number;
  height: number;
  alt: string;
}

/** 피그마 「0. 스타일 가이드」 › 공유 컴포넌트 › 일러스트 · 이미지 와 같은 이름을 쓴다. */
export const IMAGES = {
  // 브랜드
  logoGakkum: { src: logoGakkum, width: 180, height: 87, alt: "가꿈" },
  appIcon: { src: appIcon, width: 96, height: 96, alt: "가꿈 앱 아이콘" },
  tagline: { src: tagline, width: 118, height: 38, alt: "학생과 사장님이 함께 가게를 꿈꾼다." },
  splashTagline: { src: splashTagline, width: 180, height: 51, alt: "학생과 사장님이 함께 가게를 꿈꾼다." },

  // 캐릭터
  characterOwner: { src: characterOwner, width: 160, height: 160, alt: "사장님 캐릭터" },
  characterStudent: { src: characterStudent, width: 160, height: 160, alt: "학생 캐릭터" },
  characterBadgeOwner: { src: characterBadgeOwner, width: 72, height: 72, alt: "" },
  characterBadgeStudent: { src: characterBadgeStudent, width: 72, height: 72, alt: "" },
  splashOwner: { src: splashOwner, width: 156, height: 156, alt: "" },
  splashStudent: { src: splashStudent, width: 156, height: 156, alt: "" },

  // 일러스트
  onboarding1: { src: onboarding1, width: 318, height: 212, alt: "" },
  onboarding2: { src: onboarding2, width: 290, height: 290, alt: "" },
  onboarding3: { src: onboarding3, width: 290, height: 193, alt: "" },
  doneOwner: { src: doneOwner, width: 120, height: 120, alt: "" },
  doneStudent: { src: doneStudent, width: 120, height: 120, alt: "" },
  doneOwnerThumbsUp: { src: doneOwnerThumbsUp, width: 120, height: 120, alt: "" },
  doneStudentV: { src: doneStudentV, width: 120, height: 120, alt: "" },
  doneOwnerV: { src: doneOwnerV, width: 56, height: 72, alt: "" },
  paymentFailOwner: { src: paymentFailOwner, width: 96, height: 101, alt: "" },
  warningOwner: { src: warningOwner, width: 96, height: 104, alt: "" },
  warningStudent: { src: warningStudent, width: 96, height: 104, alt: "" },
  sorryOwner: { src: sorryOwner, width: 96, height: 103, alt: "" },
  splashPaperProposal: { src: splashPaperProposal, width: 40, height: 12, alt: "제안" },
  splashPaperRequest: { src: splashPaperRequest, width: 40, height: 12, alt: "의뢰" },

  // 로고
  logoKakao: { src: logoKakao, width: 20, height: 18.462, alt: "" },
  logoNotion: { src: logoNotion, width: 18, height: 18, alt: "" },
  logoKwangwoon: { src: logoKwangwoon, width: 24, height: 24, alt: "" },

  // 아이콘 (3D·이미지)
  roleOwner: { src: roleOwner, width: 48, height: 48, alt: "사장님" },
  roleStudent: { src: roleStudent, width: 48, height: 48, alt: "학생" },
  icon3dCraft: { src: icon3dCraft, width: 36, height: 36, alt: "" },
  icon3dPhoto: { src: icon3dPhoto, width: 36, height: 36, alt: "" },
  icon3dElectronics: { src: icon3dElectronics, width: 36, height: 36, alt: "" },
  icon3dBooks: { src: icon3dBooks, width: 36, height: 36, alt: "" },
  icon3dBank: { src: icon3dBank, width: 36, height: 36, alt: "" },
  icon3dHardware: { src: icon3dHardware, width: 36, height: 36, alt: "" },
  iconFieldAll: { src: iconFieldAll, width: 36, height: 36, alt: "" },
  iconCardProposal: { src: iconCardProposal, width: 22, height: 22, alt: "제안" },
  iconCardRequest: { src: iconCardRequest, width: 22, height: 22, alt: "의뢰" },
  iconHeart: { src: iconHeart, width: 16, height: 16, alt: "" },
  iconLink: { src: iconLink, width: 12, height: 12, alt: "" },

  // 아이콘 (선)
  iconHeartEmpty: { src: iconHeartEmpty, width: 16, height: 16, alt: "" },
  iconLightbulb: { src: iconLightbulb, width: 24, height: 24, alt: "" },
  iconBell: { src: iconBell, width: 32, height: 32, alt: "알림" },
  iconMy: { src: iconMy, width: 32, height: 32, alt: "내 정보" },
  iconTabHome: { src: iconTabHome, width: 24, height: 24, alt: "" },
  iconTabSearch: { src: iconTabSearch, width: 24, height: 24, alt: "" },
  iconTabRequest: { src: iconTabRequest, width: 24, height: 24, alt: "" },
  iconTabChat: { src: iconTabChat, width: 24, height: 24, alt: "" },
  iconChevronRight14: { src: iconChevronRight14, width: 14, height: 14, alt: "" },
  iconChevronRight20: { src: iconChevronRight20, width: 20, height: 20, alt: "" },
  iconCheck11: { src: iconCheck11, width: 11, height: 11, alt: "" },
  iconCheckCircle18: { src: iconCheckCircle18, width: 18, height: 18, alt: "" },
  iconCheckSuccess14: { src: iconCheckSuccess14, width: 14, height: 14, alt: "" },
  iconLock16: { src: iconLock16, width: 16, height: 16, alt: "" },
  iconLocation12: { src: iconLocation12, width: 12, height: 12, alt: "" },
  iconPlus13: { src: iconPlus13, width: 13, height: 13, alt: "" },
} satisfies Record<string, ImageInfo>;

export type ImageName = keyof typeof IMAGES;

/** 다음 화면에서 쓸 이미지를 미리 받아 둔다 (예: 온보딩 2·3단계). */
export function preloadImages(names: ImageName[]): void {
  names.forEach((name) => {
    const img = new Image();
    img.src = IMAGES[name].src;
  });
}
