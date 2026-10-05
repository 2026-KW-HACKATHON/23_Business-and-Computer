import { useEffect, useMemo, useRef, useState } from "react";
import type { FocusEvent } from "react";
import { Navigate, useNavigate } from "react-router-dom";
import { AppBar, AppImage, Button, Chip, ProfilePhoto, StepIndicator, TextField } from "../components";
import { landingPath } from "../features/auth";
import {
  EMPTY_CERTIFICATE,
  PROFILE_PHOTO_ACCEPT,
  certificateStatuses,
  checkProfilePhoto,
  registerStudentSignup,
  uploadSignupPhoto,
  useStudentSignup,
} from "../features/signup";
import type {
  Certificate,
  CertificateStatus,
  StudentVerifyReturnState,
} from "../features/signup";
import { implicitSpecialty, selectableCategories, useSpecialties } from "../features/specialty";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { MAX_SPECIALTY_BADGES } from "../types/specialty";
import "./SignupPage.css";
import "./StudentSignupProfilePage.css";

type PhotoError = "type" | "size" | null;
type SubmitError = "photo" | "invalidInput" | "dataConflict" | "retry" | null;

const PHOTO_ERROR_TEXT: Record<Exclude<PhotoError, null>, string> = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

const SUBMIT_ERROR_TEXT: Record<Exclude<SubmitError, null>, string> = {
  photo: "사진을 올리지 못했어요. 다시 시도해 주세요",
  invalidInput: "입력한 내용을 다시 확인해 주세요",
  dataConflict: "일시적인 문제가 생겼어요. 다시 시도해도 안 되면 문의해 주세요",
  retry: "잠시 후 다시 시도해 주세요",
};

function certificateErrorText(status: CertificateStatus): string | null {
  if (status === "incomplete") return "자격증 이름과 취득 연도를 모두 입력해 주세요";
  if (status === "invalidYear") return `1900~${new Date().getFullYear()} 사이 연도를 입력해 주세요`;
  if (status === "duplicate") return "같은 자격증이 두 번 입력됐어요";
  return null;
}

/** 학번 앞 두 자리로 「24학번」을 만든다 (예: 2024402145 → 24학번) */
function admissionYear(studentNumber: string): string {
  return studentNumber.length >= 4 ? `${studentNumber.slice(2, 4)}학번` : `${studentNumber}학번`;
}

/**
 * 피그마 「회원가입 - 프로필 입력(학생) 3/3」. 본문만 스크롤하고 아래 버튼은 고정.
 * 특기 불러오기·가입 중·서버 오류는 피그마에 없어 안내 문구로만 보여준다 (ADR 0019).
 */
