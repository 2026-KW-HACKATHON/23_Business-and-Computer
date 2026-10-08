package com.gakkum.backend.application.demo.facade;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.gakkum.backend.domain.category.dto.BusinessCategoryResponse;
import com.gakkum.backend.domain.category.service.BusinessCategoryService;
import com.gakkum.backend.domain.certificate.dto.CertificateCommandDto.AddStudentCertificateCommand;
import com.gakkum.backend.domain.certificate.service.CertificateService;
import com.gakkum.backend.domain.chat.entity.ChatRoom;
import com.gakkum.backend.domain.chat.service.ChatRoomService;
import com.gakkum.backend.domain.chat.service.ChatService;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CancelJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CompleteJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobApplicationCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateJobSubmissionCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.CreateProposalJobCommand;
import com.gakkum.backend.domain.job.dto.JobCommandDto.RequestJobSubmissionRevisionCommand;
import com.gakkum.backend.domain.job.entity.Job;
import com.gakkum.backend.domain.job.entity.JobApplication;
import com.gakkum.backend.domain.job.entity.JobSubmission;
import com.gakkum.backend.domain.job.service.JobService;
import com.gakkum.backend.domain.notification.dto.NotificationEvent;
import com.gakkum.backend.domain.notification.dto.NotificationEventFactory;
import com.gakkum.backend.domain.notification.service.NotificationService;
import com.gakkum.backend.domain.owner.dto.OwnerCommandDto.CreateOwnerProfileCommand;
import com.gakkum.backend.domain.owner.entity.Owner;
import com.gakkum.backend.domain.owner.service.OwnerService;
import com.gakkum.backend.domain.payment.dto.PaymentCommandDto.PreparePaymentCommand;
import com.gakkum.backend.domain.payment.dto.PaymentQueryDto.RefundedPaymentData;
import com.gakkum.backend.domain.payment.entity.Payment;
import com.gakkum.backend.domain.payment.service.PaymentService;
import com.gakkum.backend.domain.proposal.dto.ProposalCommandDto.CreateProposalCommand;
import com.gakkum.backend.domain.proposal.dto.ProposalQueryDto.ProposalLikeData;
import com.gakkum.backend.domain.proposal.entity.Proposal;
import com.gakkum.backend.domain.proposal.service.ProposalService;
import com.gakkum.backend.domain.review.dto.ReviewCommandDto.CreateReviewCommand;
import com.gakkum.backend.domain.review.entity.ReviewPositivePoint;
import com.gakkum.backend.domain.review.service.ReviewService;
import com.gakkum.backend.domain.specialty.dto.SpecialtyCommandDto.AddStudentSpecialtyCommand;
import com.gakkum.backend.domain.specialty.service.SpecialtyService;
import com.gakkum.backend.domain.student.dto.StudentCommandDto.CreateStudentProfileCommand;
import com.gakkum.backend.domain.student.entity.Student;
import com.gakkum.backend.domain.student.service.StudentService;
import com.gakkum.backend.domain.user.entity.User;
import com.gakkum.backend.domain.user.entity.UserRole;
import com.gakkum.backend.domain.user.service.UserService;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;

/**
 * 새 데모 세션의 예시 데이터. 방문자 사장님·학생이 홈·내 활동·탐색·채팅·결제 내역의 상태를 처음부터 모두 볼 수 있게 채운다.
 * 학생 지원자·다른 학생의 제안·다른 가게의 의뢰가 필요해서 같은 세션에 로그인할 수 없는 예시 사장님·학생을 둔다.
 * 결제는 카카오페이를 거치지 않고 PaymentApprovalService·ProposalFacade와 같은 순서로 상태만 바꾼다.
 * 지난 일(작성 · 지원 · 결제 · 제출 · 수정 요청 · 완료 · 후기 · 취소)은 결제일과 마감에 맞는 과거 시각으로 둔다.
 * 알림은 같은 일을 실제 API 로 했을 때 Facade 가 보내는 알림을 그 일의 시각으로 방문자 사장님·학생에게만 만든다.
 * 어제부터 온 알림은 아직 읽지 않은 새 알림이고, 그 전 알림은 읽은 기록이다.
 */
