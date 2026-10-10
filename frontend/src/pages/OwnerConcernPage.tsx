import { useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Button, Chip, FormField, LoadNotice, SubScreen, TextAreaField, TitleField } from "../components";
import { landingPath } from "../features/auth";
import {
  CONCERN_DESCRIPTION_MAX,
  CONCERN_TITLE_MAX,
  OWNER_PATHS,
  concernForm,
  saveConcern,
  useOwnerConcern,
} from "../features/owner";
import type { OwnerConcern, OwnerConcernForm } from "../features/owner";
import { useSpecialties } from "../features/specialty";
import type { SpecialtyCategory } from "../features/specialty";
import { useBack } from "../hooks/useBack";
import "./OwnerConcernPage.css";

/**
 * 가게 고민 올리기 · 고치기 (ADR 0070). 지금 고민은 GET /owners/me/concern, 분야 칩은 GET /specialties 의
 * 대분류, 저장은 PUT /owners/me/concern. 고민이 있으면 그 값으로 채워 「저장하기」, 없으면 빈 칸으로 「올리기」.
 * 고민은 학생 제안의 참고 정보라 제안과 이어지지 않는다. 「해결됐어요」는 내 정보에서 한다.
 */
function OwnerConcernPage() {
  const back = useBack(OWNER_PATHS.me);
  const { load, reload } = useOwnerConcern();
  const { load: specialtyLoad, reload: reloadSpecialties } = useSpecialties();

  if (load.status === "loaded" && specialtyLoad.status === "loaded") {
    return <ConcernForm concern={load.concern} categories={specialtyLoad.categories} onBack={back} />;
  }
  const failed = load.status === "error" || specialtyLoad.status === "error";
  return (
    <SubScreen title="우리 가게 고민" onBack={back}>
      <LoadNotice
        layout="page"
        status={failed ? "error" : "loading"}
        loadingText="가게 고민을 불러오는 중이에요"
        errorText="가게 고민을 불러오지 못했어요"
        onRetry={() => {
          if (load.status === "error") reload();
          if (specialtyLoad.status === "error") reloadSpecialties();
        }}
      />
    </SubScreen>
  );
}

/** 한 줄 고민(필수) · 자세한 설명(선택) · 분야(선택, 하나). 다시 누르면 분야를 뺀다. 저장하면 앞 화면으로 */
function ConcernForm({
  concern,
  categories,
  onBack,
}: {
  concern: OwnerConcern | null;
  categories: SpecialtyCategory[];
  onBack: () => void;
}) {
  const navigate = useNavigate();
  const [form, setForm] = useState<OwnerConcernForm>(() => concernForm(concern));
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string>();
  // 빠른 두 번 누름에도 한 번만 보낸다
  const inFlight = useRef(false);
  const update = (patch: Partial<OwnerConcernForm>) => setForm((f) => ({ ...f, ...patch }));
  const filled = form.title.trim() !== "";

  const save = async () => {
    if (inFlight.current || !filled) return;
    inFlight.current = true;
    setSaving(true);
    setSaveError(undefined);
    const result = await saveConcern(form);
    inFlight.current = false;
    setSaving(false);
    switch (result.status) {
      case "saved":
        return onBack();
      case "unauthorized":
        return navigate("/login", { replace: true });
      case "forbidden":
        window.alert("사장님만 가게 고민을 올릴 수 있어요");
        return navigate(landingPath(), { replace: true });
      case "invalidInput":
        return setSaveError("입력한 내용을 다시 확인해 주세요");
      case "conflict":
        return setSaveError("다른 곳에서 고민을 먼저 올렸어요. 뒤로 갔다가 다시 들어와 주세요");
      default:
        return setSaveError("잠시 후 다시 시도해 주세요");
    }
  };

  return (
    <SubScreen
      title="우리 가게 고민"
      onBack={onBack}
      footer={
        <>
          {saveError && (
            <p className="owner-concern__send-error" role="alert">
              {saveError}
            </p>
          )}
          <Button
            loading={saving}
            loadingLabel={concern ? "저장하는 중" : "올리는 중"}
            fullWidth
            disabled={!filled || saving}
            onClick={() => void save()}
          >
            {concern ? "저장하기" : "올리기"}
          </Button>
        </>
      }
    >
      <div className="owner-concern">
        <div className="owner-concern__intro">
          <h2 className="owner-concern__title">어떤 고민이 있나요?</h2>
          <p className="owner-concern__description">
            학생들이 가게 목록에서 고민을 보고 해결할 방법을 먼저 제안해 줘요
          </p>
        </div>

        <FormField label="한 줄 고민" wrapsInput>
          <TitleField
            value={form.title}
            maxLength={CONCERN_TITLE_MAX}
            placeholder="예: 평일 점심 손님이 적어요"
            onChange={(title) => update({ title })}
          />
        </FormField>

        <FormField label="자세한 설명 (선택)" hint="언제, 어떤 점이 고민인지 적어 주면 제안이 더 정확해져요" wrapsInput>
          <TextAreaField
            value={form.description}
            maxLength={CONCERN_DESCRIPTION_MAX}
            placeholder="예: 주말에는 붐비는데 평일 오후 2시부터 5시까지는 거의 비어 있어요"
            onChange={(description) => update({ description })}
          />
        </FormField>

        <FormField label="분야 (선택)" hint="어떤 도움이 필요한지 하나 골라 주세요">
          <div className="owner-concern__chips" role="group" aria-label="분야">
            {categories.map((category) => (
              <Chip
                key={category.id}
                label={category.name}
                selected={form.categoryId === category.id}
                onClick={() => update({ categoryId: form.categoryId === category.id ? null : category.id })}
              />
            ))}
          </div>
        </FormField>
      </div>
    </SubScreen>
  );
}

export default OwnerConcernPage;