function StudentSignupProfilePage() {
  const navigate = useNavigate();
  const { draft, update } = useStudentSignup();
  const [limitReached, setLimitReached] = useState(false);
  const { load: specialtyLoad, reload: reloadSpecialties } = useSpecialties();
  // 가입 저장이 특기 오류로 실패해 목록을 다시 불러왔을 때
  const [specialtyReset, setSpecialtyReset] = useState(false);
  const [photoError, setPhotoError] = useState<PhotoError>(null);
  // 요청 중 여부와 오류는 이 화면에만 둔다 (사장님 2/3 과 같은 방식)
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<SubmitError>(null);
  // 오류를 보여줄 자격증 줄 번호. 줄을 벗어나면 넣고, 고쳐서 맞으면 뺀다
  const [touchedCertificates, setTouchedCertificates] = useState<number[]>([]);
  // 화면을 떠나면 번호가 바뀌어 늦게 온 응답을 버린다
  const requestId = useRef(0);
  // state 가 다시 그려지기 전에 버튼이 두 번 눌려도 가입 요청은 한 번만 보낸다
  const inFlight = useRef(false);
  // 다시 시도할 때 같은 사진을 또 올리지 않도록 올린 주소를 기억한다
  const uploadedPhoto = useRef<{ file: File; imageUrl: string } | null>(null);
  const profileFiles = useMemo(
    () => (draft.profilePhoto ? [draft.profilePhoto] : []),
    [draft.profilePhoto],
  );
  const [profileUrl] = useObjectUrls(profileFiles);

  useEffect(() => {
    const latest = requestId;
    return () => {
      latest.current += 1;
    };
  }, []);

  // 메일 인증을 마치지 않고 들어오면(새로고침 포함) 역할 선택부터 다시
  if (draft.verifiedEmail === "") return <Navigate to="/signup/role" replace />;

  const toggleSpecialty = (id: number) => {
    setSpecialtyReset(false);
    if (draft.specialtyIds.includes(id)) {
      update({ specialtyIds: draft.specialtyIds.filter((s) => s !== id) });
      setLimitReached(false);
      return;
    }
    // 이미 5개면 고르지 않고 안내 문구로 알린다
    if (draft.specialtyIds.length >= MAX_SPECIALTY_BADGES) {
      setLimitReached(true);
      return;
    }
    update({ specialtyIds: [...draft.specialtyIds, id] });
  };

  const selectPhoto = (file: File) => {
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      setPhotoError(check);
      return;
    }
    setPhotoError(null);
    if (submitError === "photo") setSubmitError(null);
    update({ profilePhoto: file });
  };

  const editCertificate = (index: number, patch: Partial<Certificate>) => {
    const certificates = draft.certificates.map((c, i) => (i === index ? { ...c, ...patch } : c));
    // 오류를 보여준 줄도 고쳐서 맞으면 바로 지운다. 다시 틀리면 줄을 벗어날 때 보여준다
    const status = certificateStatuses(certificates)[index];
    if (status === "empty" || status === "complete") {
      setTouchedCertificates((rows) => rows.filter((row) => row !== index));
    }
    update({ certificates });
  };

  // 같은 줄 안에서 칸을 옮길 때는 두고, 줄 밖으로 나갈 때 오류를 보여준다
  const leaveCertificate = (index: number) => (e: FocusEvent<HTMLDivElement>) => {
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setTouchedCertificates((rows) => (rows.includes(index) ? rows : [...rows, index]));
  };

  // 고를 특기가 없는 분류와, 「기타」처럼 특기가 분류 이름과 같은 1개뿐인 분류는 가입에서 뺀다.
  // 제안 2/4 는 「기타」를 보여주므로 selectableCategories 는 그대로 두고 여기서만 더 거른다 (ADR 0019)
  const specialtyCategories =
    specialtyLoad.status === "loaded"
      ? selectableCategories(specialtyLoad.categories).filter((category) => !implicitSpecialty(category))
      : [];
  // 서버에 특기가 하나도 없으면 고를 수 없어 가입도 막힌다 (특기 1~5개 규칙은 항상 적용, ADR 0019)
  const noSpecialties = specialtyLoad.status === "loaded" && specialtyCategories.length === 0;
  // 비었거나 불러오지 못했을 때는 그 안내만 보이고 개수·고르기 안내는 숨긴다
  const specialtyUnavailable = noSpecialties || specialtyLoad.status === "error";
  const certificateStatusList = certificateStatuses(draft.certificates);
  const inputsValid =
    draft.specialtyIds.length > 0 &&
    draft.specialtyIds.length <= MAX_SPECIALTY_BADGES &&
    certificateStatusList.every((s) => s === "empty" || s === "complete");
  const canSubmit = !submitting && inputsValid;

  const handleComplete = async () => {
    if (inFlight.current) return;
    // 버튼은 비활성이면 눌리지 않지만, 눌렸는데 자격증이 틀렸다면 모든 줄의 오류를 보여준다
    if (!inputsValid) {
      setTouchedCertificates(draft.certificates.map((_, i) => i));
      return;
    }
    inFlight.current = true;
    try {
      await submit();
    } finally {
      inFlight.current = false;
    }
  };

  // 사진이 있으면 먼저 올리고, 받은 주소로 가입을 저장한다
  const submit = async () => {
    const id = ++requestId.current;
    const photo = draft.profilePhoto;
    setSubmitting(true);
    setSubmitError(null);

    let imageUrl = "";
    if (photo) {
      if (uploadedPhoto.current?.file === photo) {
        imageUrl = uploadedPhoto.current.imageUrl;
      } else {
        const upload = await uploadSignupPhoto(photo);
        if (id !== requestId.current) return;
        if (upload.status === "unauthorized") {
          navigate("/login", { replace: true });
          return;
        }
        if (upload.status === "failed") {
          setSubmitting(false);
          setSubmitError("photo");
          return;
        }
        uploadedPhoto.current = { file: photo, imageUrl: upload.imageUrl };
        imageUrl = upload.imageUrl;
      }
    }

    // 성공하면 새 토큰은 응답을 버려도 이미 저장되어 있다
    const result = await registerStudentSignup(draft, imageUrl);
    if (id !== requestId.current) return;
    setSubmitting(false);

    switch (result) {
      case "registered":
        // 뒤로 가기로 돌아오지 않게 교체한다
        update({ completed: true });
        navigate("/signup/student/done", { replace: true });
        break;
      case "studentNumberTaken":
        update({ takenStudentNumber: draft.studentNumber });
        navigate("/signup/student/1", { replace: true });
        break;
      case "reverify":
      case "emailTaken": {
        const state: StudentVerifyReturnState = { notice: result };
        navigate("/signup/student/2", { replace: true, state });
        break;
      }
      case "specialtyInvalid":
        update({ specialtyIds: [] });
        setLimitReached(false);
        setSpecialtyReset(true);
        reloadSpecialties();
        break;
      case "unauthorized":
        navigate("/login", { replace: true });
        break;
      case "alreadyRegistered":
        window.alert("이미 가입을 마친 계정이에요");
        navigate(landingPath(), { replace: true });
        break;
      case "invalidInput":
      case "dataConflict":
        setSubmitError(result);
        break;
      default:
        setSubmitError("retry");
    }
  };

  const guideAlert = limitReached || specialtyReset;

  return (
    <div className="signup">
      <AppBar
        title="프로필 입력"
        onBack={() => navigate("/signup/student/2")}
        muted
        bottom={<StepIndicator total={3} current={3} tone="student" />}
      />

      <main className="signup__body student-signup-profile__body">
        <div className="student-signup-profile__head">
          <ProfilePhoto src={profileUrl} accept={PROFILE_PHOTO_ACCEPT} onSelect={selectPhoto} />
          <div className="student-signup-profile__who">
            <strong className="student-signup-profile__name">{draft.name.trim()} 학생</strong>
            <span className="student-signup-profile__school">
              광운대학교 {draft.department.trim()} {admissionYear(draft.studentNumber)}
            </span>
          </div>
        </div>
        {photoError && (
          <p className="student-signup-profile__error" role="alert">
            {PHOTO_ERROR_TEXT[photoError]}
          </p>
        )}

        <TextField
          className="student-signup-profile__intro"
          placeholder="나를 표현하는 한 줄 소개"
          aria-label="한 줄 소개"
          value={draft.intro}
          onChange={(e) => update({ intro: e.target.value })}
        />

        <h2 className="student-signup-profile__title">전공 역량 및 특기</h2>

        <label className="student-signup-profile__portfolio">
          <span className="student-signup-profile__portfolio-title">외부 포트폴리오 추가</span>
          <span className="student-signup-profile__portfolio-link">
            <AppImage name="iconLink" alt="" />
            <input
              type="url"
              inputMode="url"
              placeholder="링크를 입력해주세요"
              value={draft.portfolioUrl}
              onChange={(e) => update({ portfolioUrl: e.target.value })}
            />
          </span>
        </label>

        <section className="student-signup-profile__panel" aria-labelledby="specialty-title">
          <div className="student-signup-profile__panel-head">
            <h3 id="specialty-title" className="student-signup-profile__panel-title">
              나의 전공역량 및 특기
            </h3>
            {!specialtyUnavailable && (
              <span className="student-signup-profile__count" aria-live="polite">
                {draft.specialtyIds.length}/{MAX_SPECIALTY_BADGES}
              </span>
            )}
          </div>
          {noSpecialties && (
            <p className="student-signup-profile__guide" role="status">
              선택할 특기가 아직 없어요
            </p>
          )}
          {!specialtyUnavailable && (
            <p
              className={`student-signup-profile__guide${guideAlert ? " student-signup-profile__guide--limit" : ""}`}
              role={guideAlert ? "alert" : undefined}
            >
              {limitReached
                ? "최대 5개까지 고를 수 있어요"
                : specialtyReset
                  ? "특기를 다시 골라 주세요"
                  : "해당하는 뱃지를 눌러 골라 주세요 (최대 5개)"}
            </p>
          )}

          {specialtyLoad.status === "loading" && (
            <p className="student-signup-profile__status" role="status">
              특기 목록을 불러오는 중이에요
            </p>
          )}
          {specialtyLoad.status === "error" && (
            <div className="student-signup-profile__status" role="alert">
              <span>특기 목록을 불러오지 못했어요</span>
              <button type="button" className="student-signup-profile__retry" onClick={reloadSpecialties}>
                다시 시도
              </button>
            </div>
          )}
          {specialtyLoad.status === "loaded" && !noSpecialties && (
            <div className="student-signup-profile__groups">
              {specialtyCategories.map((category) => (
                <div key={category.id} className="student-signup-profile__group">
                  <h4 className="student-signup-profile__group-title">{category.name}</h4>
                  <div className="student-signup-profile__chips">
                    {category.specialties.map((specialty) => (
                      <Chip
                        key={specialty.id}
                        variant="outlined"
                        label={specialty.name}
                        selected={draft.specialtyIds.includes(specialty.id)}
                        onClick={() => toggleSpecialty(specialty.id)}
                      />
                    ))}
                  </div>
                </div>
              ))}
            </div>
          )}

          <div className="student-signup-profile__certs">
            <div>
              <h3 className="student-signup-profile__certs-title">보유 자격증 (선택)</h3>
              <p className="student-signup-profile__certs-guide">자격증 이름과 취득 연도를 적어 주세요</p>
            </div>
            {draft.certificates.map((certificate, i) => {
              const errorText = touchedCertificates.includes(i)
                ? certificateErrorText(certificateStatusList[i])
                : null;
              const inputClass = `student-signup-profile__cert-input${errorText ? " student-signup-profile__cert-input--invalid" : ""}`;
              return (
                <div key={i} className="student-signup-profile__cert" onBlur={leaveCertificate(i)}>
                  <div className="student-signup-profile__cert-row">
                    <input
                      className={inputClass}
                      placeholder="자격증명"
                      aria-label={`자격증 ${i + 1} 이름`}
                      aria-invalid={errorText ? true : undefined}
                      value={certificate.name}
                      onChange={(e) => editCertificate(i, { name: e.target.value })}
                    />
                    <input
                      className={`${inputClass} student-signup-profile__cert-input--year`}
                      placeholder="취득 연도"
                      aria-label={`자격증 ${i + 1} 취득 연도`}
                      aria-invalid={errorText ? true : undefined}
                      inputMode="numeric"
                      maxLength={4}
                      value={certificate.acquiredYear}
                      onChange={(e) =>
                        editCertificate(i, { acquiredYear: e.target.value.replace(/\D/g, "").slice(0, 4) })
                      }
                    />
                  </div>
                  {errorText && <p className="student-signup-profile__error">{errorText}</p>}
                </div>
              );
            })}
            <button
              type="button"
              className="student-signup-profile__cert-add"
              onClick={() => update({ certificates: [...draft.certificates, EMPTY_CERTIFICATE] })}
            >
              + 자격증 추가
            </button>
          </div>
        </section>
      </main>

      <footer className="signup__footer student-signup-profile__footer">
        {submitError && (
          <p className="student-signup-profile__error student-signup-profile__submit-error" role="alert">
            {SUBMIT_ERROR_TEXT[submitError]}
          </p>
        )}
        <Button fullWidth tone="student" disabled={!canSubmit} onClick={() => void handleComplete()}>
          {submitting ? "가입 중..." : "회원가입 완료"}
        </Button>
      </footer>
    </div>
  );
}

export default StudentSignupProfilePage;
