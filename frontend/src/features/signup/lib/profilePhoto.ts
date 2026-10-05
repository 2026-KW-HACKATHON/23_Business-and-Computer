/** 백엔드 이미지 업로드(MediaService)가 받는 형식과 확장자 짝 */
export const PROFILE_PHOTO_EXTENSIONS: Record<string, string> = {
  "image/jpeg": "jpg",
  "image/png": "png",
  "image/webp": "webp",
};

/** 파일 고르기 창에 넘기는 accept 값 */
export const PROFILE_PHOTO_ACCEPT = Object.keys(PROFILE_PHOTO_EXTENSIONS).join(",");

/** 백엔드 media-image.max-size (10MB) */
export const MAX_PROFILE_PHOTO_BYTES = 10 * 1024 * 1024;

/** 고른 사진을 올릴 수 있는지. accept 는 우회될 수 있어 형식도 다시 본다 */
export function checkProfilePhoto(file: File): "ok" | "type" | "size" {
  if (!(file.type in PROFILE_PHOTO_EXTENSIONS)) return "type";
  if (file.size > MAX_PROFILE_PHOTO_BYTES) return "size";
  return "ok";
}
