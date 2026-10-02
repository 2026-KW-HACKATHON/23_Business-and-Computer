import type { ReactNode } from "react";
import AppImage from "../AppImage/AppImage";
import type { ImageName } from "../AppImage/images";
import "./DoneScreen.css";

interface DoneScreenProps {
  image: ImageName;
  title: string;
  /** 줄바꿈(\n)은 그대로 보인다 */
  description: string;
  /** 설명 아래 더 넣을 내용 (예: 남긴 별점) */
  children?: ReactNode;
  /** 아래 고정 버튼 */
  action: ReactNode;
}

/** 전체 화면 완료 안내 (의뢰 등록 완료 · 후기 완료). 일러스트 · 제목 · 설명이 가운데 */
function DoneScreen({ image, title, description, children, action }: DoneScreenProps) {
  return (
    <div className="done-screen">
      <main className="done-screen__body">
        <AppImage name={image} priority />
        <h1 className="done-screen__title">{title}</h1>
        <p className="done-screen__description">{description}</p>
        {children}
      </main>
      <footer className="done-screen__footer">{action}</footer>
    </div>
  );
}

export default DoneScreen;
