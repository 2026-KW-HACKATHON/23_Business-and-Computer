import { useParams } from "react-router-dom";
import { LoadNotice, SubScreen } from "../components";
import {
  ChatWorkHistory,
  chatWorkBadge,
  chatWorkEntries,
  chatWorkEntryLabel,
  chatWorkFlowIndex,
  chatWorkStageOf,
  useChatRooms,
} from "../features/chat";
import { OWNER_PATHS, OwnerMissing, flowSteps, ownerWorkDocPath, ownerWorkDocSub, parsePositiveId } from "../features/owner";
import { useBack } from "../hooks/useBack";
import { studentTitle } from "../lib/korean";
import { formatWon } from "../lib/money";

/**
 * 피그마 「작업 이력 (사장님)」 (ADR 0045). 채팅 작업 카드 「이력 상세보기 ›」. 그 작업의 채팅방(GET /me/chat-rooms)
 * 단계로 쌓인 서류를 보인다. 줄마다 그 단계의 상세 화면으로 간다. 수정이 두 번 이상이면 회차마다 한 줄
 * (회차는 채팅방의 마지막 결과물 수정 번호)
 */
function OwnerWorkHistoryPage() {
  const { workId } = useParams();
  const jobId = parsePositiveId(workId);
  const back = useBack(OWNER_PATHS.chats);
  const { load, reload } = useChatRooms();

  const room = load.status === "loaded" ? load.rooms.find((r) => r.jobId === jobId) : undefined;
  if (jobId === undefined || (load.status === "loaded" && !room)) {
    return <OwnerMissing title="작업 이력" onBack={back} />;
  }
  if (!room) {
    return (
      <SubScreen title="작업 이력" onBack={back}>
        <LoadNotice
          status={load.status === "error" ? "error" : "loading"}
          loadingText="작업 이력을 불러오는 중이에요"
          errorText="작업 이력을 불러오지 못했어요"
          onRetry={reload}
        />
      </SubScreen>
    );
  }

  const stage = chatWorkStageOf(room);
  const proposalId = room.proposalId ?? undefined;
  const kind = proposalId !== undefined ? "proposal" : "request";
  const flowIndex = chatWorkFlowIndex(stage);
  const flowSub = stage === "draftArrived" || stage === "revisionArrived" ? "확인해 주세요" : "작업 중";

  return (
    <SubScreen title="작업 이력" onBack={back}>
      <ChatWorkHistory
        role="owner"
        kind={kind}
        title={room.jobTitle}
        badge={chatWorkBadge(stage)}
        meta={`${studentTitle(room.counterpartName)}\n작업비 ${formatWon(room.budget)} · 수정 ${room.revisionCount}회`}
        steps={
          flowIndex === undefined
            ? undefined
            : flowSteps(kind === "proposal" ? "제안" : "의뢰", flowIndex, flowIndex < 5 ? flowSub : undefined)
        }
        rows={chatWorkEntries(stage, room.revisionNumber ?? undefined).map((entry) => ({
          label: chatWorkEntryLabel(entry, kind),
          sub: ownerWorkDocSub(entry.doc, kind, entry.past ? undefined : stage),
          to: entry.past ? undefined : ownerWorkDocPath(entry.doc, jobId, stage, proposalId),
        }))}
      />
    </SubScreen>
  );
}

export default OwnerWorkHistoryPage;
