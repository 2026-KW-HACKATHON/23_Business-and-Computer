/* 알림 (사장님 · 학생 공용). 갈 화면은 역할마다 features/owner · features/student 가 정한다 */

export { useNotificationUnread } from "./hooks/useNotificationUnread";
export { useNotifications } from "./hooks/useNotifications";
export type { NotificationsLoad } from "./hooks/useNotifications";

export {
  NOTIFICATION_GROUPS,
  notificationGroupOf,
  notificationIcon,
  numericTargetId,
} from "./lib/notifications";
export type { NotificationGroup, NotificationItem } from "./lib/notifications";

export type { NotificationTargetType, NotificationType } from "./api/notificationApi";
