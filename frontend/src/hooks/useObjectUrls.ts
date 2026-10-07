import { useEffect, useMemo } from "react";

/** 파일마다 미리보기 주소 하나. 쓰는 화면이 하나도 없으면 해제한다 */
const entries = new Map<File, { url: string; users: number }>();

function urlOf(file: File): string {
  let entry = entries.get(file);
  if (!entry) {
    entry = { url: URL.createObjectURL(file), users: 0 };
    entries.set(file, entry);
  }
  return entry.url;
}

/**
 * 고른 파일의 미리보기 주소를 만들고, 파일이 바뀌거나 화면을 떠나면 해제한다.
 * 해제는 한 박자 늦게 한다: 개발 모드(StrictMode)가 화면을 붙였다 떼었다 다시 붙일 때
 * 이미 보여 준 주소를 지워, 나중에 같은 주소로 여는 사진(크게 보기)이 깨지지 않게
 */
export function useObjectUrls(files: File[]): string[] {
  const urls = useMemo(() => files.map(urlOf), [files]);

  useEffect(() => {
    const used = files.map((file) => {
      urlOf(file);
      const entry = entries.get(file)!;
      entry.users += 1;
      return { file, entry };
    });
    return () => {
      used.forEach(({ entry }) => {
        entry.users -= 1;
      });
      window.setTimeout(() => {
        used.forEach(({ file, entry }) => {
          if (entry.users > 0 || entries.get(file) !== entry) return;
          URL.revokeObjectURL(entry.url);
          entries.delete(file);
        });
      }, 0);
    };
  }, [files]);

  return urls;
}
