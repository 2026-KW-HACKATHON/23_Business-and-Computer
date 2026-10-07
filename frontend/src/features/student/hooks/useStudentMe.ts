import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { useObjectUrls } from "../../../hooks/useObjectUrls";
import { landingPath } from "../../auth";
import { checkProfilePhoto } from "../../signup";
import { loadStudentMe, saveStudentMe, studentMeChanges } from "../lib/studentMe";
import type { StudentMe } from "../lib/studentMe";

export type StudentMeLoad = { status: "loading" } | { status: "error" } | { status: "loaded"; data: StudentMe };

/**
 * 내 정보 (GET /students/me). 내 정보 · 프로필 수정 · 프로필 편집이 쓴다. reload 로 다시 불러온다.
 * 401 은 /login, 403(학생이 아님)은 알림 뒤 landingPath() 로 보낸다. 지난 응답은 버린다.
 */
export function useStudentMe(): { load: StudentMeLoad; reload: () => void } {
  const navigate = useNavigate();
  const [request, setRequest] = useState(0);
  const [result, setResult] = useState<{ request: number; load: StudentMeLoad }>();

  useEffect(() => {
    let active = true;
    void loadStudentMe().then((loaded) => {
      if (!active) return;
      if (loaded.status === "unauthorized") {
        navigate("/login", { replace: true });
      } else if (loaded.status === "forbidden") {
        window.alert("학생만 내 정보를 볼 수 있어요");
        navigate(landingPath(), { replace: true });
      } else {
        setResult({ request, load: loaded });
      }
    });
    return () => {
      active = false;
    };
  }, [request, navigate]);

  const reload = useCallback(() => setRequest((n) => n + 1), []);
  const load: StudentMeLoad = result?.request === request ? result.load : { status: "loading" };
  return { load, reload };
}

const PHOTO_CHECK_TEXT = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

/**
 * 내 정보 · 프로필 수정의 사진 바꾸기. 고르면 바로 올리고(PROFILE) PUT /students/me 로 지금 값과 함께 저장한다.
 * 올리는 동안 고른 사진을 보여 주고, 실패하면 알림 뒤 원래 사진으로 돌아간다.
 */
export function useStudentPhotoChange(me: StudentMe): {
  photoUrl: string | undefined;
  changePhoto: (file: File) => void;
} {
  const navigate = useNavigate();
  const [savedPhotoUrl, setSavedPhotoUrl] = useState(me.profileImageUrl ?? "");
  const [pendingPhoto, setPendingPhoto] = useState<File>();
  const pendingFiles = useMemo(() => (pendingPhoto ? [pendingPhoto] : []), [pendingPhoto]);
  const [pendingUrl] = useObjectUrls(pendingFiles);
  // 올리는 동안 다시 골라도 한 번만 보낸다
  const saving = useRef(false);

  const changePhoto = async (file: File) => {
    if (saving.current) return;
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      window.alert(PHOTO_CHECK_TEXT[check]);
      return;
    }
    saving.current = true;
    setPendingPhoto(file);
    const result = await saveStudentMe({ ...studentMeChanges(me), profileImageUrl: savedPhotoUrl }, file);
    saving.current = false;
    setPendingPhoto(undefined);
    if (result.status === "saved") {
      setSavedPhotoUrl(result.profileImageUrl);
    } else if (result.status === "unauthorized") {
      navigate("/login", { replace: true });
    } else if (result.status === "forbidden") {
      window.alert("학생만 프로필 사진을 바꿀 수 있어요");
      navigate(landingPath(), { replace: true });
    } else if (result.status === "photoFailed") {
      window.alert("사진을 올리지 못했어요. 다시 시도해 주세요");
    } else {
      window.alert("사진을 바꾸지 못했어요. 잠시 후 다시 시도해 주세요");
    }
  };

  return {
    photoUrl: pendingUrl || savedPhotoUrl || undefined,
    changePhoto: (file) => void changePhoto(file),
  };
}
