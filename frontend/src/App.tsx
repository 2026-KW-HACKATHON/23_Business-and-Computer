import { Navigate, Route, Routes } from 'react-router-dom'
import { useFlowHistoryScope } from './hooks/useFlowHistory'
import IntroPage from './pages/IntroPage'
import LoginPage from './pages/LoginPage'
import RoleSelectPage from './pages/RoleSelectPage'
import CookiePage from './pages/CookiePage'
import LandingRedirect from './pages/LandingRedirect'
import OwnerSignupLayout from './pages/OwnerSignupLayout'
import OwnerSignupInfoPage from './pages/OwnerSignupInfoPage'
import OwnerSignupVerifyPage from './pages/OwnerSignupVerifyPage'
import OwnerSignupProfilePage from './pages/OwnerSignupProfilePage'
import OwnerSignupDonePage from './pages/OwnerSignupDonePage'
import StudentSignupLayout from './pages/StudentSignupLayout'
import StudentSignupInfoPage from './pages/StudentSignupInfoPage'
import StudentSignupVerifyPage from './pages/StudentSignupVerifyPage'
import StudentSignupProfilePage from './pages/StudentSignupProfilePage'
import StudentSignupDonePage from './pages/StudentSignupDonePage'
import OwnerHomePage from './pages/OwnerHomePage'
import OwnerExplorePage from './pages/OwnerExplorePage'
import OwnerChatsPage from './pages/OwnerChatsPage'
import OwnerNotificationsPage from './pages/OwnerNotificationsPage'
import OwnerMePage from './pages/OwnerMePage'
import OwnerWorkCheckPage from './pages/OwnerWorkCheckPage'
import OwnerWorkResultPage from './pages/OwnerWorkResultPage'
import OwnerWorkHistoryPage from './pages/OwnerWorkHistoryPage'
import OwnerProposalPage from './pages/OwnerProposalPage'
import OwnerRequestCancelPage from './pages/OwnerRequestCancelPage'
import OwnerRequestPage from './pages/OwnerRequestPage'
import OwnerApplicantsPage from './pages/OwnerApplicantsPage'
import OwnerChatRoomPage from './pages/OwnerChatRoomPage'
import OwnerRequestNewPage from './pages/OwnerRequestNewPage'
import OwnerRequestContentPage from './pages/OwnerRequestContentPage'
import OwnerRequestConfirmPage from './pages/OwnerRequestConfirmPage'
import OwnerRequestDonePage from './pages/OwnerRequestDonePage'
import OwnerApplicantProfilePage from './pages/OwnerApplicantProfilePage'
import OwnerStudentPage from './pages/OwnerStudentPage'
import OwnerAssignPage from './pages/OwnerAssignPage'
import OwnerPayPage from './pages/OwnerPayPage'
import OwnerProposalAcceptPage from './pages/OwnerProposalAcceptPage'
import OwnerRevisionPage from './pages/OwnerRevisionPage'
import OwnerRevisionSentPage from './pages/OwnerRevisionSentPage'
import OwnerPastSubmissionPage from './pages/OwnerPastSubmissionPage'
import OwnerReviewPage from './pages/OwnerReviewPage'
import OwnerReviewDonePage from './pages/OwnerReviewDonePage'
import OwnerReviewViewPage from './pages/OwnerReviewViewPage'
import OwnerWorkCancelPage from './pages/OwnerWorkCancelPage'
import OwnerWorkCanceledPage from './pages/OwnerWorkCanceledPage'
import OwnerExploreProposalPage from './pages/OwnerExploreProposalPage'
import OwnerExploreRequestPage from './pages/OwnerExploreRequestPage'
import OwnerStoreEditPage from './pages/OwnerStoreEditPage'
import OwnerPaymentsPage from './pages/OwnerPaymentsPage'
import KakaoPayResultPage from './pages/KakaoPayResultPage'
import OwnerActivityPage from './pages/OwnerActivityPage'
import StudentHomePage from './pages/StudentHomePage'
import StudentExplorePage from './pages/StudentExplorePage'
import StudentStoresPage from './pages/StudentStoresPage'
import StudentChatsPage from './pages/StudentChatsPage'
import StudentNotificationsPage from './pages/StudentNotificationsPage'
import StudentMePage from './pages/StudentMePage'
import StudentProfilePage from './pages/StudentProfilePage'
import StudentProfileEditPage from './pages/StudentProfileEditPage'
import StudentSettlementsPage from './pages/StudentSettlementsPage'
import StudentPortfolioPage from './pages/StudentPortfolioPage'
import StudentActivityPage from './pages/StudentActivityPage'
import StudentProposalStorePage from './pages/StudentProposalStorePage'
import StudentProposalTasksPage from './pages/StudentProposalTasksPage'
import StudentProposalContentPage from './pages/StudentProposalContentPage'
import StudentProposalConfirmPage from './pages/StudentProposalConfirmPage'
import StudentProposalDonePage from './pages/StudentProposalDonePage'
import StudentRequestFullPage from './pages/StudentRequestFullPage'
import StudentApplyPage from './pages/StudentApplyPage'
import StudentProposalPage from './pages/StudentProposalPage'
import StudentPeerProposalPage from './pages/StudentPeerProposalPage'
import StudentProposalStartPage from './pages/StudentProposalStartPage'
import StudentWorkSubmitPage from './pages/StudentWorkSubmitPage'
import StudentRevisionPage from './pages/StudentRevisionPage'
import StudentRevisionSubmitPage from './pages/StudentRevisionSubmitPage'
import StudentSubmittedPage from './pages/StudentSubmittedPage'
import StudentPastSubmissionPage from './pages/StudentPastSubmissionPage'
import StudentWorkResultPage from './pages/StudentWorkResultPage'
import StudentWorkHistoryPage from './pages/StudentWorkHistoryPage'
import StudentReviewPage from './pages/StudentReviewPage'
import StudentWorkCanceledPage from './pages/StudentWorkCanceledPage'
import StudentChatRoomPage from './pages/StudentChatRoomPage'

