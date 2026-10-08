import { useEffect, useMemo, useRef, useState } from "react";
import type { FocusEvent } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { Button, Chip, LoadNotice, ProfilePhoto, SubScreen, TextField } from "../components";
import { landingPath } from "../features/auth";
import {
  EMPTY_CERTIFICATE,
  PROFILE_PHOTO_ACCEPT,
  certificateErrorText,
  certificateStatuses,
  checkProfilePhoto,
} from "../features/signup";
import type { Certificate } from "../features/signup";
import { implicitSpecialty, selectableCategories, useSpecialties } from "../features/specialty";
import type { SpecialtyCategory } from "../features/specialty";
import {
  STUDENT_PATHS,
  portfolioLabel,
  saveStudentMe,
  specialtyIdsOf,
  studentYearText,
  useStudentMe,
} from "../features/student";
import type { ProfileEditSection, StudentMe } from "../features/student";
import { useBack } from "../hooks/useBack";
import { useObjectUrls } from "../hooks/useObjectUrls";
import { studentTitle } from "../lib/korean";
import { MAX_SPECIALTY_BADGES } from "../types/specialty";
import "./StudentProfileEditPage.css";

const PHOTO_CHECK_TEXT = {
  type: "JPG, PNG, WEBP 사진만 올릴 수 있어요",
  size: "10MB 이하 사진만 올릴 수 있어요",
};

/**
 * 피그마 「프로필 편집(학생)」. 프로필 수정의 「기본 정보 수정」 · 각 「수정」에서 열린다.
 * 지금 값은 GET /students/me, 특기 칩은 GET /specialties (가입 3/3과 같은 칩), 「저장하기」는
 * PUT /students/me (ADR 0041). 이름 · 학교 · 학번은 인증 정보라 보여 주기만 한다.
 */
function StudentProfileEditPage() {
  const back = useBack(STUDENT_PATHS.profile);
  const location = useLocation();
  const section = (location.state as { section?: ProfileEditSection } | null)?.section;
  const { load: meLoad, reload: reloadMe } = useStudentMe();
  const { load: specialtyLoad, reload: reloadSpecialties } = useSpecialties();

  if (meLoad.status === "loaded" && specialtyLoad.status === "loaded") {
    return (
      <ProfileForm me={meLoad.data} categories={specialtyLoad.categories} section={section} onBack={back} />
    );
  }
  const failed = meLoad.status === "error" || specialtyLoad.status === "error";
  return (
    <SubScreen title="프로필 편집" onBack={back}>
      <LoadNotice
        layout="page"
        status={failed ? "error" : "loading"}
        loadingText="프로필을 불러오는 중이에요"
        errorText="프로필을 불러오지 못했어요"
        onRetry={() => {
          if (meLoad.status === "error") reloadMe();
          if (specialtyLoad.status === "error") reloadSpecialties();
        }}
      />
    </SubScreen>
  );
}

/**
 * 불러온 값으로 채운 입력 칸. 새 사진은 「저장하기」를 누를 때 올린다.
 * 보내는 동안 「저장하는 중...」, 성공하면 프로필 수정으로 돌아간다.
 */