@Component
@ConditionalOnProperty(name = "demo-login.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DemoSampleDataSeeder {

    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String UNIVERSITY = "광운대학교";
    // 제출물 원본 파일 자리. 받기를 누르면 그림이 열리고 화면에는 주소 끝(크기.png)이 파일 이름으로 보인다
    private static final String SAMPLE_FILE_BASE = "https://placehold.co/";
    // 지난 알림은 온 지 두 시간 뒤에 읽은 것으로 둔다
    private static final Duration READ_AFTER = Duration.ofHours(2);
    // 공감 알림 기준 (ProposalFacade 와 같다)
    private static final Set<Integer> LIKE_MILESTONES = Set.of(10, 30, 50);

    private final UserService userService;
    private final OwnerService ownerService;
    private final StudentService studentService;
    private final BusinessCategoryService businessCategoryService;
    private final SpecialtyService specialtyService;
    private final JobService jobService;
    private final ProposalService proposalService;
    private final PaymentService paymentService;
    private final ReviewService reviewService;
    private final CertificateService certificateService;
    private final ChatRoomService chatRoomService;
    private final ChatService chatService;
    private final NotificationService notificationService;
    private final Clock clock;
    // 지난 일의 시각을 옮길 때 쓴다 (Session.applyTimeline)
    @PersistenceContext
    private EntityManager entityManager;

    /** 예시 데이터를 받을 방문자 한 쌍의 가게와 학생 프로필. */
    public record Visitor(String demoSessionId, Owner store, Student student) {
    }

    /**
     * 예시 데이터를 다 만든 뒤 시각을 바꿀 칼럼 하나. keyColumn 으로 행을 찾고, atType 은 칼럼 타입이다.
     * condition 은 행을 더 거르는 조건이고 없으면 빈 문자열이다 (표는 t 로 부른다)
     */
    private record TimelineColumn(String table, String column, String keyColumn, String atType, String condition) {
    }

    /** 채팅방 대화 한 줄. fromOwner 면 사장님이, 아니면 학생이 보낸 메시지다 */
    private record ChatLine(boolean fromOwner, Instant at, String text) {
    }

    /** 지원서 세 칸 (한 줄 요약 · 작업계획서 · 결과물) */
    private record Pitch(String summary, String workPlan, String deliveryMethod) {
    }

    @Transactional
    public void seed(Visitor visitor) {
        Session session = new Session(visitor);
        session.seed();
        session.applyTimeline();
    }

    /** 세션 하나를 채우는 동안의 기준 날짜와 기준 데이터. */
    private final class Session {

        private final String demoSessionId;
        private final Owner myStore;
        private final Student me;
        private final Instant now;
        private final LocalDate today;
        private final Map<String, Long> specialtyIds;
        private final Map<String, Long> categoryIds;
        // 칼럼마다 행 키 → 옮길 시각. at 은 칼럼 타입에 맞춘 값이고, 같은 행을 두 번 적으면 나중 시각이 남는다
        private final Map<TimelineColumn, Map<Long, Object>> timeline = new LinkedHashMap<>();
        // 알림 내용을 만들 때 쓴다 (프로필 id → 가게 · 학생, 사용자 id → 이름)
        private final Map<Long, Owner> stores = new HashMap<>();
        private final Map<Long, Student> students = new HashMap<>();
        private final Map<String, String> names = new HashMap<>();
        // 결제하면 고르지 않은 지원자에게도 알리므로 의뢰마다 지원서를 모아 둔다
        private final Map<Long, List<JobApplication>> applications = new HashMap<>();
        // 방문자 사장님 · 학생. 예시 사장님 · 학생은 로그인할 수 없어 알림을 만들지 않는다
        private final Set<String> visitorUserIds;
        // 이 시각부터 온 알림(어제 · 오늘)은 읽지 않은 새 알림, 그 전 알림은 읽은 기록
        private final Instant unreadFrom;

        private Session(Visitor visitor) {
            this.demoSessionId = visitor.demoSessionId();
            this.myStore = visitor.store();
            this.me = visitor.student();
            this.now = clock.instant();
            this.today = LocalDate.ofInstant(now, KOREA);
            this.specialtyIds = specialtyService.getSpecialtyIdsByName();
            this.categoryIds = businessCategoryService.getBusinessCategories().stream().collect(Collectors.toMap(
                    BusinessCategoryResponse::getName, BusinessCategoryResponse::getId, (first, second) -> first));
            this.visitorUserIds = Set.of(myStore.getUserId(), me.getUserId());
            this.unreadFrom = today.minusDays(1).atStartOfDay(KOREA).toInstant();
            stores.put(myStore.getId(), myStore);
            students.put(me.getId(), me);
        }

        private void seed() {
            Student kim = student(1, 24, "김광운", "시각디자인학과",
                    "메뉴판·전단지처럼 가게에서 바로 쓰는 인쇄물을 주로 만들어요. 손님이 3초 안에 원하는 메뉴를 찾을 수 있게 "
                            + "글씨 크기와 배치부터 정리하는 걸 좋아해요. 인쇄소에 바로 맡길 수 있는 파일까지 챙겨 드려요.",
                    "메뉴판·가격표 디자인", "전단지·포스터 디자인");
            Student park = student(2, 23, "박지은", "시각디자인학과",
                    "캐릭터 일러스트를 넣은 쿠폰·스티커·명함을 만들어요. 손님이 지갑에 오래 넣고 다니고 싶은 디자인을 목표로 해요. "
                            + "재단선까지 맞춘 인쇄용 파일로 드려요.",
                    "쿠폰·스티커·명함 디자인");
            Student lee = student(3, 22, "이은서", "경영학부",
                    "소비자 행동을 공부하고 있어요. 배달앱·지도 앱 리뷰를 모아 손님이 좋아한 점과 아쉬워한 점을 숫자로 정리하고, "
                            + "적은 비용으로 바로 해 볼 수 있는 개선안까지 드려요.",
                    "리뷰 분석", "홍보·이벤트 기획");
            Student nuri = student(4, 25, "박누리", "미디어커뮤니케이션학부",
                    "학과 영상 동아리에서 편집을 맡고 있어요. 15~30초짜리 릴스와 인스타그램 게시물을 주로 만들고, "
                            + "자막·음악 넣기와 사진 보정을 잘해요.",
                    "SNS 게시물", "영상 제작 및 편집");
            Student choi = student(5, 21, "최하늘", "영어산업학과",
                    "메뉴 이름을 소리 나는 대로만 옮기지 않고, 외국인 손님이 맛을 떠올릴 수 있게 설명을 붙여 번역해요. "
                            + "가게 소개글이나 공지 문구도 읽기 쉽게 다듬어 드려요.",
                    "영어 번역", "소개·공지 글쓰기");

            Owner dino = store(1, "정민호", "공룡카페", "카페", "서울 노원구 광운로 12길 5",
                    "공룡 피규어 200여 개가 진열된 동네 카페예요. 아이와 함께 오는 손님이 많고, 공룡 모양 쿠키와 계절 음료가 인기예요.");
            Owner kwCafe = store(2, "한지수", "광운카페", "카페", "서울 노원구 광운로 21",
                    "광운대 정문 앞 스터디 카페예요. 1인석과 4인 스터디룸이 있고, 시험 기간에는 밤늦게까지 학생들이 찾아와요.");
            Owner banjeom = store(3, "오승철", "월계반점", "음식점", "서울 노원구 월계로 45길 8",
                    "30년째 같은 자리에서 영업하는 동네 중국집이에요. 짜장면과 탕수육이 대표 메뉴이고, 배달 주문이 절반을 넘어요.");
            Owner chicken = store(4, "배정훈", "치킨플러스", "음식점", "서울 노원구 석계로 3",
                    "석계역 근처 배달 중심 치킨집이에요. 저녁 시간에 전화 주문이 몰려서 사장님 혼자 주문 받기가 벅찬 날이 많아요.");

            seedMyStoreJobs(kim, park, lee, nuri, choi);
            seedMyStoreProposals(kim, park, lee, nuri, choi);
            seedOtherStores(dino, kwCafe, banjeom, chicken, kim, park, lee, nuri, choi);
            seedStudentHistories(dino, kwCafe, banjeom, chicken, kim, park, lee, nuri, choi);
        }

        /**
         * 학생들이 다른 가게에서 끝낸 지난 작업·후기와 자격증. 사장님이 보는 지원자 프로필(완료한 작업 · 평점 · 후기 ·
         * 자격증)과 방문자 학생의 내 정보 · 받은 후기 · 정산 내역이 비어 보이지 않게 한다.
         */
        private void seedStudentHistories(Owner dino, Owner kwCafe, Owner banjeom, Owner chicken,
                                          Student kim, Student park, Student lee, Student nuri, Student choi) {
            pastWork(banjeom, kim, 15, "중식 메뉴판 가격표 정리",
                    "메뉴가 40가지가 넘는데 가격표가 빽빽해서 손님들이 잘 못 읽어요. 홀 벽에 붙일 큰 가격표(A1)를 "
                            + "보기 쉽게 다시 만들어 주세요. 메뉴와 가격 목록은 사진으로 찍어 드릴게요.",
                    50_000L,
                    new Pitch("40가지 메뉴를 종류별로 묶어 멀리서도 읽히는 가격표로 만들게요",
                            "면·밥·요리·세트로 메뉴를 나누고 종류마다 색을 달리해서 원하는 메뉴를 빨리 찾을 수 있게 할게요. "
                                    + "글씨는 3m 떨어진 자리에서도 읽히는 크기로 정하고, 많이 팔리는 메뉴에는 표시를 달아 둘게요. "
                                    + "시안을 보여 드린 뒤 고쳐서 실사 출력용 파일로 드려요.",
                            "A1 실사 출력용 PDF와 가격을 고칠 수 있는 원본 파일"),
                    "가격표 완성본이에요. 종류별로 색을 나눴고, 많이 팔리는 메뉴 5가지에는 별 표시를 달았어요.",
                    "Menu+Board", 5, "메뉴가 한눈에 들어와서 주문이 빨라졌어요. 손님들이 메뉴판 앞에서 고민하는 시간이 확 줄었어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY),
                    "메뉴판·가격표 디자인");
            pastWork(dino, kim, 52, "카페 신메뉴 포스터",
                    "가을 신메뉴 음료 2가지(밤 라떼, 고구마 라떼)를 알리는 A3 포스터가 필요해요. 입구 유리문에 붙일 거라 "
                            + "밖에서도 메뉴 이름이 잘 보였으면 좋겠어요. 가게 공룡 캐릭터를 살짝 넣어 주시면 좋겠어요.",
                    40_000L,
                    new Pitch("가을 느낌과 공룡 캐릭터를 함께 살린 신메뉴 포스터를 만들게요",
                            "밤과 고구마 색에서 따온 갈색·주황 계열로 따뜻한 분위기를 만들고, 공룡 캐릭터가 컵을 들고 있는 그림을 "
                                    + "한쪽에 넣을게요. 음료 이름과 가격은 유리문 밖에서도 읽히게 크게, 설명은 한 줄로 짧게 정리해요.",
                            "A3 인쇄용 PDF"),
                    "포스터 완성본이에요. 공룡이 밤 라떼를 들고 있는 그림을 오른쪽 아래에 넣었어요.",
                    "Cafe+Poster", 4, "포스터 색감이 가게 분위기랑 잘 어울렸어요. 아이 손님들이 공룡 그림을 보고 좋아했어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.KINDNESS), "전단지·포스터 디자인");
            certify(kim, "GTQ 포토샵 1급", 2024);
            certify(kim, "컬러리스트기사", 2025);

            pastWork(chicken, park, 20, "치킨 단골 쿠폰 디자인",
                    "주문 10번을 하면 치킨 한 마리를 드리는 배달용 쿠폰을 만들어 주세요. 배달 봉투에 넣어 보낼 거라 "
                            + "명함 크기면 좋겠고, 가게 전화번호가 크게 들어가야 해요.",
                    35_000L,
                    new Pitch("배달 봉투에 넣어도 눈에 띄는 귀여운 치킨 쿠폰을 만들어 드려요",
                            "닭 캐릭터를 그려 앞면에 넣고, 뒷면에는 도장 칸 10개와 가게 전화번호를 크게 넣을게요. "
                                    + "봉투에서 꺼냈을 때 바로 눈에 띄도록 빨간색을 포인트로 쓰고, 시안 두 가지를 보여 드린 뒤 다듬어요.",
                            "명함 크기(90×50mm) 양면 인쇄용 PDF, 재단선 포함"),
                    "쿠폰 완성본이에요. 앞면에는 닭 캐릭터, 뒷면에는 도장 칸 10개와 전화번호를 넣었어요.",
                    "Chicken+Coupon", 5, "쿠폰 받은 손님들이 귀엽다고 좋아했어요. 쿠폰 덕분에 다시 주문하는 손님이 눈에 띄게 늘었어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.FAST_COMMUNICATION),
                    "쿠폰·스티커·명함 디자인");
            pastWork(kwCafe, park, 44, "스터디룸 안내 스티커",
                    "스터디룸 문과 책상에 붙일 이용 규칙 스티커를 만들어 주세요. 「음식 반입 금지」, 「통화는 밖에서」 같은 "
                            + "내용을 딱딱하지 않게 알리고 싶어요. 규칙은 5가지예요.",
                    30_000L,
                    new Pitch("딱딱하지 않게 규칙을 알리는 귀여운 안내 스티커를 만들어 드려요",
                            "규칙마다 작은 그림을 붙여 글을 읽지 않아도 알 수 있게 하고, 문구는 「~해 주세요」처럼 부드럽게 "
                                    + "다듬을게요. 문에 붙일 큰 크기와 책상에 붙일 작은 크기 두 가지로 만들어 드려요.",
                            "스티커 인쇄용 PDF 두 가지 크기(문용 A5, 책상용 8×5cm)"),
                    "스티커 완성본이에요. 규칙 5가지를 그림과 함께 넣었고, 문용과 책상용 크기로 나눴어요.",
                    "Study+Sticker", 5, "고쳐 달라는 부분을 바로바로 반영해 주셨어요. 스티커를 붙인 뒤로 스터디룸에서 통화하는 손님이 거의 없어졌어요.",
                    List.of(ReviewPositivePoint.REVISION_FEEDBACK, ReviewPositivePoint.KINDNESS),
                    "쿠폰·스티커·명함 디자인");
            certify(park, "GTQ 일러스트 1급", 2023);

            pastWork(kwCafe, lee, 27, "카페 방문 후기 분석",
                    "지도 앱에 쌓인 방문 후기 150개를 모아 손님 반응을 정리해 주세요. 손님들이 좋아하는 점과 아쉬워하는 점이 "
                            + "궁금하고, 바로 바꿀 수 있는 것부터 알고 싶어요.",
                    55_000L,
                    new Pitch("방문 후기 150개를 주제별로 나눠 좋아하는 점과 아쉬운 점을 정리해 드릴게요",
                            "후기를 표로 옮겨 자리·음료·가격·분위기·직원 응대로 나누고, 주제별로 좋은 말과 아쉬운 말이 얼마나 "
                                    + "나오는지 세어 볼게요. 아쉬운 점에는 바로 해 볼 수 있는 것부터 개선안을 붙여 드려요.",
                            "PDF 리포트(8쪽 안팎)와 후기를 정리한 엑셀 파일"),
                    "후기 분석 리포트예요. 가장 많이 나온 아쉬운 점은 「콘센트 자리가 부족하다」였어요.",
                    "Review+Report", 5, "손님들이 아쉬워한 점을 콕 집어 주셔서 바로 고칠 수 있었어요. 리포트를 보고 콘센트를 늘렸더니 후기 별점이 올라갔어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY), "리뷰 분석");
            certify(lee, "사회조사분석사 2급", 2025);
            certify(lee, "컴퓨터활용능력 1급", 2023);

            pastWork(chicken, nuri, 24, "치킨 신메뉴 릴스",
                    "새로 나온 마늘간장치킨을 소개하는 15초 릴스를 만들어 주세요. 튀기는 장면과 소스 바르는 장면은 "
                            + "휴대폰으로 찍어 둔 영상이 있어요.",
                    60_000L,
                    new Pitch("바삭한 소리가 들리는 15초 신메뉴 릴스를 만들어 드려요",
                            "튀김옷이 바삭하게 부서지는 장면을 첫 2초에 넣어 바로 눈길을 끌고, 소스를 바르는 장면에서 메뉴 이름이 "
                                    + "나오게 할게요. 소리를 살려 편집하고, 자막으로 가격과 주문 방법을 넣어 드려요.",
                            "세로 1080×1920 MP4 영상(15초)"),
                    "릴스 완성본이에요. 첫 장면에 치킨을 반으로 가르는 소리를 넣었어요.",
                    "Chicken+Reels", 4, "영상 템포가 좋아서 조회수가 많이 나왔어요. 릴스를 보고 주문했다는 손님이 꽤 있었어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.FAST_COMMUNICATION),
                    "영상 제작 및 편집");
            pastWork(dino, nuri, 58, "공룡카페 인스타 게시물 3장",
                    "카페 인스타그램에 올릴 게시물 3장을 만들어 주세요. 공룡 피규어 진열장, 대표 음료, 공룡 쿠키를 "
                            + "한 장씩 소개하고 싶어요. 사진은 직접 찍어 둔 게 있어요.",
                    45_000L,
                    new Pitch("공룡카페만의 분위기가 살아나는 게시물 3장을 만들어 드릴게요",
                            "진열장 사진은 피규어가 잘 보이게 밝게 보정하고, 음료와 쿠키는 가까이 찍은 사진으로 맛있어 보이게 "
                                    + "다듬을게요. 세 장이 이어지도록 같은 글씨체와 색을 쓰고, 짧고 재미있는 문구를 붙여 드려요.",
                            "인스타그램 피드 규격(1080×1350) PNG 3장"),
                    "게시물 3장이에요. 공룡 발자국 무늬를 세 장에 똑같이 넣어 이어 보이게 했어요.",
                    "Dino+Posts", 5, "사진 보정이 깔끔해서 계정이 확 살아났어요. 게시물 올린 주에 처음 왔다는 손님이 많았어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT), "SNS 게시물");
            certify(nuri, "GTQ 포토샵 2급", 2025);

            pastWork(banjeom, choi, 34, "중국집 영어 메뉴판",
                    "근처에 외국인 유학생 손님이 늘었는데 메뉴판이 한국어뿐이라 주문을 어려워해요. 대표 메뉴 20가지를 "
                            + "영어로 옮긴 메뉴판을 만들어 주세요.",
                    45_000L,
                    new Pitch("메뉴 이름에 맛 설명을 붙여 외국인 손님이 고르기 쉬운 영어 메뉴판을 만들어 드려요",
                            "메뉴마다 재료와 맛을 여쭤본 뒤, 이름 아래에 「달콤한 검은 콩 소스 면」처럼 짧은 설명을 붙여 "
                                    + "번역할게요. 매운 메뉴에는 고추 표시를 달고, 지금 메뉴판 옆에 붙일 A4 크기로 정리해 드려요.",
                            "A4 인쇄용 PDF와 번역문 텍스트 파일"),
                    "영어 메뉴판 완성본이에요. 매운 메뉴에는 고추 그림을 1~3개로 표시했어요.",
                    "English+Menu", 5, "외국인 손님이 메뉴를 쉽게 고르게 됐어요. 손짓으로 주문하던 손님들이 이제 메뉴판을 보고 바로 주문해요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY), "영어 번역");
            certify(choi, "토익 스피킹 IH", 2024);

            Job cardNews = pastWork(chicken, me, 26, "치킨 세트 메뉴 카드뉴스",
                    "세트 메뉴 3가지를 소개하는 인스타그램 카드뉴스를 만들어 주세요. 세트별 구성과 가격, 몇 명이 먹기 좋은지 "
                            + "알려 주고 싶어요.",
                    50_000L,
                    new Pitch("세트별 구성과 추천 인원이 한눈에 보이는 카드뉴스를 만들게요",
                            "세트마다 한 장씩 구성 메뉴 사진과 가격, 추천 인원을 같은 틀로 정리할게요. 첫 장에는 「오늘 뭐 먹지?」"
                                    + "처럼 눈길을 끄는 문구를 넣고, 마지막 장에는 전화·배달앱 주문 방법을 넣어 드려요.",
                            "인스타그램 피드 규격(1080×1350) PNG 5장"),
                    "카드뉴스 5장이에요. 세트별로 몇 명이 먹기 좋은지 사람 아이콘으로 표시했어요.",
                    "Set+Card+News", 4, "카드뉴스 덕분에 세트 주문이 늘었어요. 문구가 재미있어서 손님들이 공유도 많이 해 줬어요.",
                    List.of(ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.KINDNESS), "SNS 게시물");
            conversation(cardNews, me, false,
                    owner(at(25, 12), "카드뉴스 잘 받았어요. 오늘 저녁에 바로 올렸어요!"),
                    student(at(25, 13), "감사합니다! 세트 주문이 많이 들어왔으면 좋겠어요."));
            certify(me, "GTQ 포토샵 1급", 2025);
        }

        /** 방문자 가게의 의뢰: 모집 중 · 지원자 있음 · 작업 중 · 초안 도착 · 수정 중 · 수정안 도착 · 완료 · 취소. */
        private void seedMyStoreJobs(Student kim, Student park, Student lee, Student nuri, Student choi) {
            openJob(myStore, at(1, 10), "봄 신메뉴 전단지",
                    "봄 신메뉴 3가지(쑥떡볶이, 냉이 김밥, 딸기 라떼)를 알리는 A4 전단지가 필요해요. 가게 앞 거치대와 학교 "
                            + "게시판에 붙일 예정이라 멀리서도 메뉴 이름과 가격이 잘 보였으면 좋겠어요. 메뉴 사진 6장과 가게 로고 "
                            + "파일은 드릴게요. 봄 느낌이 나는 연두색이나 분홍색을 쓰면 좋겠어요.",
                    40_000L, 6, 12, 1, "전단지·포스터 디자인");

            Job instagram = openJob(myStore, at(2, 11), "신메뉴 인스타그램 홍보 게시물",
                    "신메뉴 출시에 맞춰 가게 인스타그램에 올릴 게시물 3장을 만들어 주세요. 첫 장은 신메뉴를 한 번에 보여 주고, "
                            + "나머지 두 장은 메뉴별 사진과 가격을 넣어 주시면 돼요. 매장에서 찍은 사진 15장 정도를 드릴게요. "
                            + "학생 손님이 많아서 너무 딱딱하지 않은 말투로 써 주세요.",
                    50_000L, 5, 10, 2, "SNS 게시물");
            apply(instagram, nuri, at(1, 14), "사진 보정과 짧은 문구로 저장하고 싶어지는 게시물 3장을 만들어 드릴게요",
                    "보내 주신 사진 중 메뉴가 가장 잘 보이는 컷을 고르고 밝기와 색감을 맞춰 보정할게요. 첫 장은 신메뉴 "
                            + "3가지를 한 화면에 모은 표지로, 나머지 두 장은 메뉴 사진을 크게 쓰고 가격과 한 줄 설명을 넣을게요. "
                            + "시안을 보여 드린 뒤 말투와 색을 고쳐 최종본을 드려요.",
                    "인스타그램 피드 규격(1080×1350) PNG 3장과 글씨를 고칠 수 있는 캔바 원본 링크");
            apply(instagram, kim, at(1, 19), "메뉴 사진이 돋보이는 카드뉴스형 게시물로 깔끔하게 정리해 드릴게요",
                    "가게 로고 색에 맞춰 세 장이 이어 보이도록 같은 틀을 먼저 잡을게요. 첫 장에는 가게 소개와 신메뉴 출시 "
                            + "소식을, 둘째·셋째 장에는 메뉴별 사진과 가격을 넣어요. 글씨는 휴대폰에서도 잘 읽히는 크기로 맞추고, "
                            + "시안을 한 번 확인받은 뒤 다듬을게요.",
                    "PNG 3장(1080×1350)과 포토샵 원본(PSD) 파일");

            Job menu = openJob(myStore, at(5, 10), "메뉴판 디자인 변경",
                    "가게를 연 지 10년이 넘어 메뉴판이 낡고 가격도 손글씨로 고쳐 둔 상태예요. A4 한 장 크기로 새로 디자인해 "
                            + "주시고, 벽에 붙일 큰 크기(A2)도 같은 디자인으로 받고 싶어요. 대표 메뉴인 떡볶이와 김밥이 먼저 눈에 "
                            + "들어오면 좋겠어요. 메뉴 이름과 가격 목록은 엑셀로 드릴게요.",
                    80_000L, 4, 11, 1, "메뉴판·가격표 디자인");
            match(myStore, menu, apply(menu, kim, at(4, 13),
                    "인기 메뉴가 먼저 보이고 가격을 한눈에 비교할 수 있는 메뉴판으로 바꿔 드릴게요",
                    "엑셀 메뉴 목록을 떡볶이·김밥·튀김·음료로 묶고, 많이 팔리는 메뉴 두 가지는 사진과 함께 위쪽에 크게 "
                            + "배치할게요. 가격은 오른쪽 끝에 줄을 맞춰 손님이 비교하기 쉽게 하고, A4와 A2 두 크기 모두 글씨가 "
                            + "깨지지 않게 작업해요. 시안 두 가지를 보여 드리고 고르신 쪽으로 다듬을게요.",
                    "A4·A2 인쇄용 PDF와 가격을 직접 고칠 수 있는 일러스트레이터(AI) 원본"), at(2, 15));
            conversation(menu, kim, true,
                    student(at(2, 16), "맡겨 주셔서 감사해요! 보내 주신 엑셀로 메뉴부터 종류별로 묶어 볼게요."),
                    owner(at(2, 17), "잘 부탁드려요. 떡볶이랑 김밥이 제일 잘 나가요. 튀김은 세트로 많이 시켜요."),
                    student(at(1, 10), "네, 떡볶이와 김밥 사진을 위쪽에 크게 넣고 튀김은 세트 칸으로 따로 빼 볼게요."));

            Job coupon = openJob(myStore, at(9, 10), "단골 쿠폰·도장카드 디자인",
                    "단골손님을 위한 도장카드를 만들고 싶어요. 도장 10개를 모으면 떡볶이 1인분을 드리는 방식이고, 지갑에 "
                            + "들어가는 명함 크기(90×50mm)였으면 해요. 앞면에는 가게 이름과 위치, 뒷면에는 도장 칸 10개를 넣어 "
                            + "주세요. 가게 대표 색인 주황색을 꼭 써 주세요.",
                    45_000L, 1, 8, 2, "쿠폰·스티커·명함 디자인");
            match(myStore, coupon, apply(coupon, park, at(8, 12),
                    "가게 캐릭터를 넣어 지갑에 오래 넣고 다니고 싶은 도장카드를 만들어 드려요",
                    "떡볶이를 들고 있는 작은 캐릭터를 그려 앞면에 넣고, 뒷면 도장 칸 10개 중 마지막 칸은 선물 모양으로 "
                            + "표시할게요. 주황 바탕 시안과 흰 바탕 시안 두 가지를 먼저 보여 드리고, 고르신 쪽으로 다듬어 인쇄소에 "
                            + "바로 맡길 수 있는 파일로 드려요.",
                    "명함 크기(90×50mm) 양면 인쇄용 PDF, 재단선 포함"), at(6, 14));
            submitDraft(coupon, park, at(1, 20),
                    "도장카드 시안 2가지를 올렸어요. A안은 주황 바탕에 캐릭터를 크게, B안은 흰 바탕에 주황 포인트로 "
                            + "깔끔하게 만들었어요. 마음에 드는 쪽을 알려 주시면 그쪽으로 다듬을게요.",
                    "1080x1080.png?text=Coupon+A", "1080x1350.png?text=Coupon+B");
            conversation(coupon, park, true,
                    student(at(6, 15), "도장카드는 주황 바탕과 흰 바탕 두 가지 시안으로 준비할게요. 캐릭터는 떡볶이를 들고 있는 모습이에요."),
                    owner(at(6, 16), "좋아요. 주황색은 간판 색이랑 맞춰 주시면 더 좋겠어요."),
                    student(at(1, 20), "초안 올렸어요! 간판 사진 보고 주황색을 맞췄어요. 두 시안 중에 골라 주세요."));

            Job reels = openJob(myStore, at(8, 11), "가게 소개 릴스 영상 편집",
                    "가게 분위기를 보여 주는 30초 릴스를 편집해 주세요. 떡볶이를 만드는 모습, 김밥 써는 장면, 손님이 먹는 "
                            + "장면을 휴대폰으로 찍어 둔 영상이 10개쯤 있어요. 인스타그램과 지도 앱 가게 소개에 같이 쓸 거라 "
                            + "자막을 넣어 주시고, 마지막에는 가게 위치가 나오면 좋겠어요.",
                    70_000L, -1, 6, 2, "영상 제작 및 편집");
            match(myStore, reels, apply(reels, me, at(7, 13), "음악 박자에 맞춰 장면이 바뀌는 30초 가게 소개 릴스를 만들게요",
                    "보내 주신 영상에서 김이 올라오는 떡볶이처럼 눈길을 끄는 장면을 골라 첫 3초 안에 보이게 배치할게요. "
                            + "장면이 음악 박자에 맞춰 바뀌도록 자르고, 메뉴 이름과 가격은 큰 자막으로 넣어요. 마지막 장면에는 "
                            + "가게 이름과 위치를 넣고, 초안을 보신 뒤 음악과 자막을 고쳐 드릴게요.",
                    "세로 1080×1920 MP4 영상(30초 안팎)과 자막 없는 버전 1개"), at(5, 16));
            JobSubmission reelsDraft = submitDraft(reels, me, at(2, 19),
                    "초안 영상이에요. 음악은 저작권 걱정 없는 무료 음원을 썼고, 다른 분위기를 원하시면 바꿔 드릴게요. "
                            + "메뉴가 나올 때마다 이름과 가격 자막을 넣었어요.",
                    "1080x1920.png?text=Reels+Draft");
            requestRevision(reels, reelsDraft, at(1, 11),
                    "전체적으로 너무 좋아요! 다만 휴대폰으로 보니 자막이 조금 작아서 한 단계 키워 주세요. 그리고 마지막 "
                            + "장면에 가게 위치(광운대 정문에서 걸어서 3분)를 넣어 주시면 좋겠어요.");
            conversation(reels, me, true,
                    student(at(5, 17), "맡겨 주셔서 감사해요! 찍어 두신 영상을 받으면 바로 장면부터 골라 볼게요."),
                    owner(at(5, 18), "영상 10개 묶어서 보내 드렸어요. 떡볶이 끓는 장면이 제일 마음에 들어요."),
                    student(at(2, 20), "초안 올렸어요! 떡볶이 끓는 장면을 첫 장면으로 넣었어요. 확인 부탁드려요."),
                    owner(at(1, 12), "수정 요청 남겼어요. 자막 크기랑 마지막 위치 안내만 고쳐 주시면 될 것 같아요."));

            Job reviews = openJob(myStore, at(12, 10), "배달앱 리뷰 분석 리포트",
                    "배달앱에 쌓인 3개월치 리뷰(200개 정도)를 모아 손님이 아쉬워한 점을 정리해 주세요. 별점이 낮은 리뷰가 "
                            + "왜 나오는지 궁금하고, 바로 고칠 수 있는 것부터 알려 주시면 좋겠어요. 사장님 계정 리뷰 화면 캡처는 "
                            + "드릴게요.",
                    60_000L, -3, 4, 1, "리뷰 분석");
            match(myStore, reviews, apply(reviews, lee, at(11, 15),
                    "리뷰 200개를 주제별로 묶고, 바로 고칠 수 있는 개선안까지 정리해 드릴게요",
                    "캡처해 주신 리뷰를 표로 옮긴 뒤 맛·양·포장·배달 시간·응대로 나눠서 얼마나 자주 나오는지 세어 볼게요. "
                            + "별점 3점 이하 리뷰는 따로 모아 이유를 정리하고, 비용이 적게 드는 것부터 해 볼 만한 개선안을 "
                            + "우선순위로 적어 드려요. 마지막 장에는 답글 예시도 넣을게요.",
                    "PDF 리포트(10쪽 안팎)와 리뷰를 정리한 엑셀 파일"), at(9, 14));
            JobSubmission reviewsDraft = submitDraft(reviews, lee, at(4, 21),
                    "리포트 초안 올렸어요. 아쉬운 점을 맛·양·포장·배달 시간으로 나눴고, 가장 많이 나온 건 「국물이 샌다」는 "
                            + "포장 의견이었어요.",
                    "1240x1754.png?text=Report+Draft");
            requestRevision(reviews, reviewsDraft, at(3, 10),
                    "포장 관련 의견이 제일 많은 것 같아요. 포장 의견만 따로 한 장으로 모아서, 어떤 메뉴에서 많이 나오는지도 "
                            + "보여 주실 수 있을까요?");
            submitRevision(reviews, lee, at(1, 22),
                    "포장 의견을 한 장으로 따로 모았어요. 국물 떡볶이와 라볶이에서 새는 의견이 많아서, 용기 바꾸기와 랩 한 번 "
                            + "더 감기 두 가지 방법을 비용과 함께 비교해 넣었어요.",
                    "1240x1754.png?text=Report+v2");
            conversation(reviews, lee, true,
                    student(at(4, 21), "리포트 초안 올렸어요. 별점 3점 이하 리뷰 38개를 따로 모아 이유를 정리했어요."),
                    owner(at(3, 10), "잘 봤어요. 포장 얘기가 제일 많네요. 포장 의견만 따로 모아 주실 수 있을까요?"),
                    student(at(1, 22), "포장 의견을 한 장으로 정리해서 다시 올렸어요. 메뉴별로 나눠 보니 국물 떡볶이가 제일 많았어요."));

            Job posts = openJob(myStore, at(48, 10), "인스타 게시물 5개 제작",
                    "가게 인스타그램 계정을 새로 만들었는데 올릴 게시물이 없어요. 가게 소개, 대표 메뉴 3가지, 영업시간 안내까지 "
                            + "게시물 5개를 만들어 주세요. 사진은 직접 찍은 것을 드릴게요.",
                    75_000L, -40, -33, 1, "SNS 게시물");
            match(myStore, posts, apply(posts, me, at(47, 14), "가게 사진으로 통일감 있는 첫 게시물 5개를 만들어 드릴게요",
                    "5개를 피드에서 이어 볼 때 한 가게처럼 보이도록 색감과 글씨체를 먼저 정할게요. 사진은 밝기와 색을 맞춰 "
                            + "보정하고, 메뉴 게시물에는 이름·가격·한 줄 설명을, 영업시간 게시물에는 휴무일까지 넣어요. 초안을 "
                            + "보신 뒤 문구를 다듬어 드릴게요.",
                    "인스타그램 피드 규격(1080×1350) PNG 5장과 캔바 원본 링크"), at(45, 15));
            complete(posts, submitDraft(posts, me, at(41, 20),
                    "게시물 5개 올렸어요. 첫 장은 가게 소개, 2~4번은 대표 메뉴, 5번은 영업시간 안내예요. 피드에 순서대로 "
                            + "올리시면 색이 이어져 보여요.",
                    "1080x1080.png?text=Post+1", "1080x1350.png?text=Post+2"), at(38, 11));
            review(posts, me, at(38, 12), 5,
                    "사진 보정이 깔끔하고 일정도 딱 맞춰 주셨어요. 피드에 올리고 나서 팔로워가 일주일 만에 100명 넘게 늘었어요. "
                            + "문구도 학생 손님 말투에 맞춰 줘서 반응이 좋았어요.",
                    ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY,
                    ReviewPositivePoint.FAST_COMMUNICATION);
            conversation(posts, me, false,
                    student(at(41, 20), "게시물 5개 올렸어요. 피드에 올리실 순서도 파일 이름에 적어 뒀어요."),
                    owner(at(38, 11), "결과물 정말 마음에 들어요. 오늘부터 하나씩 올릴게요. 감사합니다!"),
                    student(at(38, 12), "저도 감사합니다! 다음에 또 필요하시면 불러 주세요."));

            Job signboard = openJob(myStore, at(33, 10), "가게 앞 입간판 시안",
                    "가게가 골목 안쪽이라 큰길에서 잘 안 보여요. 골목 입구에 세울 A형 입간판(600×900mm) 시안을 만들어 "
                            + "주세요. 가게 이름, 대표 메뉴, 「여기서 20m」 같은 안내가 멀리서도 보이면 좋겠어요.",
                    55_000L, -25, -18, 1, "간판·현수막 시안");
            match(myStore, signboard, apply(signboard, kim, at(32, 13), "골목 입구에서도 한눈에 보이는 입간판으로 만들게요",
                    "10m 떨어진 곳에서도 읽히도록 가게 이름 글씨 크기를 먼저 정하고, 대표 메뉴 사진 한 장과 방향 화살표를 "
                            + "넣을게요. 밤에도 잘 보이도록 밝은 바탕과 어두운 바탕 두 가지 색 조합을 보여 드리고, 고르신 쪽으로 "
                            + "출력소 규격에 맞춰 드려요.",
                    "600×900mm 실사 출력용 PDF와 원본 파일"), at(30, 15));
            JobSubmission signDraft = submitDraft(signboard, kim, at(26, 19),
                    "입간판 시안이에요. 노란 바탕에 검은 글씨로 만들었고, 화살표 옆에 「골목 안 20m」를 넣었어요.",
                    "1200x1800.png?text=Sign+Draft");
            requestRevision(signboard, signDraft, at(24, 10),
                    "좋아요! 다만 가게 이름을 지금보다 조금 더 크게 해 주세요. 메뉴 사진은 떡볶이 하나만 남겨도 될 것 같아요.");
            complete(signboard, submitRevision(signboard, kim, at(20, 21),
                    "가게 이름을 1.5배 키우고, 메뉴 사진은 떡볶이 하나만 남겨서 다시 정리했어요.",
                    "1200x1800.png?text=Sign+Final"), at(19, 11));
            conversation(signboard, kim, false,
                    owner(at(19, 11), "입간판 출력해서 세웠어요. 큰길에서도 잘 보인다고 손님들이 말해 주네요!"),
                    student(at(19, 12), "다행이에요. 세워 두신 사진 보니 저도 뿌듯해요. 감사합니다!"));

            Job sticker = openJob(myStore, at(13, 10), "포장 스티커 디자인",
                    "포장 용기 뚜껑에 붙일 지름 6cm 원형 스티커를 만들어 주세요. 가게 이름과 인스타그램 아이디가 들어가고, "
                            + "손님이 사진 찍고 싶어지는 귀여운 느낌이면 좋겠어요.",
                    35_000L, 3, 9, 1, "쿠폰·스티커·명함 디자인");
            match(myStore, sticker, apply(sticker, choi, at(12, 14), "가게 로고와 인스타 아이디를 살린 원형 포장 스티커를 만들게요",
                    "로고를 깔끔하게 다시 그린 뒤 원 안에 가게 이름과 인스타그램 아이디를 넣을게요. 떡볶이 그림을 넣은 시안과 "
                            + "글씨만 있는 시안 두 가지를 보여 드리고, 고르신 쪽으로 스티커 인쇄소 규격에 맞춰 드려요.",
                    "지름 6cm 원형 스티커 인쇄용 PDF(칼선 포함)"), at(10, 16));
            cancel(sticker, at(2, 10),
                    "가게 사정으로 포장 용기를 바꾸게 되어, 스티커 크기가 정해질 때까지 디자인을 잠시 미루게 됐어요.",
                    "시안 준비해 주셨는데 죄송해요. 용기가 정해지면 다시 꼭 부탁드릴게요.");
            conversation(sticker, choi, false,
                    owner(at(2, 10), "포장 용기를 바꾸게 돼서 스티커 작업을 잠시 미루게 됐어요. 정말 죄송해요."),
                    student(at(2, 11), "괜찮아요! 용기가 정해지면 크기만 맞춰서 바로 다시 작업할 수 있어요."));
        }

        /** 방문자 가게가 받은 제안: 결정 대기 · 결제 완료(학생 시작 전) · 작업 중 · 완료 · 학생 거절 · 사장님 거절. */
        private void seedMyStoreProposals(Student kim, Student park, Student lee, Student nuri, Student choi) {
            Proposal discount = propose(me, myStore, at(2, 18), "시험 기간 학생 할인 이벤트",
                    "시험 기간에는 학생들이 도서관에만 있어서 저녁 손님이 평소보다 30% 정도 줄어들어요. 학생 손님이 많은 "
                            + "가게라 이 시기 매출이 크게 떨어져요.",
                    "시험 기간 2주 동안 학생증을 보여 주면 떡볶이를 1,000원 할인해 주는 이벤트를 기획해 드려요. 가게 앞 "
                            + "포스터와 인스타그램 게시물, 학교 커뮤니티에 올릴 안내 글까지 함께 만들어 드릴게요.",
                    "사장님과 할인 금액과 기간을 먼저 정하고, 학생들이 많이 보는 학교 커뮤니티와 학과 단톡방에 올리기 좋은 "
                            + "안내 문구를 쓸게요. 그다음 포스터 1장과 인스타그램 게시물 2장을 같은 디자인으로 만들고, 이벤트가 "
                            + "끝나면 할인 받은 손님 수를 정리해 드려요.",
                    30_000L, 3, 7, "홍보·이벤트 기획");
            like(discount, lee, nuri, choi);

            Proposal translation = propose(choi, myStore, at(1, 15), "영어 메뉴판 번역",
                    "근처 기숙사에 교환학생이 많은데, 메뉴판이 한국어로만 되어 있어 사진만 보고 주문하거나 그냥 나가는 "
                            + "손님이 있어요.",
                    "메뉴 이름을 소리 나는 대로만 옮기지 않고, 어떤 맛인지 한 줄 설명을 붙인 영어 메뉴판을 만들어 드려요. "
                            + "매운 정도는 고추 그림으로 표시해서 처음 온 손님도 쉽게 고를 수 있게 할게요.",
                    "메뉴 목록을 받아 재료와 맛을 여쭤본 뒤 번역 초안을 만들고, 원어민 친구에게 어색한 표현이 없는지 "
                            + "확인받을게요. 지금 메뉴판 옆에 붙일 수 있는 A4 한 장 크기로 정리해 드려요.",
                    40_000L, 4, 8, "영어 번역");
            like(translation, kim);

            Proposal lunchMenu = propose(me, myStore, at(4, 17), "점심 세트 메뉴판 정리",
                    "점심시간에 세트 메뉴가 6가지나 돼서 손님이 고르는 데 오래 걸리고, 계산대 앞에 줄이 길어져요.",
                    "많이 팔리는 세트 3가지만 남기고, 구성과 가격을 한눈에 비교할 수 있는 점심 전용 메뉴판을 만들어 드려요. "
                            + "계산대 옆에 세워 둘 수 있는 크기로 만들게요.",
                    "지난달 점심 주문 기록을 보고 많이 팔린 세트를 사장님과 함께 고른 뒤, 세트별 구성을 사진과 아이콘으로 "
                            + "정리할게요. 시안을 보여 드리고 고르신 쪽으로 다듬어 A4 세움 간판에 넣을 파일로 드려요.",
                    45_000L, 3, 7, "메뉴판·가격표 디자인");
            pay(lunchMenu, myStore, 45_000L, 1, "점심 손님이 많아서 기대돼요! 세트는 떡볶이·김밥 위주로 3가지만 남기면 좋겠어요.",
                    at(1, 13));

            Proposal intro = propose(me, myStore, at(6, 16), "가게 소개글 다시 쓰기",
                    "지도 앱에 올라간 가게 소개글이 몇 년 전에 쓴 거라, 지금은 팔지 않는 메뉴가 적혀 있고 영업시간도 달라요.",
                    "지금 메뉴와 가게 분위기가 드러나는 소개글을 새로 써 드려요. 지도 앱, 배달앱, 인스타그램 프로필에 맞게 "
                            + "길이를 다르게 해서 세 가지로 드릴게요.",
                    "사장님과 15분 정도 통화하면서 가게를 연 이야기와 자신 있는 메뉴를 여쭤볼게요. 그 내용으로 소개글 초안을 "
                            + "쓰고, 맞춤법과 말투를 다듬어 앱별 글자 수에 맞춰 정리해 드려요.",
                    30_000L, 4, 8, "소개·공지 글쓰기");
            Job introJob = pay(intro, myStore, 30_000L, 1, "편하게 써 주세요. 학생 손님들이 좋아하는 분위기로 부탁드려요.",
                    at(3, 14));
            start(intro, introJob, at(2, 10));
            conversation(introJob, me, true,
                    student(at(2, 10), "의뢰서 확인했어요! 소개글에 넣을 이야기를 듣고 싶어서, 짧게 통화하면 좋을 것 같아요."),
                    owner(at(2, 11), "좋아요. 이번 주 아무 때나 괜찮아요. 가게 처음 열 때 얘기부터 해 드릴게요."),
                    student(at(1, 15), "네! 내일 오후 3시 괜찮으세요? 15분이면 충분해요."));

            Proposal groupOrder = propose(park, myStore, at(11, 15), "단체 주문 안내문 디자인",
                    "학과 행사나 동아리 모임 때 단체 주문 전화가 자주 오는데, 매번 메뉴·가격·주문 마감 시간을 처음부터 "
                            + "설명해야 해서 바쁜 시간에 힘들어요.",
                    "단체 주문 메뉴와 인원별 가격, 주문 방법과 마감 시간을 한 장에 정리한 안내문을 만들어 드려요. 학과 "
                            + "단톡방에 바로 보낼 수 있는 이미지 버전도 함께 드릴게요.",
                    "자주 들어오는 단체 주문 내용을 여쭤보고, 10인분·20인분처럼 인원별 세트와 가격을 표로 정리할게요. "
                            + "A4 인쇄용과 휴대폰 이미지용 두 가지로 디자인해서 드려요.",
                    35_000L, 3, 7, "전단지·포스터 디자인");
            decline(groupOrder, pay(groupOrder, myStore, 35_000L, 1, null, at(8, 13)), at(7, 10));

            Proposal monitorVideo = propose(nuri, myStore, at(6, 14), "가게 앞 모니터 메뉴 영상",
                    "가게 앞을 지나가는 학생은 많은데, 골목 안쪽이라 어떤 메뉴를 파는지 잘 모르고 지나쳐요.",
                    "가게 앞 창가에 둘 모니터에서 반복해서 틀 15초 메뉴 소개 영상을 만들어 드려요. 떡볶이가 끓는 장면처럼 "
                            + "지나가다 눈길이 가는 장면을 앞에 둘게요.",
                    "가게에 한 번 찾아가 메뉴 촬영을 하고, 15초 안에 대표 메뉴 3가지가 나오도록 편집할게요. 소리 없이 봐도 "
                            + "이해되게 큰 자막을 넣고, 모니터 해상도에 맞춰 드려요.",
                    60_000L, 5, 10, "영상 제작 및 편집");
            rejectByOwner(monitorVideo, at(4, 11));

            Proposal photoMenu = propose(me, myStore, at(30, 15), "대표 메뉴 사진 다시 찍기",
                    "배달앱 대표 사진을 휴대폰으로 어둡게 찍어 둬서, 다른 가게보다 맛있어 보이지 않는다는 말을 들었어요.",
                    "대표 메뉴 3가지를 자연광에서 다시 찍고, 배달앱 규격에 맞게 밝기와 색을 보정해 드려요. 같은 사진으로 "
                            + "인스타그램용 정사각형 버전도 드릴게요.",
                    "손님이 적은 오후 시간에 찾아가 창가 자연광에서 메뉴별로 20장 정도 찍을게요. 그중 잘 나온 사진을 골라 "
                            + "보정하고, 배달앱과 인스타그램 규격에 맞춰 잘라 드려요.",
                    40_000L, 3, 7, "음식·매장 사진");
            Job photoJob = pay(photoMenu, myStore, 40_000L, 1, "사진 잘 부탁드려요! 떡볶이는 김이 올라올 때 찍어 주시면 좋겠어요.",
                    at(28, 13));
            start(photoMenu, photoJob, at(27, 10));
            complete(photoJob, submitDraft(photoJob, me, at(24, 19),
                    "보정까지 끝낸 사진 3장이에요. 배달앱용(가로)과 인스타그램용(정사각형) 두 가지로 잘라 뒀어요.",
                    "1080x1080.png?text=Menu+Photo"), at(22, 11));
            review(photoJob, me, at(22, 12), 5,
                    "사진이 밝아져서 배달 주문이 눈에 띄게 늘었어요. 촬영 날에도 손님 없는 시간에 맞춰 와 주셔서 장사에 "
                            + "방해가 하나도 없었어요.",
                    ReviewPositivePoint.QUALITY_OUTPUT, ReviewPositivePoint.ON_TIME_DELIVERY);
            conversation(photoJob, me, false,
                    student(at(27, 10), "의뢰서 확인했어요! 손님이 적은 오후에 촬영하면 좋을 것 같은데, 언제가 괜찮으세요?"),
                    owner(at(27, 11), "목요일 오후 3시에 와 주세요. 그때 떡볶이 새로 끓여 둘게요."),
                    owner(at(22, 11), "사진 정말 좋아요. 배달앱 사진 바로 바꿨어요. 감사합니다!"));
        }

        /** 사장님이 결제 전에 받은 제안을 거절한 상태. ProposalFacade.rejectProposal과 같다. */
        private void rejectByOwner(Proposal proposal, Instant rejectedAt) {
            proposal.rejectByOwner(LocalDateTime.ofInstant(rejectedAt, ZoneOffset.UTC));
            addNotification(NotificationEventFactory.proposalRejected(
                    students.get(proposal.getStudentProfileId()).getUserId(), proposal.getId(), proposal.getTitle(),
                    stores.get(proposal.getOwnerProfileId()).getStoreName()), rejectedAt);
        }

        /** 다른 가게: 방문자 학생의 작업·지원·선택되지 않은 지원, 공감할 다른 학생의 제안, 탐색에 보일 의뢰. */
        private void seedOtherStores(Owner dino, Owner kwCafe, Owner banjeom, Owner chicken,
                                     Student kim, Student park, Student lee, Student nuri, Student choi) {
            Job stickers = openJob(dino, at(9, 11), "쿠폰·스티커 디자인",
                    "공룡카페 캐릭터 「디노」가 들어간 쿠폰과 포장 스티커를 만들어 주세요. 쿠폰은 음료 10잔을 마시면 1잔을 "
                            + "드리는 방식이고, 스티커는 테이크아웃 컵 홀더에 붙일 지름 5cm 원형이에요. 캐릭터 원본 그림은 드릴게요.",
                    50_000L, 2, 9, 2, "쿠폰·스티커·명함 디자인");
            match(dino, stickers, apply(stickers, me, at(8, 15), "디노 캐릭터를 살려 쿠폰과 스티커를 한 세트처럼 만들게요",
                    "보내 주신 디노 그림을 쿠폰과 스티커에 맞게 다듬고, 쿠폰 도장 칸은 공룡 발자국 모양으로 만들어 재미를 "
                            + "더할게요. 쿠폰과 스티커가 한 세트처럼 보이도록 색을 맞추고, 시안 확인 후 인쇄용 파일로 정리해 드려요.",
                    "쿠폰(90×50mm) 양면 PDF와 지름 5cm 원형 스티커 PDF, 칼선 포함"), at(5, 13));
            submitDraft(stickers, me, at(1, 16),
                    "쿠폰과 스티커 초안이에요. 도장 칸을 공룡 발자국 모양으로 만들었고, 스티커에는 컵을 들고 있는 디노를 넣었어요.",
                    "1080x1080.png?text=Dino+Coupon", "1080x1350.png?text=Dino+Sticker");
            conversation(stickers, me, true,
                    owner(at(5, 14), "디노 원본 그림 보내 드렸어요. 아이들이 좋아하게 귀엽게 부탁드려요!"),
                    student(at(5, 15), "네! 도장 칸을 공룡 발자국으로 만들어 볼게요. 캐릭터부터 정리해서 보여 드릴게요."),
                    student(at(1, 17), "초안 올렸어요. 쿠폰과 스티커 색을 맞춰 한 세트처럼 만들었어요. 확인 부탁드려요!"));

            Job menuTranslation = openJob(kwCafe, at(3, 11), "영어·중국어 메뉴판 번역",
                    "외국인 학생 손님이 늘어서 메뉴판을 영어와 중국어로 옮기고 싶어요. 음료 20가지와 디저트 8가지이고, 지금 "
                            + "메뉴판 옆에 붙일 A4 한 장으로 정리해 주시면 돼요. 스터디룸 이용 안내 문구도 같이 번역해 주시면 "
                            + "좋겠어요.",
                    60_000L, 5, 12, 1, "영어 번역", "중국어 번역");
            apply(menuTranslation, me, at(2, 13), "영어·중국어 메뉴를 한 장에 나란히 정리해 드릴게요",
                    "메뉴 이름은 음료 종류를 바로 알 수 있게 옮기고, 시럽이나 우유를 바꿀 수 있는 메뉴에는 표시를 달아 둘게요. "
                            + "중국어는 간체로 번역해 유학생 친구에게 자연스러운지 확인받고, 스터디룸 안내 문구도 같은 디자인으로 "
                            + "정리해 드려요.",
                    "A4 인쇄용 PDF와 문구를 고칠 수 있는 원본 파일");
            apply(menuTranslation, choi, at(1, 10), "외국인 손님이 맛을 떠올릴 수 있는 메뉴 이름으로 옮겨 드려요",
                    "메뉴마다 들어가는 재료를 여쭤본 뒤, 이름 아래에 짧은 설명을 붙여 번역할게요. 영어는 제가 맡고, 중국어는 "
                            + "중국어 전공 친구와 함께 검수해서 어색한 표현이 없게 할게요.",
                    "A4 인쇄용 PDF 1부");

            Job banner = openJob(banjeom, at(6, 10), "배달 리뷰 이벤트 배너",
                    "배달앱에서 「리뷰 남기면 군만두 서비스」 이벤트를 하려고 해요. 배달앱 두 곳 가게 화면에 올릴 배너 "
                            + "이미지를 만들어 주세요. 앱마다 크기가 달라서 두 가지 크기가 필요해요.",
                    45_000L, 3, 9, 1, "전단지·포스터 디자인");
            apply(banner, me, at(5, 14), "눈에 띄는 리뷰 이벤트 배너를 앱별 크기로 만들게요",
                    "「리뷰 쓰면 군만두 서비스」 문구를 가장 크게 넣고, 군만두 사진을 오른쪽에 배치해 한눈에 이벤트가 "
                            + "보이게 할게요. 시안 두 가지를 보여 드리고, 고르신 쪽을 앱별 규격으로 맞춰 드려요.",
                    "배달앱 두 곳 규격 PNG 각 1장");
            match(banjeom, banner, apply(banner, nuri, at(5, 18), "배달앱 화면에서 잘리지 않게 앱별 규격에 맞춘 배너를 만들어 드려요",
                    "두 앱의 배너 규격과 글씨가 잘리는 영역을 먼저 확인하고, 그 안에 이벤트 문구와 군만두 사진을 배치할게요. "
                            + "휴대폰에서 실제로 어떻게 보이는지 캡처해서 함께 보여 드려요.",
                    "PNG 2장과 휴대폰 미리보기 캡처"), at(2, 11));

            openJob(chicken, at(1, 9), "전화 대신 받는 예약서 만들기",
                    "저녁 시간에 전화 주문이 몰려서 혼자서는 받기가 벅차요. 손님이 메뉴와 픽업 시간을 직접 고르고 남길 수 있는 "
                            + "온라인 예약서를 만들어 주세요. 예약이 들어오면 휴대폰으로 알림을 받을 수 있으면 좋겠고, 무료로 쓸 "
                            + "수 있는 도구였으면 해요.",
                    90_000L, 7, 14, 1, "온라인 예약·주문서");

            Job poster = openJob(dino, at(29, 10), "여름 음료 포스터",
                    "여름 신메뉴 음료 2가지(수박 에이드, 망고 스무디)를 알리는 포스터를 만들어 주세요. 카페 입구 유리문에 "
                            + "붙일 A3 크기이고, 보기만 해도 시원한 느낌이었으면 좋겠어요.",
                    40_000L, -22, -16, 1, "전단지·포스터 디자인");
            match(dino, poster, apply(poster, choi, at(28, 13), "여름에 딱 맞는 시원한 느낌의 음료 포스터를 만들게요",
                    "음료 색이 잘 보이도록 하늘색 바탕에 얼음 그림을 더하고, 음료 이름과 가격은 유리문 밖에서도 읽히게 크게 "
                            + "넣을게요. 시안을 확인받은 뒤 A3 인쇄용으로 정리해 드려요.",
                    "A3 인쇄용 PDF"), at(26, 15));
            complete(poster, submitDraft(poster, choi, at(23, 19),
                    "음료 포스터예요. 수박 에이드는 빨간색, 망고 스무디는 노란색으로 포인트를 줬어요.",
                    "1191x1684.png?text=Summer+Poster"), at(18, 11));
            review(poster, choi, at(18, 12), 4,
                    "색감이 시원해서 손님들이 문 앞에서 사진을 찍고 들어올 정도였어요. 메뉴 이름도 잘 보여서 신메뉴 주문이 "
                            + "많이 늘었어요.",
                    ReviewPositivePoint.QUALITY_OUTPUT);

            Proposal menuDraft = propose(lee, banjeom, at(5, 20), "인기 메뉴를 강조한 메뉴판 시안",
                    "메뉴가 40가지가 넘어서 손님이 메뉴판을 한참 보다가 결국 짜장면만 시키는 경우가 많아요. 사장님이 자신 "
                            + "있는 메뉴가 손님 눈에 잘 안 띄어요.",
                    "주문 기록에서 많이 팔리는 메뉴 5가지와 사장님 추천 메뉴를 위쪽에 사진과 함께 크게 넣고, 나머지는 "
                            + "종류별로 묶어 정리한 메뉴판을 만들어 드려요.",
                    "최근 한 달 주문 기록을 받아 많이 팔린 메뉴를 정리하고, 사장님과 추천 메뉴를 함께 골라요. 그 결과로 메뉴판 "
                            + "배치안을 만들고 시안을 보여 드린 뒤 다듬어 인쇄용으로 드릴게요.",
                    35_000L, 3, 7, "메뉴판·가격표 디자인");
            like(menuDraft, kim, park, nuri, choi);

            Proposal examEvent = propose(nuri, kwCafe, at(4, 19), "시험 기간 광운대생 이벤트 기획",
                    "시험 기간에는 낮에 자리가 부족하다가도 밤 10시 이후에는 빈자리가 많아요. 늦은 시간에도 학생들이 찾을 "
                            + "이유가 필요해요.",
                    "밤 10시 이후 음료 할인과 SNS 인증 시 쿠키를 주는 이벤트를 기획하고, 인스타그램 게시물과 매장 안내문까지 "
                            + "만들어 드려요.",
                    "사장님과 할인 시간과 혜택을 정한 뒤, 학생들이 공유하기 좋은 인스타그램 게시물 2장과 매장 테이블에 둘 "
                            + "안내문을 같은 디자인으로 만들게요. 이벤트 기간 동안 인증 게시물 수도 정리해 드려요.",
                    30_000L, 3, 7, "홍보·이벤트 기획");
            like(examEvent, lee, choi);

            Proposal photos = propose(kim, chicken, at(3, 21), "치킨 세트 사진 다시 찍기",
                    "배달앱 메뉴 사진이 어둡고 치킨이 작게 나와서, 다른 가게보다 맛있어 보이지 않는다는 리뷰가 있었어요.",
                    "세트 메뉴 4가지를 밝은 조명에서 다시 찍고, 바삭한 튀김옷이 잘 보이게 보정해 드려요. 배달앱 규격에 맞춰 "
                            + "잘라서 바로 올릴 수 있게 드릴게요.",
                    "손님이 적은 오후에 찾아가 메뉴별로 각도를 바꿔 가며 찍고, 잘 나온 컷을 골라 보정할게요. 배달앱 두 곳 "
                            + "규격에 맞춰 잘라 드리고, 인스타그램용 정사각형 버전도 함께 드려요.",
                    50_000L, 5, 10, "음식·매장 사진");
            like(photos, park);
        }

        /** @param admissionYear 입학 연도 두 자리. 화면에 「24학번」처럼 보인다 */
        private Student student(int number, int admissionYear, String name, String major, String introduction,
                                String... specialties) {
            User user = userService.createDemoSampleUser(demoSessionId, UserRole.STUDENT, number, name);
            Student student = studentService.createStudentProfile(CreateStudentProfileCommand.of(
                    user.getId(), UNIVERSITY, DemoStudentNumbers.next(studentService, admissionYear), major, null,
                    introduction, null));
            for (Long specialtyId : specialties(specialties)) {
                specialtyService.addStudentSpecialty(AddStudentSpecialtyCommand.of(student.getId(), specialtyId));
            }
            students.put(student.getId(), student);
            names.put(user.getId(), name);
            return student;
        }

        private Owner store(int number, String ownerName, String storeName, String categoryName, String address,
                            String description) {
            User user = userService.createDemoSampleUser(demoSessionId, UserRole.OWNER, number, ownerName);
            Long categoryId = categoryIds.containsKey(categoryName)
                    ? categoryIds.get(categoryName)
                    : businessCategoryService.getFirstCategoryId();
            Owner owner = ownerService.createOwnerProfile(CreateOwnerProfileCommand.of(
                    user.getId(), "DEMO-" + demoSessionId + "-" + number, null, ownerName, storeName, categoryId,
                    address, description, null, List.of()), demoSessionId);
            stores.put(owner.getId(), owner);
            return owner;
        }

        private Job openJob(Owner store, Instant postedAt, String title, String description, long budget,
                            int draftInDays, int finalInDays, int revisionCount, String... specialties) {
            Job job = jobService.createJob(CreateJobCommand.of(store.getId(), specialties(specialties), title,
                    description, budget, today.plusDays(draftInDays), today.plusDays(finalInDays), revisionCount),
                    demoSessionId);
            backdate("jobs", "created_at", job.getId(), postedAt);
            return job;
        }

        private JobApplication apply(Job job, Student student, Instant appliedAt, String summary, String workPlan,
                                     String deliveryMethod) {
            JobApplication application = jobService.createJobApplication(CreateJobApplicationCommand.of(
                    null, job.getId(), summary, workPlan, deliveryMethod), student.getId(), demoSessionId);
            backdate("job_applications", "created_at", application.getId(), appliedAt);
            applications.computeIfAbsent(job.getId(), id -> new ArrayList<>()).add(application);
            addNotification(NotificationEventFactory.jobApplicationReceived(storeOf(job).getUserId(),
                    application.getId(), job.getId(), job.getTitle(), nameOf(student)), appliedAt);
            return application;
        }

        /** 사장님이 지원자를 고르고 결제한 상태. 카카오페이 승인 뒤 PaymentApprovalService가 하는 일과 같다. */
        private void match(Owner store, Job job, JobApplication application, Instant approvedAt) {
            Payment payment = paymentService.preparePayment(PreparePaymentCommand.of(
                    job.getId(), application.getId(), store.getUserId(), job.getBudget()));
            payment.recordKakaoTid(newTid());
            job.match(application.getStudentProfileId());
            application.accept();
            payment.approve(approvedAt);
            ChatRoom chatRoom = chatRoomService.getOrCreate(job.getId());
            backdateByJob("chat_rooms", "created_at", job.getId(), approvedAt);
            // 고른 학생에게 선정을, 같은 의뢰의 나머지 지원자에게 미선정을 알린다
            addNotification(NotificationEventFactory.jobApplicationSelected(
                    students.get(application.getStudentProfileId()).getUserId(), payment.getId(), chatRoom.getId(),
                    job.getTitle(), store.getStoreName(), job.getFinalDeadline()), approvedAt);
            for (JobApplication other : applications.getOrDefault(job.getId(), List.of())) {
                if (!other.getId().equals(application.getId())) {
                    addNotification(NotificationEventFactory.jobApplicationRejected(
                            students.get(other.getStudentProfileId()).getUserId(), payment.getId(), job.getId(),
                            job.getTitle(), store.getStoreName()), approvedAt);
                }
            }
        }

        // 샘플 파일은 저장소에 올린 것이 아니라 크기를 기록하지 않는다
        private JobSubmission submitDraft(Job job, Student student, Instant submittedAt, String message,
                                          String... files) {
            JobSubmission submission = jobService.submitDraft(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId(), Map.of());
            backdate("job_submissions", "created_at", submission.getId(), submittedAt);
            addNotification(NotificationEventFactory.jobDraftSubmitted(storeOf(job).getUserId(), submission.getId(),
                    job.getId(), job.getTitle(), nameOf(student)), submittedAt);
            return submission;
        }

        private JobSubmission submitRevision(Job job, Student student, Instant submittedAt, String message,
                                             String... files) {
            JobSubmission submission = jobService.submitRevision(CreateJobSubmissionCommand.of(
                    null, job.getId(), sampleFiles(files), message), student.getId(), Map.of());
            backdate("job_submissions", "created_at", submission.getId(), submittedAt);
            addNotification(NotificationEventFactory.jobRevisionSubmitted(storeOf(job).getUserId(), submission.getId(),
                    job.getId(), job.getTitle(), nameOf(student)), submittedAt);
            return submission;
        }

        // 수정 요청 시각은 제출물의 reviewed_at 에 남는다
        private void requestRevision(Job job, JobSubmission submission, Instant requestedAt, String message) {
            jobService.requestRevision(RequestJobSubmissionRevisionCommand.of(
                    null, job.getId(), submission.getId(), message, List.of()), job.getOwnerProfileId());
            backdate("job_submissions", "reviewed_at", submission.getId(), requestedAt);
            addNotification(NotificationEventFactory.jobRevisionRequested(
                    students.get(job.getSelectedStudentProfileId()).getUserId(), submission.getId(), job.getId(),
                    job.getTitle(), storeOf(job).getStoreName()), requestedAt);
        }

        private void complete(Job job, JobSubmission submission, Instant completedAt) {
            jobService.completeSubmission(CompleteJobSubmissionCommand.of(
                    job.getId(), submission.getId(), job.getOwnerProfileId()));
            backdate("job_submissions", "reviewed_at", submission.getId(), completedAt);
            backdate("jobs", "completed_at", job.getId(), completedAt);
            // 사장님에게 후기 요청을, 결제가 있으면 담당 학생에게 정산 내역을 알린다
            Student student = students.get(job.getSelectedStudentProfileId());
            addNotification(NotificationEventFactory.jobReviewRequested(storeOf(job).getUserId(), job.getId(),
                    job.getTitle(), nameOf(student)), completedAt);
            paymentService.findPaidPayment(job.getId()).ifPresent(paid -> addNotification(
                    NotificationEventFactory.paymentSettled(student.getUserId(), paid.paymentId(), job.getId(),
                            job.getTitle(), paid.amount()), completedAt));
        }

        private void review(Job job, Student student, Instant reviewedAt, int rating, String content,
                            ReviewPositivePoint... points) {
            Long reviewId = reviewService.createReview(
                    CreateReviewCommand.of(null, job.getId(), List.of(points), content, rating),
                    job.getOwnerProfileId(), student.getId()).getId();
            backdateByJob("reviews", "created_at", job.getId(), reviewedAt);
            addNotification(NotificationEventFactory.jobReviewReceived(student.getUserId(), reviewId, job.getId(),
                    job.getTitle(), storeOf(job).getStoreName()), reviewedAt);
        }

        /** 사장님이 작업 중에 취소해 착수 보상을 뺀 금액이 환불된 상태. JobFacade.cancelJob과 같다. */
        private void cancel(Job job, Instant cancelledAt, String reason, String messageToStudent) {
            jobService.cancelJob(CancelJobCommand.of(null, job.getId(), reason, messageToStudent),
                    job.getOwnerProfileId());
            RefundedPaymentData refund = paymentService.refundOnCancel(job.getId());
            backdate("jobs", "completed_at", job.getId(), cancelledAt);
            backdateRefund(job.getId(), cancelledAt);
            // 담당 학생에게 취소를, 환불 기록을 받는 사장님에게 환불 내역을 알린다
            Owner store = storeOf(job);
            addNotification(NotificationEventFactory.jobCancelledByOwner(
                    students.get(job.getSelectedStudentProfileId()).getUserId(), job.getId(),
                    chatRoomService.getOrCreate(job.getId()).getId(), job.getTitle(), store.getStoreName()),
                    cancelledAt);
            addNotification(NotificationEventFactory.paymentRefunded(store.getUserId(), refund.paymentId(),
                    job.getTitle(), refund.refundAmount()), cancelledAt);
        }

        /**
         * 의뢰 채팅방의 대화. 줄은 시간 순서로 받는다. 보낸 쪽은 자기 메시지까지 읽은 것으로 두고, 받는 쪽도 읽은 것으로 두되
         * lastUnread 면 마지막 메시지만 받는 쪽이 아직 읽지 않은 채로 둔다 (채팅 탭의 안 읽은 표시).
         */
        private void conversation(Job job, Student student, boolean lastUnread, ChatLine... lines) {
            ChatRoom room = chatRoomService.getOrCreate(job.getId());
            String ownerUserId = ownerService.getOwnerProfileById(job.getOwnerProfileId()).getUserId();
            for (int i = 0; i < lines.length; i++) {
                ChatLine line = lines[i];
                Long messageId = chatService.sendTextMessage(room, line.fromOwner() ? ownerUserId : student.getUserId(),
                        UUID.randomUUID(), line.text()).getMessage().getId();
                backdate("chat_messages", "created_at", messageId, line.at());
                chatService.markRead(room, line.fromOwner(), messageId);
                if (!lastUnread || i < lines.length - 1) {
                    chatService.markRead(room, !line.fromOwner(), messageId);
                }
            }
        }

        private ChatLine owner(Instant at, String text) {
            return new ChatLine(true, at, text);
        }

        private ChatLine student(Instant at, String text) {
            return new ChatLine(false, at, text);
        }

        /**
         * 지난 작업 한 건: 모집 → 지원 → 결제 → 초안 → 완료 → 후기. daysAgo 일 전에 끝났고 마감도 그 전에 지났다.
         * @return 끝난 의뢰
         */
        private Job pastWork(Owner store, Student student, int daysAgo, String title, String description, long budget,
                             Pitch pitch, String submissionMessage, String fileLabel, int rating, String reviewContent,
                             List<ReviewPositivePoint> points, String... specialties) {
            Job job = openJob(store, at(daysAgo + 10, 10), title, description, budget, -(daysAgo + 2), -daysAgo, 1,
                    specialties);
            match(store, job, apply(job, student, at(daysAgo + 9, 14), pitch.summary(), pitch.workPlan(),
                    pitch.deliveryMethod()), at(daysAgo + 8, 15));
            complete(job, submitDraft(job, student, at(daysAgo + 3, 19), submissionMessage,
                    "1080x1080.png?text=" + fileLabel), at(daysAgo + 1, 11));
            review(job, student, at(daysAgo + 1, 12), rating, reviewContent, points.toArray(ReviewPositivePoint[]::new));
            return job;
        }

        private void certify(Student student, String certificateName, int acquiredYear) {
            certificateService.addStudentCertificate(
                    AddStudentCertificateCommand.of(student.getId(), certificateName, acquiredYear));
        }

        private Proposal propose(Student student, Owner store, Instant createdAt, String title, String customerProblem,
                                 String proposedSolution, String workPlan, long proposedFee, int draftDays,
                                 int finalDays, String... specialties) {
            Proposal proposal = proposalService.createProposal(CreateProposalCommand.of(
                    null, store.getId(), specialties(specialties), title, customerProblem, proposedSolution, workPlan,
                    proposedFee, draftDays, finalDays, List.of()), student.getId(), demoSessionId);
            backdate("proposals", "created_at", proposal.getId(), createdAt);
            addNotification(NotificationEventFactory.proposalReceived(store.getUserId(), proposal.getId(),
                    proposal.getTitle(), nameOf(student)), createdAt);
            return proposal;
        }

        private void like(Proposal proposal, Student... likers) {
            for (Student liker : likers) {
                ProposalLikeData liked = proposalService.likeProposal(proposal.getId(), liker.getId(), demoSessionId);
                // 새 공감으로 기준 수에 닿으면 받은 사장님과 제안한 학생에게 알린다 (공감은 지금 시각에 남는다)
                int likeCount = liked.getProposal().getLikeCount();
                if (liked.isAdded() && LIKE_MILESTONES.contains(likeCount)) {
                    addNotification(NotificationEventFactory.proposalLikeMilestoneReachedForOwner(
                            stores.get(proposal.getOwnerProfileId()).getUserId(), proposal.getId(), proposal.getTitle(),
                            likeCount), now);
                    addNotification(NotificationEventFactory.proposalLikeMilestoneReachedForStudent(
                            students.get(proposal.getStudentProfileId()).getUserId(), proposal.getId(),
                            proposal.getTitle(), likeCount), now);
                }
            }
        }

        /** 사장님이 제안을 받아 결제한 상태(학생 시작 전). PaymentApprovalService.approveProposalPayment와 순서가 같다. */
        private Job pay(Proposal proposal, Owner store, long amount, int revisionCount, String messageToStudent,
                        Instant approvedAt) {
            Payment payment = paymentService.prepareProposalPayment(
                    proposal.getId(), store.getUserId(), amount, revisionCount, messageToStudent);
            payment.recordKakaoTid(newTid());
            Job job = jobService.createAwaitingStartJob(CreateProposalJobCommand.of(
                    proposal, proposalService.getSpecialtyIds(proposal.getId()), payment.getAmount(),
                    LocalDate.ofInstant(approvedAt, KOREA), payment.getRevisionCount(), payment.getMessageToStudent()));
            proposal.awaitStart();
            payment.approve(approvedAt);
            payment.linkJob(job.getId());
            backdate("jobs", "created_at", job.getId(), approvedAt);
            // 제안한 학생에게 수락을 알린다. 학생이 시작하기 전이라 작업 시작 알림은 없다
            addNotification(NotificationEventFactory.proposalAccepted(
                    students.get(proposal.getStudentProfileId()).getUserId(), payment.getId(), job.getId(),
                    job.getTitle(), store.getStoreName(), job.getFinalDeadline()), approvedAt);
            return job;
        }

        /** 학생이 의뢰서 조건을 보고 작업을 시작한 상태. ProposalFacade.startProposalJob과 같다. */
        private void start(Proposal proposal, Job job, Instant startedAt) {
            jobService.startJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.accept();
            ChatRoom chatRoom = chatRoomService.getOrCreate(job.getId());
            backdate("jobs", "started_at", job.getId(), startedAt);
            backdateByJob("chat_rooms", "created_at", job.getId(), startedAt);
            addNotification(NotificationEventFactory.jobStarted(storeOf(job).getUserId(), job.getId(), chatRoom.getId(),
                    job.getTitle(), nameOf(students.get(proposal.getStudentProfileId()))), startedAt);
        }

        /** 학생이 의뢰서를 거절해 전액 환불된 상태. ProposalFacade.declineProposalJob과 같다. */
        private void decline(Proposal proposal, Job job, Instant declinedAt) {
            Owner store = ownerService.getOwnerProfileById(proposal.getOwnerProfileId());
            jobService.declineJob(job.getId(), proposal.getId(), proposal.getStudentProfileId());
            proposal.rejectByStudent(LocalDateTime.ofInstant(declinedAt, ZoneOffset.UTC));
            RefundedPaymentData refund = paymentService.refundOnDecline(job.getId(), proposal.getId(),
                    store.getUserId());
            backdate("jobs", "completed_at", job.getId(), declinedAt);
            backdateRefund(job.getId(), declinedAt);
            addNotification(NotificationEventFactory.paymentRefunded(store.getUserId(), refund.paymentId(),
                    job.getTitle(), refund.refundAmount()), declinedAt);
        }

        private List<Long> specialties(String... names) {
            return Arrays.stream(names).filter(specialtyIds::containsKey).map(specialtyIds::get).toList();
        }

        private List<String> sampleFiles(String... files) {
            return Arrays.stream(files).map(file -> SAMPLE_FILE_BASE + file).toList();
        }

        /**
         * 같은 일을 실제 API 로 했을 때 Facade 가 보내는 알림을 그 일의 시각으로 바로 저장한다.
         * 방문자에게 가는 알림만 만들고, 어제보다 전에 온 알림은 두 시간 뒤에 읽은 것으로 둔다.
         */
        private void addNotification(NotificationEvent event, Instant at) {
            if (!visitorUserIds.contains(event.recipientUserId())) {
                return;
            }
            LocalDateTime readAt = at.isBefore(unreadFrom)
                    ? LocalDateTime.ofInstant(at.plus(READ_AFTER), ZoneOffset.UTC)
                    : null;
            backdate("notifications", "created_at", notificationService.storeDemoSample(event, readAt).getId(), at);
        }

        private Owner storeOf(Job job) {
            return stores.get(job.getOwnerProfileId());
        }

        private String nameOf(Student student) {
            return names.computeIfAbsent(student.getUserId(), userId -> userService.getUser(userId).getName());
        }

        /** 오늘에서 days 일 전 hour 시(한국 시간). 지난 일은 모두 이 시각에 일어난 것으로 둔다 (days 는 1 이상) */
        private Instant at(int days, int hour) {
            return today.minusDays(days).atTime(hour, 0).atZone(KOREA).toInstant();
        }

        /*
         * 지난 일의 시각을 적어 둔다. 서비스는 지금 시각으로 저장하고 생성 시각 칼럼은 엔티티로 바꿀 수 없어서
         * (updatable = false) 예시 데이터를 다 만든 뒤 applyTimeline 에서 한꺼번에 고친다.
         * TIMESTAMP 칼럼은 서비스의 now() 처럼 UTC 시각으로 저장한다.
         */
        private void backdate(String table, String column, Long id, Instant at) {
            backdate(new TimelineColumn(table, column, "id", "timestamp", ""),
                    id, LocalDateTime.ofInstant(at, ZoneOffset.UTC));
        }

        private void backdateByJob(String table, String column, Long jobId, Instant at) {
            backdate(new TimelineColumn(table, column, "job_id", "timestamp", ""),
                    jobId, LocalDateTime.ofInstant(at, ZoneOffset.UTC));
        }

        // 환불 시각은 TIMESTAMP WITH TIME ZONE 칼럼이다
        private void backdateRefund(Long jobId, Instant at) {
            backdate(new TimelineColumn("payments", "refunded_at", "job_id", "timestamp with time zone",
                    " and t.refunded_at is not null"), jobId, at.atOffset(ZoneOffset.UTC));
        }

        private void backdate(TimelineColumn column, Long key, Object at) {
            timeline.computeIfAbsent(column, added -> new LinkedHashMap<>()).put(key, at);
        }

        /**
         * 적어 둔 시각을 DB 에 옮긴다. 행마다 쿼리를 보내면 DB 왕복이 수백 번이라 칼럼마다 한 번에 옮긴다.
         * 영속성 컨텍스트의 엔티티는 옮기기 전 시각을 들고 있어서, 같은 트랜잭션의 다음 조회가 DB 값을 읽도록 비운다.
         */
        private void applyTimeline() {
            entityManager.flush();
            timeline.forEach((column, rows) -> {
                List<Long> keys = List.copyOf(rows.keySet());
                String values = IntStream.range(0, keys.size())
                        .mapToObj(i -> "(cast(:key" + i + " as bigint), cast(:at" + i + " as " + column.atType() + "))")
                        .collect(Collectors.joining(", "));
                Query query = entityManager.createNativeQuery("update " + column.table() + " t set " + column.column()
                        + " = v.at from (values " + values + ") as v(key, at) where t." + column.keyColumn()
                        + " = v.key" + column.condition());
                for (int i = 0; i < keys.size(); i++) {
                    query.setParameter("key" + i, keys.get(i));
                    query.setParameter("at" + i, rows.get(keys.get(i)));
                }
                query.executeUpdate();
            });
            entityManager.clear();
        }

        // kakao_tid는 고유한 20자 이하 값이어야 한다
        private String newTid() {
            return "DEMO" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        }
    }
}