function App() {
  // 끝난 흐름(의뢰 등록 · 제안 보내기 · 결제)은 뒤로가기로 돌아가지 못하게 (ADR 0047)
  useFlowHistoryScope()

  return (
    <Routes>
      {/* No splash screen: every app start shows the intro, then the home for the role or login. */}
      <Route path="/" element={<IntroPage />} />
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup/role" element={<RoleSelectPage mode="signup" />} />
      <Route path="/demo/role" element={<RoleSelectPage mode="demo" />} />
      {/* Signup steps share their input through each layout route. */}
      <Route path="/signup/owner" element={<OwnerSignupLayout />}>
        <Route index element={<Navigate to="1" replace />} />
        <Route path="1" element={<OwnerSignupInfoPage />} />
        <Route path="2" element={<OwnerSignupVerifyPage />} />
        <Route path="3" element={<OwnerSignupProfilePage />} />
        <Route path="done" element={<OwnerSignupDonePage />} />
      </Route>
      <Route path="/signup/student" element={<StudentSignupLayout />}>
        <Route index element={<Navigate to="1" replace />} />
        <Route path="1" element={<StudentSignupInfoPage />} />
        <Route path="2" element={<StudentSignupVerifyPage />} />
        <Route path="3" element={<StudentSignupProfilePage />} />
        <Route path="done" element={<StudentSignupDonePage />} />
      </Route>
      <Route path="/owner" element={<OwnerHomePage />} />
      <Route path="/owner/explore" element={<OwnerExplorePage />} />
      <Route path="/owner/chats" element={<OwnerChatsPage />} />
      <Route path="/owner/notifications" element={<OwnerNotificationsPage />} />
      <Route path="/owner/me" element={<OwnerMePage />} />
      <Route path="/owner/me/store" element={<OwnerStoreEditPage />} />
      <Route path="/owner/me/payments" element={<OwnerPaymentsPage />} />
      <Route path="/owner/requests" element={<OwnerActivityPage />} />
      <Route path="/owner/chats/:roomId" element={<OwnerChatRoomPage />} />
      <Route path="/owner/works/:workId/check" element={<OwnerWorkCheckPage />} />
      <Route path="/owner/works/:workId/result" element={<OwnerWorkResultPage />} />
      <Route path="/owner/works/:workId/history" element={<OwnerWorkHistoryPage />} />
      <Route path="/owner/proposals/:proposalId" element={<OwnerProposalPage />} />
      <Route path="/owner/requests/new" element={<OwnerRequestNewPage />} />
      <Route path="/owner/requests/new/2" element={<OwnerRequestContentPage />} />
      <Route path="/owner/requests/new/3" element={<OwnerRequestConfirmPage />} />
      <Route path="/owner/requests/new/done" element={<OwnerRequestDonePage />} />
      <Route path="/owner/requests/:requestId" element={<OwnerRequestPage />} />
      <Route path="/owner/requests/:requestId/cancel" element={<OwnerRequestCancelPage />} />
      <Route path="/owner/requests/:requestId/applicants" element={<OwnerApplicantsPage />} />
      <Route
        path="/owner/requests/:requestId/applicants/:applicationId"
        element={<OwnerApplicantProfilePage />}
      />
      <Route path="/owner/requests/:requestId/assign/:applicationId" element={<OwnerAssignPage />} />
      <Route path="/owner/requests/:requestId/assign/:applicationId/pay" element={<OwnerPayPage />} />
      <Route path="/owner/students/:studentId" element={<OwnerStudentPage />} />
      <Route path="/owner/works/:workId/revision" element={<OwnerRevisionPage />} />
      <Route path="/owner/works/:workId/revision/sent" element={<OwnerRevisionSentPage />} />
      <Route path="/owner/works/:workId/submissions/:submissionId" element={<OwnerPastSubmissionPage />} />
      <Route path="/owner/works/:workId/review" element={<OwnerReviewPage />} />
      <Route path="/owner/works/:workId/review/done" element={<OwnerReviewDonePage />} />
      <Route path="/owner/works/:workId/review/view" element={<OwnerReviewViewPage />} />
      <Route path="/owner/works/:workId/cancel" element={<OwnerWorkCancelPage />} />
      <Route path="/owner/works/:workId/canceled" element={<OwnerWorkCanceledPage />} />
      <Route path="/explore/proposals/:proposalId" element={<OwnerExploreProposalPage />} />
      <Route path="/explore/requests/:requestId" element={<OwnerExploreRequestPage />} />
      <Route path="/owner/proposals/:proposalId/accept" element={<OwnerProposalAcceptPage />} />
      <Route path="/student" element={<StudentHomePage />} />
      <Route path="/student/explore" element={<StudentExplorePage />} />
      <Route path="/student/explore/stores" element={<StudentStoresPage />} />
      <Route path="/student/chats" element={<StudentChatsPage />} />
      <Route path="/student/chats/:roomId" element={<StudentChatRoomPage />} />
      <Route path="/student/notifications" element={<StudentNotificationsPage />} />
      <Route path="/student/me" element={<StudentMePage />} />
      <Route path="/student/me/profile" element={<StudentProfilePage />} />
      <Route path="/student/me/profile/edit" element={<StudentProfileEditPage />} />
      <Route path="/student/me/settlements" element={<StudentSettlementsPage />} />
      <Route path="/student/me/portfolio" element={<StudentPortfolioPage />} />
      <Route path="/student/activity" element={<StudentActivityPage />} />
      <Route path="/student/proposals/new" element={<StudentProposalStorePage />} />
      <Route path="/student/proposals/new/2" element={<StudentProposalTasksPage />} />
      <Route path="/student/proposals/new/3" element={<StudentProposalContentPage />} />
      <Route path="/student/proposals/new/4" element={<StudentProposalConfirmPage />} />
      <Route path="/student/proposals/new/done" element={<StudentProposalDonePage />} />
      <Route path="/student/proposals/:proposalId" element={<StudentProposalPage />} />
      <Route path="/student/proposals/:proposalId/start" element={<StudentProposalStartPage />} />
      <Route path="/student/explore/proposals/:proposalId" element={<StudentPeerProposalPage />} />
      <Route path="/student/requests/:requestId/full" element={<StudentRequestFullPage />} />
      <Route path="/student/requests/:requestId/apply" element={<StudentApplyPage />} />
      <Route path="/student/works/:workId/submit" element={<StudentWorkSubmitPage />} />
      <Route path="/student/works/:workId/revision" element={<StudentRevisionPage />} />
      <Route path="/student/works/:workId/revision/submit" element={<StudentRevisionSubmitPage />} />
      <Route path="/student/works/:workId/submitted" element={<StudentSubmittedPage />} />
      <Route
        path="/student/works/:workId/submissions/:submissionId"
        element={<StudentPastSubmissionPage view="submission" />}
      />
      <Route
        path="/student/works/:workId/submissions/:submissionId/request"
        element={<StudentPastSubmissionPage view="request" />}
      />
      <Route path="/student/works/:workId/result" element={<StudentWorkResultPage />} />
      <Route path="/student/works/:workId/history" element={<StudentWorkHistoryPage />} />
      <Route path="/student/works/:workId/review" element={<StudentReviewPage />} />
      <Route path="/student/works/:workId/canceled" element={<StudentWorkCanceledPage />} />
      {/* Student screens not built yet fall back to the student home. */}
      <Route path="/student/*" element={<Navigate to="/student" replace />} />
      {/* Owner screens not built yet fall back to the owner home. */}
      <Route path="/owner/*" element={<Navigate to="/owner" replace />} />
      {/* KakaoPay returns here: approval · cancel · fail (ADR 0031). */}
      <Route path="/payments/kakao/:outcome" element={<KakaoPayResultPage />} />
      {/* Backend redirects here after a successful social login. */}
      <Route path="/cookie" element={<CookiePage />} />
      {/* Unknown paths land by login state and role (ADR 0015). */}
      <Route path="*" element={<LandingRedirect />} />
    </Routes>
  )
}

export default App
