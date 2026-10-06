import { useRef } from "react";
import type { ChangeEvent } from "react";
import type { WorkFile } from "../types";
import "./FilePicker.css";

interface FilePickerProps {
  files: WorkFile[];
  onChange: (files: WorkFile[]) => void;
  /** 버튼 아래 회색 안내 (예: PDF·이미지·원본 파일 · 최대 50MB) */
  hint: string;
  label?: string;
  /** 파일 고르기 창에서 받을 확장자 (예: .pdf,.png) */
  accept?: string;
}

/** 1.8MB · 240KB */
function sizeText(bytes: number): string {
  return bytes >= 1024 * 1024
    ? `${(bytes / 1024 / 1024).toFixed(1)}MB`
    : `${Math.max(1, Math.round(bytes / 1024))}KB`;
}

/**
 * 결과물 파일 올리기 (초안 제출 · 수정안 제출). 고른 파일의 이름 · 크기를 줄로 보여 주고
 * ✕ 로 뺀다. 고른 파일은 file 에 담아 넘기고, 올리기는 제출할 때 화면이 한다.
 */
function FilePicker({ files, onChange, hint, label = "+ 파일 올리기", accept }: FilePickerProps) {
  const input = useRef<HTMLInputElement>(null);

  const add = (e: ChangeEvent<HTMLInputElement>) => {
    const picked = [...(e.target.files ?? [])].map((f) => ({ name: f.name, size: sizeText(f.size), file: f }));
    onChange([...files, ...picked]);
    e.target.value = "";
  };

  return (
    <div className="file-picker">
      <input ref={input} type="file" multiple hidden accept={accept} onChange={add} />
      <button type="button" className="file-picker__add" onClick={() => input.current?.click()}>
        <strong>{label}</strong>
        <span>{hint}</span>
      </button>
      {files.map((file, i) => (
        <div key={`${file.name}-${i}`} className="file-picker__file">
          <span aria-hidden="true">📄</span>
          <strong>{file.name}</strong>
          <small>{file.size}</small>
          <button
            type="button"
            aria-label={`${file.name} 빼기`}
            onClick={() => onChange(files.filter((_, j) => j !== i))}
          >
            ✕
          </button>
        </div>
      ))}
    </div>
  );
}

export default FilePicker;