function ProfileForm({
  me,
  categories,
  section,
  onBack,
}: {
  me: StudentMe;
  categories: SpecialtyCategory[];
  section?: ProfileEditSection;
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const [photo, setPhoto] = useState<File>();
  const photoFiles = useMemo(() => (photo ? [photo] : []), [photo]);
  const [photoUrl] = useObjectUrls(photoFiles);
  const [photoError, setPhotoError] = useState<string>();
  const [intro, setIntro] = useState(me.introduction ?? "");
  const [specialtyIds, setSpecialtyIds] = useState(() => specialtyIdsOf(me));
  const [limitReached, setLimitReached] = useState(false);
  const [certificates, setCertificates] = useState<Certificate[]>(() =>
    me.certificates.map((c) => ({ name: c.certificateName, acquiredYear: String(c.acquiredYear) })),
  );
  const [touchedCertificates, setTouchedCertificates] = useState<number[]>([]);
  const [portfolioUrl, setPortfolioUrl] = useState(() => portfolioLabel(me.portfolioUrl ?? ""));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string>();
  // 빠른 두 번 누름에도 한 번만 보낸다
  const inFlight = useRef(false);
  const badgesRef = useRef<HTMLElement>(null);
  const certificatesRef = useRef<HTMLElement>(null);

  // 가입 3/3과 같은 칩. 분류를 고르면 정해지는 「기타」 같은 특기는 고를 칩이 없다
  const chipCategories = selectableCategories(categories).filter((category) => !implicitSpecialty(category));

  // 「전공역량 수정」 · 「자격증·포트폴리오 수정」으로 들어오면 그 칸부터 보여 준다
  useEffect(() => {
    const target = section === "badges" ? badgesRef : section === "certificates" ? certificatesRef : null;
    target?.current?.scrollIntoView({ block: "start" });
  }, [section]);

  const pickPhoto = (file: File) => {
    const check = checkProfilePhoto(file);
    if (check !== "ok") {
      setPhotoError(PHOTO_CHECK_TEXT[check]);
      return;
    }
    setPhotoError(undefined);
    setPhoto(file);
  };

  const toggleSpecialty = (id: number) => {
    if (specialtyIds.includes(id)) {
      setSpecialtyIds(specialtyIds.filter((s) => s !== id));
      setLimitReached(false);
      return;
    }
    // 이미 5개면 고르지 않고 안내 문구로 알린다
    if (specialtyIds.length >= MAX_SPECIALTY_BADGES) {
      setLimitReached(true);
      return;
    }
    setSpecialtyIds([...specialtyIds, id]);
  };

  // 가입과 같은 규칙: 비운 줄은 저장하지 않고, 덜 채웠거나 연도가 틀리거나 겹친 줄은 저장을 막는다
  const certificateStatusList = certificateStatuses(certificates);
  const canSave =
    specialtyIds.length > 0 && certificateStatusList.every((s) => s === "empty" || s === "complete");

  const editCertificate = (index: number, patch: Partial<Certificate>) => {
    const next = certificates.map((row, i) => (i === index ? { ...row, ...patch } : row));
    // 오류를 보여준 줄도 고쳐서 맞으면 바로 지운다. 다시 틀리면 줄을 벗어날 때 보여준다
    const status = certificateStatuses(next)[index];
    if (status === "empty" || status === "complete") {
      setTouchedCertificates((rows) => rows.filter((row) => row !== index));
    }
    setCertificates(next);
  };

  // 같은 줄 안에서 칸을 옮길 때는 두고, 줄 밖으로 나갈 때 오류를 보여준다
  const leaveCertificate = (index: number) => (e: FocusEvent<HTMLDivElement>) => {
    if (e.currentTarget.contains(e.relatedTarget as Node | null)) return;
    setTouchedCertificates((rows) => (rows.includes(index) ? rows : [...rows, index]));
  };

  const save = async () => {
    if (!canSave || inFlight.current) return;
    inFlight.current = true;
    setSaving(true);
    setSaveError(undefined);
    const result = await saveStudentMe(
      {
        introduction: intro,
        specialtyIds,
        certificates: certificates
          .filter((_, i) => certificateStatusList[i] === "complete")
          .map((c) => ({ certificateName: c.name, acquiredYear: Number(c.acquiredYear) })),
        portfolioUrl,
        profileImageUrl: me.profileImageUrl ?? "",
      },
      photo,
    );
    inFlight.current = false;
    setSaving(false);
    switch (result.status) {
      case "saved":
        return onBack();
      case "unauthorized":
        return navigate("/login", { replace: true });
      case "forbidden":
        window.alert("학생만 프로필을 바꿀 수 있어요");
        return navigate(landingPath(), { replace: true });
      case "photoFailed":
        return setSaveError("사진을 올리지 못했어요. 다시 시도해 주세요");
      case "invalidSpecialty":
        return setSaveError("고른 특기를 다시 확인해 주세요");
      case "invalidInput":
        return setSaveError("입력한 내용을 다시 확인해 주세요");
      default:
        return setSaveError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title="프로필 편집"
      onBack={onBack}
      footer={
        <>
          {saveError && (
            <p className="student-profile-edit__send-error" role="alert">
              {saveError}
            </p>
          )}
          <Button fullWidth tone="student" disabled={!canSave || saving} onClick={() => void save()}>
            {saving ? "저장하는 중..." : "저장하기"}
          </Button>
        </>
      }
    >
      <div className="student-profile-edit">
        <header className="student-profile-edit__head">
          <ProfilePhoto
            size="medium"
            src={photoUrl || me.profileImageUrl || undefined}
            accept={PROFILE_PHOTO_ACCEPT}
            onSelect={pickPhoto}
          />
          {photoError && (
            <p className="student-profile-edit__photo-error" role="alert">
              {photoError}
            </p>
          )}
          <h2 className="student-profile-edit__name">{studentTitle(me.name)}</h2>
          <p className="student-profile-edit__school">
            {["광운대", studentYearText(me.studentNumber)].filter(Boolean).join(" ")}
          </p>
        </header>

        <label className="student-profile-edit__field">
          <span className="student-profile-edit__label">한 줄 소개</span>
          <TextField
            placeholder="나를 표현하는 한 줄 소개"
            value={intro}
            onChange={(e) => setIntro(e.target.value)}
          />
        </label>

        <section ref={badgesRef} className="student-profile-edit__section" aria-labelledby="edit-badges">
          <h3 id="edit-badges" className="student-profile-edit__title">
            전공역량·특기{" "}
            <span aria-live="polite">
              {specialtyIds.length}/{MAX_SPECIALTY_BADGES}
            </span>
          </h3>
          <p
            className={`student-profile-edit__guide${limitReached || specialtyIds.length === 0 ? " student-profile-edit__guide--alert" : ""}`}
            role={limitReached ? "alert" : undefined}
          >
            {limitReached
              ? "최대 5개까지 고를 수 있어요"
              : specialtyIds.length === 0
                ? "뱃지를 1개 이상 골라 주세요"
                : "해당하는 뱃지를 눌러 골라 주세요 (최대 5개)"}
          </p>
          <div className="student-profile-edit__groups">
            {chipCategories.map((category) => (
              <div key={category.id} className="student-profile-edit__group">
                <h4 className="student-profile-edit__group-title">{category.name}</h4>
                <div className="student-profile-edit__chips">
                  {category.specialties.map((specialty) => (
                    <Chip
                      key={specialty.id}
                      variant="outlined"
                      label={specialty.name}
                      selected={specialtyIds.includes(specialty.id)}
                      onClick={() => toggleSpecialty(specialty.id)}
                    />
                  ))}
                </div>
              </div>
            ))}
          </div>
        </section>

        <section
          ref={certificatesRef}
          className="student-profile-edit__section"
          aria-labelledby="edit-certificates"
        >
          <div>
            <h3 id="edit-certificates" className="student-profile-edit__title">
              보유 자격증
            </h3>
            <p className="student-profile-edit__guide">자격증 이름과 취득 연도를 적어 주세요</p>
          </div>
          {certificates.map((certificate, i) => {
            const errorText = touchedCertificates.includes(i)
              ? certificateErrorText(certificateStatusList[i])
              : null;
            const inputClass = `student-profile-edit__cert-input${errorText ? " student-profile-edit__cert-input--invalid" : ""}`;
            return (
              <div key={i} className="student-profile-edit__cert" onBlur={leaveCertificate(i)}>
                <div className="student-profile-edit__cert-row">
                  <input
                    className={inputClass}
                    placeholder="자격증명"
                    aria-label={`자격증 ${i + 1} 이름`}
                    aria-invalid={errorText ? true : undefined}
                    maxLength={255}
                    value={certificate.name}
                    onChange={(e) => editCertificate(i, { name: e.target.value })}
                  />
                  <input
                    className={`${inputClass} student-profile-edit__cert-input--year`}
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
                {errorText && <p className="student-profile-edit__cert-error">{errorText}</p>}
              </div>
            );
          })}
          <button
            type="button"
            className="student-profile-edit__cert-add"
            onClick={() => setCertificates((rows) => [...rows, EMPTY_CERTIFICATE])}
          >
            + 자격증 추가
          </button>
        </section>

        <label className="student-profile-edit__field">
          <span className="student-profile-edit__title">외부 포트폴리오</span>
          <TextField
            type="url"
            inputMode="url"
            placeholder="링크를 입력해 주세요"
            maxLength={255}
            value={portfolioUrl}
            onChange={(e) => setPortfolioUrl(e.target.value)}
          />
        </label>
      </div>
    </SubScreen>
  );
}

export default StudentProfileEditPage;
