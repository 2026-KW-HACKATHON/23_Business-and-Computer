import { useEffect, useMemo } from "react";

/** 고른 파일의 미리보기 주소를 만들고, 파일이 바뀌거나 화면을 떠나면 해제한다 */
export function useObjectUrls(files: File[]): string[] {
  const urls = useMemo(() => files.map((file) => URL.createObjectURL(file)), [files]);

  useEffect(() => {
    return () => urls.forEach((url) => URL.revokeObjectURL(url));
  }, [urls]);

  return urls;
}
